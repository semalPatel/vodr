package com.vodr.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class ForegroundVodrPlayerController @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val sessionStore: PlaybackSessionStore,
) : VodrPlayerController {
    private val mutableState = MutableStateFlow(
        sessionStore.load()?.toPlaybackState() ?: PlaybackState(),
    )
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    override fun updateQueue(
        queue: List<PlaybackChapter>,
        activeDocument: PlaybackDocument?,
        currentChapterIndex: Int,
        resumePositionMs: Long,
    ) {
        mutableState.update { current ->
            val clampedIndex = clampChapterIndex(queue, currentChapterIndex)
            current.copy(
                queue = queue,
                activeDocument = activeDocument ?: current.activeDocument,
                currentChapterIndex = clampedIndex,
                resumePositionMs = resumePositionMs.coerceAtLeast(0L),
                currentChapterDurationMs = queue.getOrNull(clampedIndex)?.let { chapter ->
                    PlaybackEstimator.estimatedDurationMs(
                        text = chapter.text,
                        playbackSpeed = current.playbackSpeed,
                    )
                } ?: 0L,
                playbackStatus = if (queue.isEmpty()) PlaybackStatus.IDLE else current.playbackStatus,
                errorMessage = null,
            )
        }
        persistCurrentState()
        dispatchAction(VodrPlaybackService.ACTION_SYNC_QUEUE)
    }

    override fun play() {
        if (state.value.queue.isNotEmpty()) {
            mutableState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.PREPARING,
                    errorMessage = null,
                )
            }
            persistCurrentState()
        }
        dispatchForegroundAction(VodrPlaybackService.ACTION_PLAY)
    }

    override fun pause() {
        dispatchAction(VodrPlaybackService.ACTION_PAUSE)
    }

    override fun goToNextChapter() {
        dispatchAction(VodrPlaybackService.ACTION_NEXT)
    }

    override fun goToPreviousChapter() {
        dispatchAction(VodrPlaybackService.ACTION_PREVIOUS)
    }

    override fun seekForward(incrementMs: Long) {
        dispatchAction(
            action = VodrPlaybackService.ACTION_SEEK_FORWARD,
            extraName = VodrPlaybackService.EXTRA_SEEK_INCREMENT_MS,
            longValue = incrementMs,
        )
    }

    override fun seekBackward(incrementMs: Long) {
        dispatchAction(
            action = VodrPlaybackService.ACTION_SEEK_BACKWARD,
            extraName = VodrPlaybackService.EXTRA_SEEK_INCREMENT_MS,
            longValue = incrementMs,
        )
    }

    override fun updateResumePosition(resumePositionMs: Long) {
        dispatchAction(
            action = VodrPlaybackService.ACTION_SEEK_TO_POSITION,
            extraName = VodrPlaybackService.EXTRA_RESUME_POSITION_MS,
            longValue = resumePositionMs,
        )
    }

    override fun setPlaybackSpeed(playbackSpeed: Float) {
        dispatchAction(
            action = VodrPlaybackService.ACTION_SET_SPEED,
            extraName = VodrPlaybackService.EXTRA_PLAYBACK_SPEED,
            floatValue = playbackSpeed,
        )
    }

    override fun selectChapter(chapterIndex: Int) {
        dispatchAction(
            action = VodrPlaybackService.ACTION_SELECT_CHAPTER,
            extraName = VodrPlaybackService.EXTRA_CHAPTER_INDEX,
            intValue = chapterIndex,
        )
    }

    internal fun snapshot(): PlaybackState = state.value

    internal fun updateFromService(state: PlaybackState) {
        mutableState.value = state
        persistCurrentState()
    }

    private fun dispatchForegroundAction(action: String) {
        ContextCompat.startForegroundService(
            applicationContext,
            baseIntent(action),
        )
    }

    private fun dispatchAction(
        action: String,
        extraName: String? = null,
        intValue: Int? = null,
        longValue: Long? = null,
        floatValue: Float? = null,
    ) {
        val intent = baseIntent(action).apply {
            if (extraName != null && intValue != null) {
                putExtra(extraName, intValue)
            }
            if (extraName != null && longValue != null) {
                putExtra(extraName, longValue)
            }
            if (extraName != null && floatValue != null) {
                putExtra(extraName, floatValue)
            }
        }
        applicationContext.startService(intent)
    }

    private fun baseIntent(action: String): Intent {
        return Intent(applicationContext, VodrPlaybackService::class.java).apply {
            this.action = action
        }
    }

    private fun clampChapterIndex(
        queue: List<PlaybackChapter>,
        chapterIndex: Int,
    ): Int {
        return if (queue.isEmpty()) {
            0
        } else {
            chapterIndex.coerceIn(0, queue.lastIndex)
        }
    }

    private fun persistCurrentState() {
        state.value.toPlaybackSessionSnapshot()?.let(sessionStore::save) ?: sessionStore.clear()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackControllerModule {
    @Binds
    @Singleton
    abstract fun bindVodrPlayerController(
        implementation: ForegroundVodrPlayerController,
    ): VodrPlayerController
}
