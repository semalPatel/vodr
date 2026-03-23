package com.vodr.playback

data class PlaybackChapter(
    val id: String,
    val title: String,
)

interface VodrPlayerController {
    val queue: List<PlaybackChapter>
    val currentChapterIndex: Int
    val resumePositionMs: Long

    fun updateQueue(
        queue: List<PlaybackChapter>,
        currentChapterIndex: Int = 0,
        resumePositionMs: Long = 0L,
    )

    fun goToNextChapter()

    fun goToPreviousChapter()

    fun updateResumePosition(resumePositionMs: Long)
}

class InMemoryVodrPlayerController : VodrPlayerController {
    override var queue: List<PlaybackChapter> = emptyList()
        private set

    override var currentChapterIndex: Int = 0
        private set

    override var resumePositionMs: Long = 0L
        private set

    override fun updateQueue(
        queue: List<PlaybackChapter>,
        currentChapterIndex: Int,
        resumePositionMs: Long,
    ) {
        this.queue = queue
        this.currentChapterIndex = if (queue.isEmpty()) {
            0
        } else {
            currentChapterIndex.coerceIn(0, queue.lastIndex)
        }
        this.resumePositionMs = resumePositionMs
    }

    override fun goToNextChapter() {
        if (currentChapterIndex < queue.lastIndex) {
            currentChapterIndex += 1
        }
    }

    override fun goToPreviousChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex -= 1
        }
    }

    override fun updateResumePosition(resumePositionMs: Long) {
        this.resumePositionMs = resumePositionMs
    }
}
