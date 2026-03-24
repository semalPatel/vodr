package com.vodr.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class VodrPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    @Inject
    lateinit var controller: ForegroundVodrPlayerController

    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSessionCompat
    private var audioFocusRequest: AudioFocusRequest? = null
    private var textToSpeech: TextToSpeech? = null
    private var activeUtteranceId: String? = null
    private var utteranceStartElapsedRealtimeMs: Long = 0L
    private var playbackBasePositionMs: Long = 0L
    private var isVoiceReady: Boolean = false
    private val progressHandler = android.os.Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val snapshot = controller.snapshot()
            val chapter = snapshot.currentChapter ?: return
            if (snapshot.playbackStatus != PlaybackStatus.PLAYING) {
                return
            }
            val updatedPosition = currentPlaybackPosition(
                chapter = chapter,
                playbackSpeed = snapshot.playbackSpeed,
            )
            controller.updateFromService(
                snapshot.copy(
                    resumePositionMs = updatedPosition,
                    isVoiceReady = isVoiceReady,
                )
            )
            updateMediaSessionState(controller.snapshot())
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
        registerBecomingNoisyReceiver()
        initializeMediaSession()
        initializeTextToSpeech()
        syncStateWithSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent
        when (commandIntent?.action ?: ACTION_SYNC_QUEUE) {
            ACTION_SYNC_QUEUE -> syncStateWithSession()
            ACTION_PLAY -> playCurrentChapter()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> skipToNext()
            ACTION_PREVIOUS -> skipToPrevious()
            ACTION_SEEK_FORWARD -> seekBy(
                commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS
            )
            ACTION_SEEK_BACKWARD -> seekBy(
                -(commandIntent?.getLongExtra(
                    EXTRA_SEEK_INCREMENT_MS,
                    PlaybackState.DEFAULT_SEEK_INCREMENT_MS,
                ) ?: PlaybackState.DEFAULT_SEEK_INCREMENT_MS)
            )
            ACTION_SEEK_TO_POSITION -> seekTo(
                commandIntent?.getLongExtra(EXTRA_RESUME_POSITION_MS, 0L) ?: 0L
            )
            ACTION_SET_SPEED -> updatePlaybackSpeed(
                commandIntent?.getFloatExtra(
                    EXTRA_PLAYBACK_SPEED,
                    PlaybackState.DEFAULT_PLAYBACK_SPEED,
                ) ?: PlaybackState.DEFAULT_PLAYBACK_SPEED
            )
            ACTION_SELECT_CHAPTER -> selectChapter(
                commandIntent?.getIntExtra(
                    EXTRA_CHAPTER_INDEX,
                    controller.snapshot().currentChapterIndex,
                ) ?: controller.snapshot().currentChapterIndex
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> pausePlayback()
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(becomingNoisyReceiver)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        mediaSession.release()
        abandonAudioFocus()
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, SESSION_TAG).apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = playCurrentChapter()

                    override fun onPause() = pausePlayback()

                    override fun onSkipToNext() = skipToNext()

                    override fun onSkipToPrevious() = skipToPrevious()

                    override fun onSeekTo(pos: Long) = seekTo(pos)

                    override fun onFastForward() = seekBy(PlaybackState.DEFAULT_SEEK_INCREMENT_MS)

                    override fun onRewind() = seekBy(-PlaybackState.DEFAULT_SEEK_INCREMENT_MS)
                }
            )
            isActive = true
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                isVoiceReady = true
                syncStateWithSession()
            } else {
                isVoiceReady = false
                controller.updateFromService(
                    controller.snapshot().copy(
                        playbackStatus = PlaybackStatus.ERROR,
                        errorMessage = "Text-to-speech engine unavailable.",
                        isVoiceReady = false,
                    )
                )
                updateMediaSessionState(controller.snapshot())
                updateForegroundNotification(controller.snapshot())
            }
        }.apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        val snapshot = controller.snapshot()
                        controller.updateFromService(
                            snapshot.copy(
                                playbackStatus = PlaybackStatus.PLAYING,
                                isVoiceReady = isVoiceReady,
                                errorMessage = null,
                            )
                        )
                        updateMediaSessionState(controller.snapshot())
                        updateForegroundNotification(controller.snapshot())
                        startProgressUpdates()
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        activeUtteranceId = null
                        stopProgressUpdates()
                        handleChapterFinished()
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        activeUtteranceId = null
                        stopProgressUpdates()
                        abandonAudioFocus()
                        controller.updateFromService(
                            controller.snapshot().copy(
                                playbackStatus = PlaybackStatus.ERROR,
                                errorMessage = "Unable to speak the current chapter.",
                                isVoiceReady = isVoiceReady,
                            )
                        )
                        updateMediaSessionState(controller.snapshot())
                        updateForegroundNotification(controller.snapshot())
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId != activeUtteranceId) {
                            return
                        }
                        activeUtteranceId = null
                        stopProgressUpdates()
                    }
                }
            )
        }
    }

    private fun syncStateWithSession() {
        val snapshot = controller.snapshot().copy(
            isVoiceReady = isVoiceReady,
            playbackStatus = if (controller.snapshot().queue.isEmpty()) {
                PlaybackStatus.IDLE
            } else {
                controller.snapshot().playbackStatus
            },
        )
        controller.updateFromService(snapshot)
        updateMediaSessionState(snapshot)
        updateForegroundNotification(snapshot)
    }

    private fun playCurrentChapter() {
        val snapshot = controller.snapshot()
        val chapter = snapshot.currentChapter ?: run {
            syncStateWithSession()
            return
        }
        if (!isVoiceReady || textToSpeech == null) {
            controller.updateFromService(
                snapshot.copy(
                    playbackStatus = PlaybackStatus.ERROR,
                    errorMessage = "Voice engine is still preparing.",
                    isVoiceReady = isVoiceReady,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
            return
        }
        if (!requestAudioFocus()) {
            controller.updateFromService(
                snapshot.copy(
                    playbackStatus = PlaybackStatus.PAUSED,
                    errorMessage = "Audio focus unavailable.",
                    isVoiceReady = isVoiceReady,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
            return
        }

        val durationMs = estimatedDurationMs(
            text = chapter.text,
            playbackSpeed = snapshot.playbackSpeed,
        )
        val resumePositionMs = if (snapshot.resumePositionMs >= durationMs) {
            0L
        } else {
            snapshot.resumePositionMs
        }
        val offset = estimatedCharacterOffset(
            text = chapter.text,
            resumePositionMs = resumePositionMs,
            playbackSpeed = snapshot.playbackSpeed,
        )
        val textToSpeak = chapter.text.drop(offset).trimStart()
        if (textToSpeak.isBlank()) {
            handleChapterFinished()
            return
        }

        activeUtteranceId = UUID.randomUUID().toString()
        playbackBasePositionMs = resumePositionMs
        utteranceStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        controller.updateFromService(
            snapshot.copy(
                playbackStatus = PlaybackStatus.PREPARING,
                resumePositionMs = resumePositionMs,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())

        textToSpeech?.setSpeechRate(snapshot.playbackSpeed)
        val speakResult = textToSpeech?.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            null,
            activeUtteranceId,
        ) ?: TextToSpeech.ERROR
        if (speakResult != TextToSpeech.SUCCESS) {
            activeUtteranceId = null
            abandonAudioFocus()
            controller.updateFromService(
                controller.snapshot().copy(
                    playbackStatus = PlaybackStatus.ERROR,
                    errorMessage = "Unable to start playback.",
                    isVoiceReady = isVoiceReady,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
        }
    }

    private fun pausePlayback() {
        val snapshot = controller.snapshot()
        val updatedPosition = snapshot.currentChapter?.let {
            currentPlaybackPosition(
                chapter = it,
                playbackSpeed = snapshot.playbackSpeed,
            )
        } ?: snapshot.resumePositionMs
        activeUtteranceId = null
        textToSpeech?.stop()
        stopProgressUpdates()
        abandonAudioFocus()
        controller.updateFromService(
            snapshot.copy(
                resumePositionMs = updatedPosition,
                playbackStatus = if (snapshot.queue.isEmpty()) PlaybackStatus.IDLE else PlaybackStatus.PAUSED,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
    }

    private fun skipToNext() {
        val snapshot = controller.snapshot()
        if (snapshot.currentChapterIndex >= snapshot.queue.lastIndex) {
            return
        }
        val shouldResume = snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        stopCurrentUtterance()
        controller.updateFromService(
            snapshot.copy(
                currentChapterIndex = snapshot.currentChapterIndex + 1,
                resumePositionMs = 0L,
                playbackStatus = PlaybackStatus.PAUSED,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
        if (shouldResume) {
            playCurrentChapter()
        }
    }

    private fun skipToPrevious() {
        val snapshot = controller.snapshot()
        if (snapshot.currentChapterIndex <= 0) {
            seekTo(0L)
            return
        }
        val shouldResume = snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        stopCurrentUtterance()
        controller.updateFromService(
            snapshot.copy(
                currentChapterIndex = snapshot.currentChapterIndex - 1,
                resumePositionMs = 0L,
                playbackStatus = PlaybackStatus.PAUSED,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
        if (shouldResume) {
            playCurrentChapter()
        }
    }

    private fun seekBy(deltaMs: Long) {
        val snapshot = controller.snapshot()
        val chapter = snapshot.currentChapter ?: return
        val target = (snapshot.resumePositionMs + deltaMs).coerceIn(
            0L,
            estimatedDurationMs(chapter.text, snapshot.playbackSpeed),
        )
        seekTo(target)
    }

    private fun seekTo(positionMs: Long) {
        val snapshot = controller.snapshot()
        val shouldResume = snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        stopCurrentUtterance()
        controller.updateFromService(
            snapshot.copy(
                resumePositionMs = positionMs.coerceAtLeast(0L),
                playbackStatus = PlaybackStatus.PAUSED,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
        if (shouldResume) {
            playCurrentChapter()
        }
    }

    private fun updatePlaybackSpeed(playbackSpeed: Float) {
        val snapshot = controller.snapshot()
        val clampedSpeed = playbackSpeed.coerceIn(0.75f, 2.0f)
        val shouldResume = snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        stopCurrentUtterance()
        controller.updateFromService(
            snapshot.copy(
                playbackSpeed = clampedSpeed,
                playbackStatus = PlaybackStatus.PAUSED,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
        if (shouldResume) {
            playCurrentChapter()
        }
    }

    private fun selectChapter(chapterIndex: Int) {
        val snapshot = controller.snapshot()
        if (snapshot.queue.isEmpty()) {
            return
        }
        val shouldResume = snapshot.playbackStatus == PlaybackStatus.PLAYING ||
            snapshot.playbackStatus == PlaybackStatus.PREPARING
        stopCurrentUtterance()
        controller.updateFromService(
            snapshot.copy(
                currentChapterIndex = chapterIndex.coerceIn(0, snapshot.queue.lastIndex),
                resumePositionMs = 0L,
                playbackStatus = PlaybackStatus.PAUSED,
                errorMessage = null,
                isVoiceReady = isVoiceReady,
            )
        )
        updateMediaSessionState(controller.snapshot())
        updateForegroundNotification(controller.snapshot())
        if (shouldResume) {
            playCurrentChapter()
        }
    }

    private fun handleChapterFinished() {
        val snapshot = controller.snapshot()
        val chapter = snapshot.currentChapter
        if (chapter == null) {
            controller.updateFromService(
                snapshot.copy(
                    playbackStatus = PlaybackStatus.IDLE,
                    isVoiceReady = isVoiceReady,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
            return
        }

        val chapterDurationMs = estimatedDurationMs(
            text = chapter.text,
            playbackSpeed = snapshot.playbackSpeed,
        )
        if (snapshot.currentChapterIndex < snapshot.queue.lastIndex) {
            controller.updateFromService(
                snapshot.copy(
                    currentChapterIndex = snapshot.currentChapterIndex + 1,
                    resumePositionMs = 0L,
                    playbackStatus = PlaybackStatus.PAUSED,
                    isVoiceReady = isVoiceReady,
                    errorMessage = null,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
            playCurrentChapter()
        } else {
            abandonAudioFocus()
            controller.updateFromService(
                snapshot.copy(
                    resumePositionMs = chapterDurationMs,
                    playbackStatus = PlaybackStatus.PAUSED,
                    isVoiceReady = isVoiceReady,
                    errorMessage = null,
                )
            )
            updateMediaSessionState(controller.snapshot())
            updateForegroundNotification(controller.snapshot())
        }
    }

    private fun updateMediaSessionState(state: PlaybackState) {
        val chapter = state.currentChapter
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND
            )
            .setState(
                when (state.playbackStatus) {
                    PlaybackStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
                    PlaybackStatus.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
                    PlaybackStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
                    PlaybackStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
                    PlaybackStatus.IDLE -> PlaybackStateCompat.STATE_STOPPED
                },
                state.resumePositionMs,
                state.playbackSpeed,
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    chapter?.title ?: "Vodr Player",
                )
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    if (chapter == null) {
                        0L
                    } else {
                        estimatedDurationMs(
                            text = chapter.text,
                            playbackSpeed = state.playbackSpeed,
                        )
                    },
                )
                .build()
        )
    }

    private fun updateForegroundNotification(state: PlaybackState) {
        if (state.queue.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: PlaybackState): Notification {
        val chapter = state.currentChapter
        val isPlaying = state.playbackStatus == PlaybackStatus.PLAYING ||
            state.playbackStatus == PlaybackStatus.PREPARING
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                REQUEST_CONTENT_INTENT,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setContentTitle(chapter?.title ?: "Vodr Player")
            .setContentText(
                when (state.playbackStatus) {
                    PlaybackStatus.PLAYING -> "Playing narration"
                    PlaybackStatus.PREPARING -> "Preparing narration"
                    PlaybackStatus.PAUSED -> "Playback paused"
                    PlaybackStatus.ERROR -> state.errorMessage ?: "Playback error"
                    PlaybackStatus.IDLE -> "Ready to play"
                }
            )
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    servicePendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS),
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    servicePendingIntent(
                        if (isPlaying) ACTION_PAUSE else ACTION_PLAY,
                        if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY,
                    ),
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    servicePendingIntent(ACTION_NEXT, REQUEST_NEXT),
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, VodrPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
        progressHandler.post(progressUpdater)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater)
    }

    private fun stopCurrentUtterance() {
        activeUtteranceId = null
        textToSpeech?.stop()
        stopProgressUpdates()
    }

    private fun currentPlaybackPosition(
        chapter: PlaybackChapter,
        playbackSpeed: Float,
    ): Long {
        val elapsedMs = (SystemClock.elapsedRealtime() - utteranceStartElapsedRealtimeMs).coerceAtLeast(0L)
        val chapterDurationMs = estimatedDurationMs(chapter.text, playbackSpeed)
        return (playbackBasePositionMs + elapsedMs).coerceIn(0L, chapterDurationMs)
    }

    private fun estimatedDurationMs(
        text: String,
        playbackSpeed: Float,
    ): Long {
        val charsPerSecond = BASE_CHARACTERS_PER_SECOND * playbackSpeed.coerceAtLeast(0.1f)
        return ((text.length / charsPerSecond) * 1_000f).toLong().coerceAtLeast(1_000L)
    }

    private fun estimatedCharacterOffset(
        text: String,
        resumePositionMs: Long,
        playbackSpeed: Float,
    ): Int {
        val charsPerSecond = BASE_CHARACTERS_PER_SECOND * playbackSpeed.coerceAtLeast(0.1f)
        val offset = ((resumePositionMs / 1_000f) * charsPerSecond).toInt()
        return offset.coerceIn(0, text.length)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
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

        private const val NOTIFICATION_CHANNEL_ID: String = "vodr_playback"
        private const val NOTIFICATION_ID: Int = 1001
        private const val SESSION_TAG: String = "vodr-playback-session"
        private const val PROGRESS_UPDATE_INTERVAL_MS: Long = 500L
        private const val BASE_CHARACTERS_PER_SECOND: Float = 14f
        private const val REQUEST_CONTENT_INTENT: Int = 1
        private const val REQUEST_PREVIOUS: Int = 2
        private const val REQUEST_PLAY: Int = 3
        private const val REQUEST_PAUSE: Int = 4
        private const val REQUEST_NEXT: Int = 5
    }
}
