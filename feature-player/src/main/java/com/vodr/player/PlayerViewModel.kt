package com.vodr.player

import androidx.lifecycle.ViewModel
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.PlaybackDocument
import com.vodr.playback.PlaybackRuntimeMetadata
import com.vodr.playback.PlaybackState
import com.vodr.playback.VodrPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: VodrPlayerController,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = controller.state

    fun updateQueue(
        queue: List<PlaybackChapter>,
        activeDocument: PlaybackDocument? = state.value.activeDocument,
        runtimeMetadata: PlaybackRuntimeMetadata? = state.value.runtimeMetadata,
    ) {
        val isNewDocument = activeDocument != null && activeDocument != state.value.activeDocument
        controller.updateQueue(
            queue = queue,
            activeDocument = activeDocument,
            runtimeMetadata = runtimeMetadata,
            currentChapterIndex = if (isNewDocument) 0 else state.value.currentChapterIndex,
            resumePositionMs = if (isNewDocument) 0L else state.value.resumePositionMs,
        )
    }

    fun togglePlayback() {
        when (state.value.playbackStatus) {
            com.vodr.playback.PlaybackStatus.PLAYING,
            com.vodr.playback.PlaybackStatus.PREPARING,
            -> controller.pause()
            else -> controller.play()
        }
    }

    fun goToNextChapter() {
        controller.goToNextChapter()
    }

    fun goToPreviousChapter() {
        controller.goToPreviousChapter()
    }

    fun seekForward() {
        controller.seekForward()
    }

    fun seekBackward() {
        controller.seekBackward()
    }

    fun updateResumePosition(resumePositionMs: Long) {
        controller.updateResumePosition(resumePositionMs)
    }

    fun updatePlaybackSpeed(playbackSpeed: Float) {
        controller.setPlaybackSpeed(playbackSpeed)
    }

    fun selectChapter(chapterIndex: Int) {
        controller.selectChapter(chapterIndex)
    }

    fun restoreSession(sessionId: String) {
        controller.restoreSession(sessionId)
    }

    fun removeSession(sessionId: String) {
        controller.removeSession(sessionId)
    }

    fun setSessionFavorite(
        sessionId: String,
        isFavorite: Boolean,
    ) {
        controller.setSessionFavorite(
            sessionId = sessionId,
            isFavorite = isFavorite,
        )
    }
}
