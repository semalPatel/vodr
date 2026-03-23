package com.vodr.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vodr.playback.InMemoryVodrPlayerController
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.VodrPlayerController

data class PlayerUiState(
    val queue: List<PlaybackChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val resumePositionMs: Long = 0L,
)

class PlayerViewModel(
    private val controller: VodrPlayerController = InMemoryVodrPlayerController(),
) {
    var state by mutableStateOf(PlayerUiState())
        private set

    fun updateQueue(queue: List<PlaybackChapter>) {
        val clampedIndex = if (queue.isEmpty()) {
            0
        } else {
            state.currentChapterIndex.coerceIn(0, queue.lastIndex)
        }
        state = state.copy(
            queue = queue,
            currentChapterIndex = clampedIndex,
        )
        controller.updateQueue(
            queue = queue,
            currentChapterIndex = clampedIndex,
            resumePositionMs = state.resumePositionMs,
        )
    }

    fun goToNextChapter() {
        if (state.currentChapterIndex < state.queue.lastIndex) {
            val nextIndex = state.currentChapterIndex + 1
            state = state.copy(currentChapterIndex = nextIndex)
            controller.goToNextChapter()
        }
    }

    fun goToPreviousChapter() {
        if (state.currentChapterIndex > 0) {
            val previousIndex = state.currentChapterIndex - 1
            state = state.copy(currentChapterIndex = previousIndex)
            controller.goToPreviousChapter()
        }
    }

    fun updateResumePosition(resumePositionMs: Long) {
        state = state.copy(resumePositionMs = resumePositionMs)
        controller.updateResumePosition(resumePositionMs)
    }
}
