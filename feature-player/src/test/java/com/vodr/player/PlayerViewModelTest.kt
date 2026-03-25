package com.vodr.player

import com.vodr.playback.PlaybackChapter
import com.vodr.playback.PlaybackDocument
import com.vodr.playback.PlaybackRuntimeMetadata
import com.vodr.playback.PlaybackState
import com.vodr.playback.PlaybackStatus
import com.vodr.playback.VodrPlayerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModelTest {

    @Test
    fun resumePositionStartsAtZeroAndCanBeUpdated() {
        val viewModel = PlayerViewModel(FakeVodrPlayerController())

        assertEquals(0L, viewModel.state.value.resumePositionMs)

        viewModel.updateResumePosition(12_345L)

        assertEquals(12_345L, viewModel.state.value.resumePositionMs)
    }

    @Test
    fun chapterNavigationUpdatesIndex() {
        val viewModel = PlayerViewModel(FakeVodrPlayerController())

        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
                PlaybackChapter(id = "chapter-3", title = "Three", text = "Chapter three"),
            ),
        )

        assertEquals(0, viewModel.state.value.currentChapterIndex)

        viewModel.goToNextChapter()

        assertEquals(1, viewModel.state.value.currentChapterIndex)

        viewModel.goToPreviousChapter()

        assertEquals(0, viewModel.state.value.currentChapterIndex)
    }

    @Test
    fun queueUpdateKeepsCurrentIndexWithinTheNewQueueBounds() {
        val viewModel = PlayerViewModel(FakeVodrPlayerController())

        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
                PlaybackChapter(id = "chapter-3", title = "Three", text = "Chapter three"),
            ),
        )
        viewModel.goToNextChapter()
        viewModel.goToNextChapter()

        assertEquals(2, viewModel.state.value.currentChapterIndex)

        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
            ),
        )

        assertEquals(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
            ),
            viewModel.state.value.queue,
        )
        assertEquals(1, viewModel.state.value.currentChapterIndex)
    }

    @Test
    fun togglePlaybackUsesControllerState() {
        val controller = FakeVodrPlayerController()
        val viewModel = PlayerViewModel(controller)
        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
            ),
        )

        viewModel.togglePlayback()

        assertEquals(PlaybackStatus.PLAYING, viewModel.state.value.playbackStatus)

        viewModel.togglePlayback()

        assertEquals(PlaybackStatus.PAUSED, viewModel.state.value.playbackStatus)
    }

    @Test
    fun speedAndChapterSelectionDelegateToController() {
        val controller = FakeVodrPlayerController()
        val viewModel = PlayerViewModel(controller)
        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
            ),
        )

        viewModel.updatePlaybackSpeed(1.25f)
        viewModel.selectChapter(1)

        assertEquals(1.25f, viewModel.state.value.playbackSpeed, 0.0f)
        assertEquals(1, viewModel.state.value.currentChapterIndex)
        assertTrue(controller.selectChapterCalled)
    }

    @Test
    fun restoreSessionDelegatesToController() {
        val controller = FakeVodrPlayerController()
        val viewModel = PlayerViewModel(controller)

        viewModel.restoreSession("content://book/one")

        assertEquals("content://book/one", controller.restoredSessionId)
    }

    @Test
    fun removeSessionDelegatesToController() {
        val controller = FakeVodrPlayerController()
        val viewModel = PlayerViewModel(controller)

        viewModel.removeSession("content://book/two")

        assertEquals("content://book/two", controller.removedSessionId)
    }

    @Test
    fun updatingQueueForNewDocument_resetsChapterAndResumePosition() {
        val viewModel = PlayerViewModel(FakeVodrPlayerController())

        viewModel.updateQueue(
            queue = listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
            ),
            activeDocument = PlaybackDocument(
                title = "First Book",
                sourceUri = "content://first",
                mimeType = "application/pdf",
            ),
        )
        viewModel.selectChapter(1)
        viewModel.updateResumePosition(12_000L)

        viewModel.updateQueue(
            queue = listOf(
                PlaybackChapter(id = "chapter-3", title = "Three", text = "Chapter three"),
                PlaybackChapter(id = "chapter-4", title = "Four", text = "Chapter four"),
            ),
            activeDocument = PlaybackDocument(
                title = "Second Book",
                sourceUri = "content://second",
                mimeType = "application/epub+zip",
            ),
        )

        assertEquals(0, viewModel.state.value.currentChapterIndex)
        assertEquals(0L, viewModel.state.value.resumePositionMs)
    }

    private class FakeVodrPlayerController : VodrPlayerController {
        private val mutableState = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

        var selectChapterCalled: Boolean = false
        var restoredSessionId: String? = null
        var removedSessionId: String? = null

        override fun updateQueue(
            queue: List<PlaybackChapter>,
            activeDocument: PlaybackDocument?,
            runtimeMetadata: PlaybackRuntimeMetadata?,
            currentChapterIndex: Int,
            resumePositionMs: Long,
        ) {
            mutableState.value = mutableState.value.copy(
                queue = queue,
                activeDocument = activeDocument,
                runtimeMetadata = runtimeMetadata,
                currentChapterIndex = if (queue.isEmpty()) {
                    0
                } else {
                    currentChapterIndex.coerceIn(0, queue.lastIndex)
                },
                resumePositionMs = resumePositionMs,
            )
        }

        override fun play() {
            mutableState.value = mutableState.value.copy(playbackStatus = PlaybackStatus.PLAYING)
        }

        override fun pause() {
            mutableState.value = mutableState.value.copy(playbackStatus = PlaybackStatus.PAUSED)
        }

        override fun goToNextChapter() {
            val current = mutableState.value
            if (current.currentChapterIndex < current.queue.lastIndex) {
                mutableState.value = current.copy(currentChapterIndex = current.currentChapterIndex + 1)
            }
        }

        override fun goToPreviousChapter() {
            val current = mutableState.value
            if (current.currentChapterIndex > 0) {
                mutableState.value = current.copy(currentChapterIndex = current.currentChapterIndex - 1)
            }
        }

        override fun seekForward(incrementMs: Long) {
            updateResumePosition(mutableState.value.resumePositionMs + incrementMs)
        }

        override fun seekBackward(incrementMs: Long) {
            updateResumePosition((mutableState.value.resumePositionMs - incrementMs).coerceAtLeast(0L))
        }

        override fun updateResumePosition(resumePositionMs: Long) {
            mutableState.value = mutableState.value.copy(resumePositionMs = resumePositionMs)
        }

        override fun setPlaybackSpeed(playbackSpeed: Float) {
            mutableState.value = mutableState.value.copy(playbackSpeed = playbackSpeed)
        }

        override fun selectChapter(chapterIndex: Int) {
            selectChapterCalled = true
            val current = mutableState.value
            mutableState.value = current.copy(
                currentChapterIndex = if (current.queue.isEmpty()) {
                    0
                } else {
                    chapterIndex.coerceIn(0, current.queue.lastIndex)
                },
                resumePositionMs = 0L,
            )
        }

        override fun restoreSession(sessionId: String) {
            restoredSessionId = sessionId
        }

        override fun removeSession(sessionId: String) {
            removedSessionId = sessionId
        }
    }
}
