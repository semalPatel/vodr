package com.vodr.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackChapter(
    val id: String,
    val title: String,
    val text: String,
)

enum class PlaybackStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR,
}

data class PlaybackState(
    val queue: List<PlaybackChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val resumePositionMs: Long = 0L,
    val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val isVoiceReady: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentChapter: PlaybackChapter?
        get() = queue.getOrNull(currentChapterIndex)

    companion object {
        const val DEFAULT_PLAYBACK_SPEED: Float = 1.0f
        const val DEFAULT_SEEK_INCREMENT_MS: Long = 15_000L
    }
}

interface VodrPlayerController {
    val state: StateFlow<PlaybackState>

    fun updateQueue(
        queue: List<PlaybackChapter>,
        currentChapterIndex: Int = 0,
        resumePositionMs: Long = 0L,
    )

    fun play()

    fun pause()

    fun goToNextChapter()

    fun goToPreviousChapter()

    fun seekForward(incrementMs: Long = PlaybackState.DEFAULT_SEEK_INCREMENT_MS)

    fun seekBackward(incrementMs: Long = PlaybackState.DEFAULT_SEEK_INCREMENT_MS)

    fun updateResumePosition(resumePositionMs: Long)

    fun setPlaybackSpeed(playbackSpeed: Float)

    fun selectChapter(chapterIndex: Int)
}

class InMemoryVodrPlayerController : VodrPlayerController {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    override fun updateQueue(
        queue: List<PlaybackChapter>,
        currentChapterIndex: Int,
        resumePositionMs: Long,
    ) {
        mutableState.update { current ->
            current.copy(
                queue = queue,
                currentChapterIndex = clampChapterIndex(queue, currentChapterIndex),
                resumePositionMs = resumePositionMs.coerceAtLeast(0L),
                playbackStatus = if (queue.isEmpty()) PlaybackStatus.IDLE else current.playbackStatus,
                errorMessage = null,
            )
        }
    }

    override fun play() {
        if (state.value.queue.isNotEmpty()) {
            mutableState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.PLAYING,
                    isVoiceReady = true,
                    errorMessage = null,
                )
            }
        }
    }

    override fun pause() {
        mutableState.update {
            it.copy(playbackStatus = PlaybackStatus.PAUSED)
        }
    }

    override fun goToNextChapter() {
        val currentState = state.value
        if (currentState.currentChapterIndex < currentState.queue.lastIndex) {
            mutableState.update {
                it.copy(
                    currentChapterIndex = it.currentChapterIndex + 1,
                    resumePositionMs = 0L,
                )
            }
        }
    }

    override fun goToPreviousChapter() {
        val currentState = state.value
        if (currentState.currentChapterIndex > 0) {
            mutableState.update {
                it.copy(
                    currentChapterIndex = it.currentChapterIndex - 1,
                    resumePositionMs = 0L,
                )
            }
        }
    }

    override fun seekForward(incrementMs: Long) {
        updateResumePosition(state.value.resumePositionMs + incrementMs)
    }

    override fun seekBackward(incrementMs: Long) {
        updateResumePosition((state.value.resumePositionMs - incrementMs).coerceAtLeast(0L))
    }

    override fun updateResumePosition(resumePositionMs: Long) {
        mutableState.update {
            it.copy(resumePositionMs = resumePositionMs.coerceAtLeast(0L))
        }
    }

    override fun setPlaybackSpeed(playbackSpeed: Float) {
        mutableState.update {
            it.copy(playbackSpeed = playbackSpeed.coerceIn(0.75f, 2.0f))
        }
    }

    override fun selectChapter(chapterIndex: Int) {
        mutableState.update { current ->
            current.copy(
                currentChapterIndex = clampChapterIndex(current.queue, chapterIndex),
                resumePositionMs = 0L,
            )
        }
    }

    private fun clampChapterIndex(
        queue: List<PlaybackChapter>,
        currentChapterIndex: Int,
    ): Int {
        return if (queue.isEmpty()) {
            0
        } else {
            currentChapterIndex.coerceIn(0, queue.lastIndex)
        }
    }
}
