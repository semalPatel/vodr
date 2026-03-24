package com.vodr.player

import androidx.lifecycle.ViewModel
import com.vodr.playback.InMemoryVodrPlayerController
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.VodrPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    val queue: List<PlaybackChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val resumePositionMs: Long = 0L,
)

class PlayerViewModel(
    private val controller: VodrPlayerController = InMemoryVodrPlayerController(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    fun updateQueue(queue: List<PlaybackChapter>) {
        val clampedIndex = if (queue.isEmpty()) {
            0
        } else {
            state.value.currentChapterIndex.coerceIn(0, queue.lastIndex)
        }
        mutableState.update {
            it.copy(
            queue = queue,
            currentChapterIndex = clampedIndex,
            )
        }
        controller.updateQueue(
            queue = queue,
            currentChapterIndex = clampedIndex,
            resumePositionMs = state.value.resumePositionMs,
        )
    }

    fun goToNextChapter() {
        if (state.value.currentChapterIndex < state.value.queue.lastIndex) {
            val nextIndex = state.value.currentChapterIndex + 1
            mutableState.update { it.copy(currentChapterIndex = nextIndex) }
            controller.goToNextChapter()
        }
    }

    fun goToPreviousChapter() {
        if (state.value.currentChapterIndex > 0) {
            val previousIndex = state.value.currentChapterIndex - 1
            mutableState.update { it.copy(currentChapterIndex = previousIndex) }
            controller.goToPreviousChapter()
        }
    }

    fun updateResumePosition(resumePositionMs: Long) {
        mutableState.update { it.copy(resumePositionMs = resumePositionMs) }
        controller.updateResumePosition(resumePositionMs)
    }
}
