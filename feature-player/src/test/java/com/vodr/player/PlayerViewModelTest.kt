package com.vodr.player

import com.vodr.playback.PlaybackChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerViewModelTest {

    @Test
    fun resumePositionStartsAtZeroAndCanBeUpdated() {
        val viewModel = PlayerViewModel()

        assertEquals(0L, viewModel.state.resumePositionMs)

        viewModel.updateResumePosition(12_345L)

        assertEquals(12_345L, viewModel.state.resumePositionMs)
    }

    @Test
    fun chapterNavigationUpdatesIndex() {
        val viewModel = PlayerViewModel()

        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
                PlaybackChapter(id = "chapter-3", title = "Three", text = "Chapter three"),
            ),
        )

        assertEquals(0, viewModel.state.currentChapterIndex)

        viewModel.goToNextChapter()

        assertEquals(1, viewModel.state.currentChapterIndex)

        viewModel.goToPreviousChapter()

        assertEquals(0, viewModel.state.currentChapterIndex)
    }

    @Test
    fun queueUpdateKeepsCurrentIndexWithinTheNewQueueBounds() {
        val viewModel = PlayerViewModel()

        viewModel.updateQueue(
            listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "Chapter one"),
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Chapter two"),
                PlaybackChapter(id = "chapter-3", title = "Three", text = "Chapter three"),
            ),
        )
        viewModel.goToNextChapter()
        viewModel.goToNextChapter()

        assertEquals(2, viewModel.state.currentChapterIndex)

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
            viewModel.state.queue,
        )
        assertEquals(1, viewModel.state.currentChapterIndex)
    }
}
