package com.vodr.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class VodrPlaybackService : MediaSessionService() {
    @Inject
    lateinit var controller: ForegroundVodrPlayerController

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var textToSpeech: TextToSpeech? = null
    private var isVoiceReady: Boolean = false
    private var activeUtteranceId: String? = null
    private var activeChapterIndex: Int = -1
    private var activeStartPositionMs: Long = 0L
    private val progressHandler = android.os.Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            syncControllerState()
            if (player.isPlaying) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val shouldRestartTts = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    syncTtsWithPlayer(force = true)
                } else {
                    stopCurrentUtterance()
                }
            } else if (shouldRestartTts && player.isPlaying) {
                syncTtsWithPlayer(force = true)
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_ENDED
            ) {
                stopCurrentUtterance()
            }
            syncControllerState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            stopCurrentUtterance()
            syncControllerState(errorMessage = error.message ?: "Playback failed.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(playerListener)
        }
        val sessionBuilder = MediaSession.Builder(this, player)
        sessionActivityPendingIntent()?.let(sessionBuilder::setSessionActivity)
        mediaSession = sessionBuilder.build()
        initializeTextToSpeech()
        syncControllerState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent
        when (commandIntent?.action ?: ACTION_SYNC_QUEUE) {
            ACTION_SYNC_QUEUE -> syncQueueWithPlayer()
            ACTION_PLAY -> if (player.mediaItemCount > 0) player.play() else syncQueueWithPlayer()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    if (!player.isPlaying && controller.snapshot().playbackStatus == PlaybackStatus.PLAYING) {
                        player.play()
                    }
                }
            }
            ACTION_PREVIOUS -> {
                if (player.currentPosition > RESTART_THRESHOLD_MS) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
            }
            ACTION_SEEK_FORWARD -> {
                val incrementMs = commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS
                player.seekTo(player.currentPosition + incrementMs)
            }
            ACTION_SEEK_BACKWARD -> {
                val incrementMs = commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS
                player.seekTo((player.currentPosition - incrementMs).coerceAtLeast(0L))
            }
            ACTION_SEEK_TO_POSITION -> {
                val positionMs = commandIntent?.getLongExtra(EXTRA_RESUME_POSITION_MS, 0L) ?: 0L
                player.seekTo(positionMs.coerceAtLeast(0L))
            }
            ACTION_SET_SPEED -> {
                val playbackSpeed = commandIntent?.getFloatExtra(
                    EXTRA_PLAYBACK_SPEED,
                    PlaybackState.DEFAULT_PLAYBACK_SPEED,
                ) ?: PlaybackState.DEFAULT_PLAYBACK_SPEED
                player.setPlaybackParameters(
                    PlaybackParameters(playbackSpeed.coerceIn(0.75f, 2.0f)),
                )
            }
            ACTION_SELECT_CHAPTER -> {
                val chapterIndex = commandIntent?.getIntExtra(
                    EXTRA_CHAPTER_INDEX,
                    player.currentMediaItemIndex.coerceAtLeast(0),
                ) ?: player.currentMediaItemIndex.coerceAtLeast(0)
                val clampedIndex = chapterIndex.coerceIn(0, (player.mediaItemCount - 1).coerceAtLeast(0))
                player.seekToDefaultPosition(clampedIndex)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)
        stopCurrentUtterance()
        textToSpeech?.shutdown()
        player.removeListener(playerListener)
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    private fun syncQueueWithPlayer() {
        val snapshot = controller.snapshot()
        if (snapshot.queue.isEmpty()) {
            stopCurrentUtterance()
            player.clearMediaItems()
            player.pause()
            syncControllerState()
            stopSelf()
            return
        }

        val mediaSources = snapshot.queue.mapIndexed { index, chapter ->
            buildSilenceMediaSource(
                chapter = chapter,
                chapterIndex = index,
            )
        }
        player.setMediaSources(
            mediaSources,
            snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex),
            snapshot.resumePositionMs,
        )
        player.prepare()
        player.setPlaybackParameters(PlaybackParameters(snapshot.playbackSpeed))
        if (snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        ) {
            player.play()
        } else {
            player.pause()
        }
        syncControllerState()
    }

    private fun buildSilenceMediaSource(
        chapter: PlaybackChapter,
        chapterIndex: Int,
    ): MediaSource {
        return SilenceMediaSource.Factory()
            .setDurationUs(PlaybackEstimator.estimatedDurationMs(chapter.text, 1.0f) * 1_000L)
            .setTag(
                MediaItem.Builder()
                    .setMediaId(chapter.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(chapter.title)
                            .setArtist("Vodr")
                            .setTrackNumber(chapterIndex + 1)
                            .build(),
                    )
                    .build(),
            )
            .createMediaSource()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            isVoiceReady = status == TextToSpeech.SUCCESS
            if (isVoiceReady) {
                textToSpeech?.language = Locale.getDefault()
            }
            syncControllerState(
                errorMessage = if (isVoiceReady) null else "Text-to-speech engine unavailable.",
            )
        }.apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        startProgressUpdates()
                        syncControllerState()
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        activeUtteranceId = null
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            if (!player.isPlaying) {
                                player.play()
                            }
                        } else {
                            player.pause()
                        }
                        syncControllerState()
                    }

                    @Deprecated("Use onError(String?, Int) when migrating to newer TTS callbacks.")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        activeUtteranceId = null
                        player.pause()
                        syncControllerState(errorMessage = "Unable to speak the current chapter.")
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == activeUtteranceId) {
                            activeUtteranceId = null
                        }
                    }
                },
            )
        }
    }

    private fun syncTtsWithPlayer(force: Boolean) {
        if (!player.isPlaying || !isVoiceReady) {
            stopCurrentUtterance()
            return
        }

        val snapshot = controller.snapshot()
        val chapterIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val chapter = snapshot.queue.getOrNull(chapterIndex) ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        if (!force &&
            activeUtteranceId != null &&
            chapterIndex == activeChapterIndex &&
            kotlin.math.abs(positionMs - activeStartPositionMs) < 1_000L
        ) {
            return
        }

        val offset = PlaybackEstimator.estimatedCharacterOffset(
            text = chapter.text,
            positionMs = positionMs,
            playbackSpeed = player.playbackParameters.speed,
        )
        val textToSpeak = chapter.text.drop(offset).trimStart()
        if (textToSpeak.isBlank()) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.pause()
            }
            return
        }

        stopCurrentUtterance()
        activeUtteranceId = UUID.randomUUID().toString()
        activeChapterIndex = chapterIndex
        activeStartPositionMs = positionMs
        textToSpeech?.setSpeechRate(player.playbackParameters.speed)
        val result = textToSpeech?.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            null,
            activeUtteranceId,
        ) ?: TextToSpeech.ERROR
        if (result != TextToSpeech.SUCCESS) {
            activeUtteranceId = null
            player.pause()
            syncControllerState(errorMessage = "Unable to start narration.")
        }
    }

    private fun stopCurrentUtterance() {
        activeUtteranceId = null
        textToSpeech?.stop()
        stopProgressUpdates()
    }

    private fun syncControllerState(errorMessage: String? = controller.snapshot().errorMessage) {
        val snapshot = controller.snapshot()
        val currentChapterIndex = when {
            snapshot.queue.isEmpty() -> 0
            player.currentMediaItemIndex >= 0 -> player.currentMediaItemIndex.coerceIn(0, snapshot.queue.lastIndex)
            else -> snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex)
        }
        controller.updateFromService(
            snapshot.copy(
                currentChapterIndex = currentChapterIndex,
                resumePositionMs = if (snapshot.queue.isEmpty()) {
                    0L
                } else {
                    player.currentPosition.coerceAtLeast(0L)
                },
                playbackSpeed = player.playbackParameters.speed.coerceIn(0.75f, 2.0f),
                playbackStatus = when {
                    errorMessage != null -> PlaybackStatus.ERROR
                    snapshot.queue.isEmpty() -> PlaybackStatus.IDLE
                    !isVoiceReady -> PlaybackStatus.PREPARING
                    player.isPlaying -> PlaybackStatus.PLAYING
                    player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.PREPARING
                    else -> PlaybackStatus.PAUSED
                },
                isVoiceReady = isVoiceReady,
                errorMessage = errorMessage,
            ),
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

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
        progressHandler.post(progressUpdater)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
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
    }
}
