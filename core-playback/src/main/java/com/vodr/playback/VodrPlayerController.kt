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

data class PlaybackDocument(
    val title: String,
    val sourceUri: String,
    val mimeType: String,
)

data class PlaybackRuntimeMetadata(
    val personalizationProviderLabel: String? = null,
    val personalizationDetail: String? = null,
    val transcriptionProviderLabel: String? = null,
    val transcriptionDetail: String? = null,
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
    val activeDocument: PlaybackDocument? = null,
    val runtimeMetadata: PlaybackRuntimeMetadata? = null,
    val currentChapterIndex: Int = 0,
    val resumePositionMs: Long = 0L,
    val currentChapterDurationMs: Long = 0L,
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
        activeDocument: PlaybackDocument? = null,
        runtimeMetadata: PlaybackRuntimeMetadata? = null,
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
        activeDocument: PlaybackDocument?,
        runtimeMetadata: PlaybackRuntimeMetadata?,
        currentChapterIndex: Int,
        resumePositionMs: Long,
    ) {
        mutableState.update { current ->
            val clampedIndex = clampChapterIndex(queue, currentChapterIndex)
            current.copy(
                queue = queue,
                activeDocument = activeDocument ?: current.activeDocument,
                runtimeMetadata = runtimeMetadata ?: current.runtimeMetadata,
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
                    currentChapterDurationMs = it.queue.getOrNull(it.currentChapterIndex + 1)?.let { chapter ->
                        PlaybackEstimator.estimatedDurationMs(
                            text = chapter.text,
                            playbackSpeed = it.playbackSpeed,
                        )
                    } ?: 0L,
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
                    currentChapterDurationMs = it.queue.getOrNull(it.currentChapterIndex - 1)?.let { chapter ->
                        PlaybackEstimator.estimatedDurationMs(
                            text = chapter.text,
                            playbackSpeed = it.playbackSpeed,
                        )
                    } ?: 0L,
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
            val clampedSpeed = playbackSpeed.coerceIn(0.75f, 2.0f)
            it.copy(
                playbackSpeed = clampedSpeed,
                currentChapterDurationMs = it.currentChapter?.let { chapter ->
                    PlaybackEstimator.estimatedDurationMs(
                        text = chapter.text,
                        playbackSpeed = clampedSpeed,
                    )
                } ?: 0L,
            )
        }
    }

    override fun selectChapter(chapterIndex: Int) {
        mutableState.update { current ->
            val clampedIndex = clampChapterIndex(current.queue, chapterIndex)
            current.copy(
                currentChapterIndex = clampedIndex,
                resumePositionMs = 0L,
                currentChapterDurationMs = current.queue.getOrNull(clampedIndex)?.let { chapter ->
                    PlaybackEstimator.estimatedDurationMs(
                        text = chapter.text,
                        playbackSpeed = current.playbackSpeed,
                    )
                } ?: 0L,
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
