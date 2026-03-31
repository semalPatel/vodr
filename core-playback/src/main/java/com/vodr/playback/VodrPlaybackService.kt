package com.vodr.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vodr.tts.AudioCacheStore
import com.vodr.tts.NarrationPlan
import com.vodr.tts.NarrationPlanner
import com.vodr.tts.NarrationProviderType
import com.vodr.tts.NarrationRenderSettings
import com.vodr.tts.NarrationResolution
import com.vodr.tts.NarrationStyle
import com.vodr.tts.NarratorVoicePack
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

@AndroidEntryPoint
class VodrPlaybackService : MediaSessionService() {
    @Inject
    lateinit var controller: ForegroundVodrPlayerController

    @Inject
    lateinit var playbackSettingsRepository: PlaybackSettingsRepository

    @Inject
    lateinit var playbackRenderRepository: PlaybackRenderRepository

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var textToSpeech: TextToSpeech? = null
    private var isVoiceReady: Boolean = false
    private var preparedSessionId: String? = null
    private var preparedNarrationSignature: String? = null
    private var currentNarrationResolution: NarrationResolution? = null
    private var prepareJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val synthResults = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()
    private val notificationManager: NotificationManager by lazy {
        getSystemService<NotificationManager>()
            ?: error("NotificationManager unavailable")
    }
    private val audioCacheStore: AudioCacheStore by lazy {
        AudioCacheStore(File(cacheDir, AUDIO_CACHE_DIRECTORY_NAME))
    }
    private val narrationPlanner = NarrationPlanner()
    private val progressHandler = android.os.Handler(Looper.getMainLooper())
    private val wakeLock: PowerManager.WakeLock? by lazy {
        getSystemService<PowerManager>()?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:playback",
        )?.apply {
            setReferenceCounted(false)
        }
    }
    private val progressUpdater = object : Runnable {
        override fun run() {
            syncControllerState()
            updateNotification()
            if (player.isPlaying || prepareJob?.isActive == true) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            ) {
                syncControllerState()
                updateForegroundState()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            syncControllerState(errorMessage = error.message ?: "Playback failed.")
            updateForegroundState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(playerListener)
        }
        val sessionBuilder = MediaSession.Builder(this, player)
        sessionActivityPendingIntent()?.let(sessionBuilder::setSessionActivity)
        mediaSession = sessionBuilder.build()
        initializeTextToSpeech()
        val snapshot = controller.snapshot()
        if (snapshot.queue.isNotEmpty() && snapshot.resumeWhenReady) {
            syncQueueWithPlayer(forceForeground = true)
        } else {
            syncControllerState()
            updateNotification()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent
        when (commandIntent?.action ?: ACTION_SYNC_QUEUE) {
            ACTION_SYNC_QUEUE -> syncQueueWithPlayer()
            ACTION_PLAY -> {
                val snapshot = controller.snapshot()
                if (player.mediaItemCount > 0 && preparedSessionId == deriveSessionId(snapshot.activeDocument, snapshot.queue)) {
                    player.play()
                    syncControllerState()
                    updateForegroundState()
                } else {
                    syncQueueWithPlayer(forceForeground = true)
                }
            }

            ACTION_PAUSE -> {
                player.pause()
                syncControllerState()
                updateForegroundState()
            }

            ACTION_NEXT -> {
                ensureQueueLoaded()
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
                if (controller.snapshot().resumeWhenReady) {
                    player.play()
                }
                syncControllerState()
                updateForegroundState()
            }

            ACTION_PREVIOUS -> {
                ensureQueueLoaded()
                if (player.currentPosition > RESTART_THRESHOLD_MS) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
                if (controller.snapshot().resumeWhenReady) {
                    player.play()
                }
                syncControllerState()
                updateForegroundState()
            }

            ACTION_SEEK_FORWARD -> {
                ensureQueueLoaded()
                val incrementMs = commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS
                player.seekTo(player.currentPosition + incrementMs)
                syncControllerState()
            }

            ACTION_SEEK_BACKWARD -> {
                ensureQueueLoaded()
                val incrementMs = commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS
                player.seekTo((player.currentPosition - incrementMs).coerceAtLeast(0L))
                syncControllerState()
            }

            ACTION_SEEK_TO_POSITION -> {
                ensureQueueLoaded()
                val positionMs = commandIntent?.getLongExtra(EXTRA_RESUME_POSITION_MS, 0L) ?: 0L
                player.seekTo(positionMs.coerceAtLeast(0L))
                syncControllerState()
            }

            ACTION_SET_SPEED -> {
                ensureQueueLoaded()
                val playbackSpeed = commandIntent?.getFloatExtra(
                    EXTRA_PLAYBACK_SPEED,
                    PlaybackState.DEFAULT_PLAYBACK_SPEED,
                ) ?: PlaybackState.DEFAULT_PLAYBACK_SPEED
                player.setPlaybackParameters(PlaybackParameters(playbackSpeed.coerceIn(0.75f, 2.0f)))
                syncControllerState()
                updateNotification()
            }

            ACTION_SELECT_CHAPTER -> {
                ensureQueueLoaded()
                val chapterIndex = commandIntent?.getIntExtra(
                    EXTRA_CHAPTER_INDEX,
                    player.currentMediaItemIndex.coerceAtLeast(0),
                ) ?: player.currentMediaItemIndex.coerceAtLeast(0)
                val clampedIndex = chapterIndex.coerceIn(0, (player.mediaItemCount - 1).coerceAtLeast(0))
                player.seekToDefaultPosition(clampedIndex)
                if (controller.snapshot().resumeWhenReady) {
                    player.play()
                }
                syncControllerState()
                updateForegroundState()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying && prepareJob?.isActive != true) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)
        prepareJob?.cancel()
        serviceScope.cancel()
        synthResults.values.forEach { deferred ->
            deferred.complete(Result.failure(IllegalStateException("Playback service destroyed.")))
        }
        synthResults.clear()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        player.removeListener(playerListener)
        player.release()
        mediaSession.release()
        releaseWakeLock()
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun syncQueueWithPlayer(forceForeground: Boolean = false) {
        prepareJob?.cancel()
        val snapshot = controller.snapshot()
        if (snapshot.queue.isEmpty()) {
            preparedSessionId = null
            preparedNarrationSignature = null
            currentNarrationResolution = null
            player.clearMediaItems()
            player.pause()
            syncControllerState()
            updateForegroundState()
            stopSelf()
            return
        }

        val shouldPlay = snapshot.resumeWhenReady
        syncControllerState(errorMessage = null)
        updateNotification(forceForeground = forceForeground || shouldPlay)
        prepareJob = serviceScope.launch {
            if (!isVoiceReady) {
                syncControllerState(
                    errorMessage = if (textToSpeech == null) {
                        "Text-to-speech engine unavailable."
                    } else {
                        null
                    },
                )
                updateForegroundState(forceForeground = forceForeground || shouldPlay)
                return@launch
            }

            val settings = playbackSettingsRepository.loadNarrationSettings()
            val resolution = resolveNarrationResolution(settings)
            val narrationSignature = buildNarrationSignature(snapshot, settings, resolution)
            if (preparedSessionId == deriveSessionId(snapshot.activeDocument, snapshot.queue) &&
                preparedNarrationSignature == narrationSignature &&
                player.mediaItemCount == snapshot.queue.size
            ) {
                player.setPlaybackParameters(PlaybackParameters(snapshot.playbackSpeed))
                if (shouldPlay) {
                    player.play()
                } else {
                    player.pause()
                }
                currentNarrationResolution = resolution
                syncControllerState(
                    narrationResolution = resolution,
                    errorMessage = null,
                )
                updateForegroundState(forceForeground = forceForeground || shouldPlay)
                return@launch
            }

            val mediaSources = runCatching {
                buildChapterMediaSources(snapshot = snapshot, settings = settings, resolution = resolution)
            }.getOrElse { error ->
                syncControllerState(
                    errorMessage = error.message ?: "Unable to prepare audiobook playback.",
                    narrationResolution = resolution,
                )
                updateForegroundState(forceForeground = false)
                return@launch
            }

            player.setMediaSources(
                mediaSources,
                snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex),
                snapshot.resumePositionMs,
            )
            player.prepare()
            player.setPlaybackParameters(PlaybackParameters(snapshot.playbackSpeed))
            preparedSessionId = deriveSessionId(snapshot.activeDocument, snapshot.queue)
            preparedNarrationSignature = narrationSignature
            currentNarrationResolution = resolution
            if (shouldPlay) {
                player.play()
            } else {
                player.pause()
            }
            syncControllerState(
                narrationResolution = resolution,
                errorMessage = null,
            )
            updateForegroundState(forceForeground = forceForeground || shouldPlay)
        }
    }

    private suspend fun buildChapterMediaSources(
        snapshot: PlaybackState,
        settings: NarrationRenderSettings,
        resolution: NarrationResolution,
    ): List<MediaSource> {
        return withContext(Dispatchers.IO) {
            val dataSourceFactory = DefaultDataSource.Factory(this@VodrPlaybackService)
            snapshot.queue.mapIndexed { index, chapter ->
                val audioFile = renderChapterAudio(
                    chapter = chapter,
                    settings = settings,
                    resolution = resolution,
                    playbackSpeed = snapshot.playbackSpeed,
                )
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
                    MediaItem.Builder()
                        .setMediaId(chapter.id)
                        .setUri(Uri.fromFile(audioFile))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(chapter.title)
                                .setArtist("Vodr")
                                .setTrackNumber(index + 1)
                                .build(),
                        )
                        .build(),
                )
            }
        }
    }

    private suspend fun renderChapterAudio(
        chapter: PlaybackChapter,
        settings: NarrationRenderSettings,
        resolution: NarrationResolution,
        playbackSpeed: Float,
    ): File {
        val plan = narrationPlanner.plan(chapter.text)
        val cacheKey = buildAudioCacheKey(
            chapter = chapter,
            settings = settings,
            resolution = resolution,
            plan = plan,
            playbackSpeed = playbackSpeed,
        )
        audioCacheStore.get(cacheKey)?.let { cachedPath ->
            val cachedFile = File(cachedPath)
            if (cachedFile.exists() && cachedFile.canRead()) {
                return cachedFile
            }
        }

        val targetFile = File(File(cacheDir, AUDIO_CACHE_DIRECTORY_NAME), "$cacheKey.wav")
        targetFile.parentFile?.mkdirs()
        when (resolution.providerType) {
            NarrationProviderType.CLOUD_ENDPOINT -> {
                runCatching {
                    renderWithCloudEndpoint(
                        targetFile = targetFile,
                        chapter = chapter,
                        settings = settings,
                        resolution = resolution,
                        plan = plan,
                    )
                }.getOrElse {
                    synthesizeWithSystemTts(
                        targetFile = targetFile,
                        chapter = chapter,
                        settings = settings,
                        voicePack = resolution.voicePack,
                        plan = plan,
                    )
                }
            }

            NarrationProviderType.OFFLINE_VOICE_PACK,
            NarrationProviderType.SYSTEM_TTS,
            NarrationProviderType.AUTO,
            -> {
                synthesizeWithSystemTts(
                    targetFile = targetFile,
                    chapter = chapter,
                    settings = settings,
                    voicePack = resolution.voicePack,
                    plan = plan,
                )
            }
        }
        audioCacheStore.put(cacheKey, targetFile.absolutePath)
        playbackRenderRepository.markChapterRendered(
            chapter = chapter,
            renderedAudioPath = targetFile.absolutePath,
            providerLabel = resolution.providerLabel,
            voicePackId = resolution.voicePack?.id,
            estimatedDurationMs = PlaybackEstimator.estimatedDurationMs(
                text = chapter.text,
                playbackSpeed = playbackSpeed,
            ),
        )
        return targetFile
    }

    private suspend fun synthesizeWithSystemTts(
        targetFile: File,
        chapter: PlaybackChapter,
        settings: NarrationRenderSettings,
        voicePack: NarratorVoicePack?,
        plan: NarrationPlan,
    ) {
        val tts = textToSpeech ?: throw IllegalStateException("Text-to-speech engine unavailable.")
        val supportedLocale = selectFirstSupportedTtsLocale(defaultLocale = preferredLocale(voicePack)) { locale ->
            tts.setLanguage(locale)
        } ?: throw IllegalStateException("No supported text-to-speech voice is installed on this device.")
        tts.setLanguage(supportedLocale)
        chooseVoice(
            tts = tts,
            settings = settings,
            voicePack = voicePack,
        )
        tts.setPitch(
            resolvePitch(
                basePitch = settings.pitch,
                voicePack = voicePack,
                style = plan.dominantStyle,
            ),
        )
        tts.setSpeechRate(
            resolveSpeechRate(
                baseSpeechRate = settings.speechRate,
                voicePack = voicePack,
                style = plan.dominantStyle,
            ),
        )
        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Result<Unit>>()
        synthResults[utteranceId] = deferred
        val normalizedText = splitTextForTts(
            text = chapter.text,
            maxChars = TextToSpeech.getMaxSpeechInputLength(),
        ).joinToString(separator = " ")
        val result = tts.synthesizeToFile(
            normalizedText,
            Bundle(),
            targetFile,
            utteranceId,
        )
        if (result != TextToSpeech.SUCCESS) {
            synthResults.remove(utteranceId)
            throw IllegalStateException("Unable to render audio for ${chapter.title}.")
        }
        deferred.await().getOrThrow()
    }

    private suspend fun renderWithCloudEndpoint(
        targetFile: File,
        chapter: PlaybackChapter,
        settings: NarrationRenderSettings,
        resolution: NarrationResolution,
        plan: NarrationPlan,
    ) {
        require(!settings.offlineOnly) { "Offline-only mode blocks cloud narration." }
        val endpoint = settings.cloudEndpoint.trim().ifBlank {
            throw IllegalStateException("Cloud narration endpoint is not configured.")
        }
        val connection = withContext(Dispatchers.IO) {
            (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CLOUD_CONNECT_TIMEOUT_MS
                readTimeout = CLOUD_READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, audio/wav, audio/mpeg, audio/*")
            }
        }
        val body = JSONObject()
            .put("text", chapter.text)
            .put("chapterId", chapter.id)
            .put("chapterTitle", chapter.title)
            .put("model", settings.cloudModelName)
            .put("style", plan.dominantStyle.name.lowercase())
            .put("pauseMs", plan.segments.sumOf { it.pauseAfterMs })
            .put("providerLabel", resolution.providerLabel)
            .toString()
        withContext(Dispatchers.IO) {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException(
                    "Cloud narrator returned HTTP $responseCode${errorBody.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}",
                )
            }
            val contentType = connection.contentType.orEmpty()
            when {
                contentType.startsWith("audio/") -> {
                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                else -> {
                    val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val payload = JSONObject(responseBody)
                    when {
                        payload.has("audioBase64") -> {
                            val bytes = Base64.decode(payload.getString("audioBase64"), Base64.DEFAULT)
                            targetFile.writeBytes(bytes)
                        }

                        payload.has("audioUrl") -> {
                            val audioConnection = (URI(payload.getString("audioUrl")).toURL().openConnection()
                                as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = CLOUD_CONNECT_TIMEOUT_MS
                                readTimeout = CLOUD_READ_TIMEOUT_MS
                            }
                            if (audioConnection.responseCode !in 200..299) {
                                throw IllegalStateException("Cloud narrator audio URL could not be fetched.")
                            }
                            audioConnection.inputStream.use { input ->
                                targetFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }

                        else -> throw IllegalStateException("Cloud narrator response did not include audio.")
                    }
                }
            }
        }
    }

    private fun initializeTextToSpeech() {
        val enginePackageName = resolvePreferredTtsEnginePackageName(
            defaultEnginePackage = readDefaultTtsEnginePackageName(),
            availableEngines = availableTtsEnginePackageNames(),
        )
        if (enginePackageName == null) {
            isVoiceReady = false
            syncControllerState(errorMessage = "No text-to-speech engine is installed on this device.")
            updateNotification()
            return
        }

        textToSpeech = TextToSpeech(applicationContext, { status ->
            val selectedLocale = if (status == TextToSpeech.SUCCESS) {
                selectFirstSupportedTtsLocale(defaultLocale = Locale.getDefault()) { locale ->
                    textToSpeech?.setLanguage(locale) ?: TextToSpeech.ERROR
                }
            } else {
                null
            }
            isVoiceReady = selectedLocale != null
            syncControllerState(
                errorMessage = when {
                    status != TextToSpeech.SUCCESS -> "Text-to-speech engine unavailable."
                    selectedLocale == null -> "No supported text-to-speech voice is installed on this device."
                    else -> null
                },
            )
            if (isVoiceReady && controller.snapshot().queue.isNotEmpty() && controller.snapshot().resumeWhenReady) {
                syncQueueWithPlayer(forceForeground = true)
            } else {
                updateForegroundState()
            }
        }, enginePackageName).apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let(synthResults::remove)?.complete(Result.success(Unit))
                    }

                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    @Deprecated("Use onError(String?, Int) when migrating to newer TTS callbacks.")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let(synthResults::remove)?.complete(
                            Result.failure(IllegalStateException("Text-to-speech synthesis failed.")),
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let(synthResults::remove)?.complete(
                            Result.failure(IllegalStateException("Text-to-speech synthesis failed ($errorCode).")),
                        )
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        utteranceId?.let(synthResults::remove)?.complete(
                            Result.failure(IllegalStateException("Text-to-speech synthesis stopped.")),
                        )
                    }
                },
            )
        }
    }

    private fun chooseVoice(
        tts: TextToSpeech,
        settings: NarrationRenderSettings,
        voicePack: NarratorVoicePack?,
    ) {
        val preferredName = voicePack?.systemVoiceName
            ?: settings.voiceName
            ?: return
        tts.voices
            ?.firstOrNull { voice ->
                voice.name == preferredName && !voice.isNetworkConnectionRequired
            }
            ?.let { voice ->
                tts.voice = voice
            }
    }

    private fun resolveNarrationResolution(settings: NarrationRenderSettings): NarrationResolution {
        val voicePack = settings.selectedVoicePack
        return when (settings.providerType) {
            NarrationProviderType.OFFLINE_VOICE_PACK -> {
                if (voicePack != null) {
                    NarrationResolution(
                        providerType = NarrationProviderType.OFFLINE_VOICE_PACK,
                        providerLabel = voicePack.providerLabel,
                        detail = voicePack.displayName,
                        voicePack = voicePack,
                    )
                } else {
                    NarrationResolution(
                        providerType = NarrationProviderType.SYSTEM_TTS,
                        providerLabel = "System TTS",
                        detail = "Selected voice pack is unavailable, so system speech is active.",
                    )
                }
            }

            NarrationProviderType.CLOUD_ENDPOINT -> {
                if (!settings.offlineOnly && settings.cloudEndpoint.isNotBlank()) {
                    NarrationResolution(
                        providerType = NarrationProviderType.CLOUD_ENDPOINT,
                        providerLabel = "Cloud Narrator",
                        detail = settings.cloudModelName.ifBlank { "Endpoint audio" },
                        voicePack = voicePack,
                    )
                } else if (voicePack != null) {
                    NarrationResolution(
                        providerType = NarrationProviderType.OFFLINE_VOICE_PACK,
                        providerLabel = voicePack.providerLabel,
                        detail = "Cloud path unavailable, using ${voicePack.displayName}.",
                        voicePack = voicePack,
                    )
                } else {
                    NarrationResolution(
                        providerType = NarrationProviderType.SYSTEM_TTS,
                        providerLabel = "System TTS",
                        detail = "Cloud path unavailable, using system speech.",
                    )
                }
            }

            NarrationProviderType.SYSTEM_TTS -> {
                NarrationResolution(
                    providerType = NarrationProviderType.SYSTEM_TTS,
                    providerLabel = "System TTS",
                    detail = "Using the device speech engine.",
                )
            }

            NarrationProviderType.AUTO -> {
                when {
                    voicePack != null -> NarrationResolution(
                        providerType = NarrationProviderType.OFFLINE_VOICE_PACK,
                        providerLabel = voicePack.providerLabel,
                        detail = voicePack.displayName,
                        voicePack = voicePack,
                    )

                    !settings.offlineOnly && settings.cloudEndpoint.isNotBlank() -> NarrationResolution(
                        providerType = NarrationProviderType.CLOUD_ENDPOINT,
                        providerLabel = "Cloud Narrator",
                        detail = settings.cloudModelName.ifBlank { "Endpoint audio" },
                    )

                    else -> NarrationResolution(
                        providerType = NarrationProviderType.SYSTEM_TTS,
                        providerLabel = "System TTS",
                        detail = "Using the device speech engine.",
                    )
                }
            }
        }
    }

    private fun buildNarrationSignature(
        snapshot: PlaybackState,
        settings: NarrationRenderSettings,
        resolution: NarrationResolution,
    ): String {
        return listOf(
            deriveSessionId(snapshot.activeDocument, snapshot.queue),
            resolution.providerType.name,
            resolution.providerLabel,
            resolution.detail,
            settings.speechRate.toString(),
            settings.pitch.toString(),
            settings.voiceName.orEmpty(),
            settings.selectedVoicePackId,
            settings.cloudEndpoint,
            settings.cloudModelName,
            snapshot.playbackSpeed.toString(),
        ).joinToString(separator = "|")
    }

    private fun buildAudioCacheKey(
        chapter: PlaybackChapter,
        settings: NarrationRenderSettings,
        resolution: NarrationResolution,
        plan: NarrationPlan,
        playbackSpeed: Float,
    ): String {
        val source = listOf(
            chapter.id,
            chapter.title,
            chapter.text,
            resolution.providerType.name,
            resolution.providerLabel,
            resolution.detail,
            resolution.voicePack?.stableCacheKey.orEmpty(),
            settings.voiceName.orEmpty(),
            settings.speechRate.toString(),
            settings.pitch.toString(),
            settings.cloudModelName,
            plan.dominantStyle.name,
            playbackSpeed.toString(),
        ).joinToString(separator = "|")
        return sha256(source)
    }

    private fun syncControllerState(
        errorMessage: String? = controller.snapshot().errorMessage,
        narrationResolution: NarrationResolution? = currentNarrationResolution,
    ) {
        val snapshot = controller.snapshot()
        val currentChapterIndex = when {
            snapshot.queue.isEmpty() -> 0
            player.currentMediaItemIndex >= 0 -> player.currentMediaItemIndex.coerceIn(0, snapshot.queue.lastIndex)
            else -> snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex)
        }
        val mergedRuntimeMetadata = mergeRuntimeMetadata(
            existing = snapshot.runtimeMetadata,
            narrationResolution = narrationResolution,
        )
        controller.updateFromService(
            snapshot.copy(
                runtimeMetadata = mergedRuntimeMetadata,
                currentChapterIndex = currentChapterIndex,
                resumePositionMs = when {
                    snapshot.queue.isEmpty() -> 0L
                    player.currentMediaItemIndex >= 0 -> player.currentPosition.coerceAtLeast(0L)
                    else -> snapshot.resumePositionMs
                },
                currentChapterDurationMs = resolveCurrentChapterDuration(
                    snapshot = snapshot,
                    currentChapterIndex = currentChapterIndex,
                ),
                playbackSpeed = player.playbackParameters.speed.coerceIn(0.75f, 2.0f),
                playbackStatus = when {
                    errorMessage != null -> PlaybackStatus.ERROR
                    snapshot.queue.isEmpty() -> PlaybackStatus.IDLE
                    prepareJob?.isActive == true -> PlaybackStatus.PREPARING
                    player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.PREPARING
                    player.isPlaying -> PlaybackStatus.PLAYING
                    else -> PlaybackStatus.PAUSED
                },
                resumeWhenReady = if (prepareJob?.isActive == true) {
                    snapshot.resumeWhenReady
                } else {
                    player.playWhenReady
                },
                isVoiceReady = isVoiceReady,
                errorMessage = errorMessage,
            ),
        )
    }

    private fun resolveCurrentChapterDuration(
        snapshot: PlaybackState,
        currentChapterIndex: Int,
    ): Long {
        return when {
            player.duration != C.TIME_UNSET && player.duration > 0L -> player.duration
            else -> snapshot.queue.getOrNull(currentChapterIndex)?.let { chapter ->
                PlaybackEstimator.estimatedDurationMs(
                    text = chapter.text,
                    playbackSpeed = player.playbackParameters.speed.coerceIn(0.75f, 2.0f),
                )
            } ?: 0L
        }
    }

    private fun updateForegroundState(forceForeground: Boolean = false) {
        syncWakeLock(forceForeground = forceForeground)
        updateNotification(forceForeground = forceForeground)
        if (player.isPlaying || prepareJob?.isActive == true) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    private fun syncWakeLock(forceForeground: Boolean) {
        val shouldHoldWakeLock = forceForeground || player.isPlaying || prepareJob?.isActive == true
        if (shouldHoldWakeLock) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
        }
    }

    private fun updateNotification(forceForeground: Boolean = false) {
        val snapshot = controller.snapshot()
        if (snapshot.queue.isEmpty()) {
            notificationManager.cancel(NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val notification = buildNotification(snapshot)
        val shouldForeground = forceForeground || player.isPlaying || prepareJob?.isActive == true
        if (shouldForeground) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(snapshot: PlaybackState): Notification {
        val currentChapter = snapshot.currentChapter
        val statusLabel = when {
            prepareJob?.isActive == true -> "Rendering narration"
            player.isPlaying -> "Playing"
            snapshot.playbackStatus == PlaybackStatus.ERROR -> snapshot.errorMessage ?: "Playback issue"
            else -> "Paused"
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(snapshot.activeDocument?.title ?: currentChapter?.title ?: "Vodr")
            .setContentText(currentChapter?.title?.takeIf { it.isNotBlank() } ?: statusLabel)
            .setSubText(statusLabel)
            .setContentIntent(sessionActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying || prepareJob?.isActive == true)
            .setShowWhen(false)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    serviceActionPendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS_INTENT),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (player.isPlaying) "Pause" else "Play",
                    serviceActionPendingIntent(
                        if (player.isPlaying) ACTION_PAUSE else ACTION_PLAY,
                        REQUEST_PLAY_PAUSE_INTENT,
                    ),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    serviceActionPendingIntent(ACTION_NEXT, REQUEST_NEXT_INTENT),
                ),
            )
            .build()
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audiobook playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Playback controls for ongoing audiobook sessions."
            },
        )
    }

    private fun mergeRuntimeMetadata(
        existing: PlaybackRuntimeMetadata?,
        narrationResolution: NarrationResolution?,
    ): PlaybackRuntimeMetadata {
        if (existing == null && narrationResolution == null) {
            return PlaybackRuntimeMetadata()
        }
        return PlaybackRuntimeMetadata(
            personalizationProviderLabel = existing?.personalizationProviderLabel,
            personalizationDetail = existing?.personalizationDetail,
            transcriptionProviderLabel = existing?.transcriptionProviderLabel,
            transcriptionDetail = existing?.transcriptionDetail,
            narrationProviderLabel = narrationResolution?.providerLabel ?: existing?.narrationProviderLabel,
            narrationDetail = narrationResolution?.detail ?: existing?.narrationDetail,
        )
    }

    private fun sessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            REQUEST_CONTENT_INTENT,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VodrPlaybackService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
        progressHandler.post(progressUpdater)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
    }

    private fun ensureQueueLoaded() {
        if (player.mediaItemCount == 0 && controller.snapshot().queue.isNotEmpty()) {
            syncQueueWithPlayer(forceForeground = controller.snapshot().resumeWhenReady)
        }
    }

    private fun availableTtsEnginePackageNames(): List<String> {
        return packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        ).mapNotNull { resolveInfo ->
            resolveInfo.serviceInfo?.packageName
        }.distinct()
    }

    private fun readDefaultTtsEnginePackageName(): String? {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH,
        )?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val ACTION_SYNC_QUEUE: String = "com.vodr.playback.action.SYNC_QUEUE"
        const val ACTION_PLAY: String = "com.vodr.playback.action.PLAY"
        const val ACTION_PAUSE: String = "com.vodr.playback.action.PAUSE"
        const val ACTION_NEXT: String = "com.vodr.playback.action.NEXT"
        const val ACTION_PREVIOUS: String = "com.vodr.playback.action.PREVIOUS"
        const val ACTION_SEEK_FORWARD: String = "com.vodr.playback.action.SEEK_FORWARD"
        const val ACTION_SEEK_BACKWARD: String = "com.vodr.playback.action.SEEK_BACKWARD"
        const val ACTION_SEEK_TO_POSITION: String = "com.vodr.playback.action.SEEK_TO_POSITION"
        const val ACTION_SET_SPEED: String = "com.vodr.playback.action.SET_SPEED"
        const val ACTION_SELECT_CHAPTER: String = "com.vodr.playback.action.SELECT_CHAPTER"

        const val EXTRA_SEEK_INCREMENT_MS: String = "extra_seek_increment_ms"
        const val EXTRA_RESUME_POSITION_MS: String = "extra_resume_position_ms"
        const val EXTRA_PLAYBACK_SPEED: String = "extra_playback_speed"
        const val EXTRA_CHAPTER_INDEX: String = "extra_chapter_index"

        private const val PROGRESS_UPDATE_INTERVAL_MS: Long = 500L
        private const val RESTART_THRESHOLD_MS: Long = 2_000L
        private const val REQUEST_CONTENT_INTENT: Int = 1
        private const val REQUEST_PREVIOUS_INTENT: Int = 2
        private const val REQUEST_PLAY_PAUSE_INTENT: Int = 3
        private const val REQUEST_NEXT_INTENT: Int = 4
        private const val NOTIFICATION_ID: Int = 3001
        private const val NOTIFICATION_CHANNEL_ID: String = "vodr_playback"
        private const val AUDIO_CACHE_DIRECTORY_NAME: String = "rendered-audio"
        private const val CLOUD_CONNECT_TIMEOUT_MS: Int = 5_000
        private const val CLOUD_READ_TIMEOUT_MS: Int = 20_000
        private const val WAKE_LOCK_TIMEOUT_MS: Long = 60L * 60L * 1_000L
    }
}

internal fun selectFirstSupportedTtsLocale(
    defaultLocale: Locale,
    languageAvailability: (Locale) -> Int,
): Locale? {
    return preferredTtsLocales(defaultLocale).firstOrNull { locale ->
        languageAvailability(locale).isSupportedTtsLanguageResult()
    }
}

private fun preferredTtsLocales(defaultLocale: Locale): List<Locale> {
    return buildList {
        add(defaultLocale)
        defaultLocale.language.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)?.let(::add)
        if (defaultLocale != Locale.US) {
            add(Locale.US)
        }
        if (defaultLocale != Locale.ENGLISH) {
            add(Locale.ENGLISH)
        }
    }.distinct()
}

internal fun resolvePreferredTtsEnginePackageName(
    defaultEnginePackage: String?,
    availableEngines: List<String>,
): String? {
    val sanitizedDefault = defaultEnginePackage?.trim()?.takeIf { it.isNotBlank() }
    return when {
        sanitizedDefault != null && sanitizedDefault in availableEngines -> sanitizedDefault
        GOOGLE_TTS_PACKAGE_NAME in availableEngines -> GOOGLE_TTS_PACKAGE_NAME
        else -> availableEngines.firstOrNull()
    }
}

internal fun splitTextForTts(
    text: String,
    maxChars: Int,
): List<String> {
    val normalizedText = text.replace(WHITESPACE_REGEX, " ").trim()
    if (normalizedText.isBlank()) {
        return emptyList()
    }
    val utterances = mutableListOf<String>()
    var currentUtterance = ""
    val segments = normalizedText.split(SENTENCE_BOUNDARY_REGEX)
        .map(String::trim)
        .filter(String::isNotBlank)

    segments.forEach { segment ->
        val pieces = if (segment.length <= maxChars) {
            listOf(segment)
        } else {
            splitSegmentForTts(segment = segment, maxChars = maxChars)
        }
        pieces.forEach { piece ->
            val candidate = if (currentUtterance.isBlank()) {
                piece
            } else {
                "$currentUtterance $piece"
            }
            if (candidate.length <= maxChars) {
                currentUtterance = candidate
            } else {
                if (currentUtterance.isNotBlank()) {
                    utterances += currentUtterance
                }
                currentUtterance = piece
            }
        }
    }
    if (currentUtterance.isNotBlank()) {
        utterances += currentUtterance
    }
    return utterances
}

private fun splitSegmentForTts(
    segment: String,
    maxChars: Int,
): List<String> {
    val parts = mutableListOf<String>()
    var currentPart = ""
    segment.split(" ").filter(String::isNotBlank).forEach { word ->
        when {
            word.length > maxChars -> {
                if (currentPart.isNotBlank()) {
                    parts += currentPart
                    currentPart = ""
                }
                parts += word.chunked(maxChars)
            }

            currentPart.isBlank() -> currentPart = word
            currentPart.length + 1 + word.length <= maxChars -> currentPart += " $word"
            else -> {
                parts += currentPart
                currentPart = word
            }
        }
    }
    if (currentPart.isNotBlank()) {
        parts += currentPart
    }
    return parts
}

internal fun Int.isSupportedTtsLanguageResult(): Boolean {
    return this >= TextToSpeech.LANG_AVAILABLE
}

private fun preferredLocale(voicePack: NarratorVoicePack?): Locale {
    return voicePack?.languageTag
        ?.takeIf { it.isNotBlank() }
        ?.let(Locale::forLanguageTag)
        ?: Locale.getDefault()
}

private fun resolveSpeechRate(
    baseSpeechRate: Float,
    voicePack: NarratorVoicePack?,
    style: NarrationStyle,
): Float {
    val styleMultiplier = when (style) {
        NarrationStyle.NEUTRAL -> 1.0f
        NarrationStyle.DIALOGUE -> 1.02f
        NarrationStyle.QUESTION -> 0.98f
        NarrationStyle.EXCITED -> 1.05f
        NarrationStyle.SUSPENSE -> 0.9f
        NarrationStyle.REFLECTIVE -> 0.92f
    }
    return (baseSpeechRate * (voicePack?.speechRateMultiplier ?: 1.0f) * styleMultiplier)
        .coerceIn(0.5f, 1.6f)
}

private fun resolvePitch(
    basePitch: Float,
    voicePack: NarratorVoicePack?,
    style: NarrationStyle,
): Float {
    val styleMultiplier = when (style) {
        NarrationStyle.NEUTRAL -> 1.0f
        NarrationStyle.DIALOGUE -> 1.02f
        NarrationStyle.QUESTION -> 1.04f
        NarrationStyle.EXCITED -> 1.08f
        NarrationStyle.SUSPENSE -> 0.92f
        NarrationStyle.REFLECTIVE -> 0.96f
    }
    return (basePitch * (voicePack?.pitchMultiplier ?: 1.0f) * styleMultiplier)
        .coerceIn(0.7f, 1.3f)
}

private fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private val SENTENCE_BOUNDARY_REGEX = Regex("(?<=[.!?])\\s+")
private val WHITESPACE_REGEX = Regex("\\s+")
private const val GOOGLE_TTS_PACKAGE_NAME: String = "com.google.android.tts"
