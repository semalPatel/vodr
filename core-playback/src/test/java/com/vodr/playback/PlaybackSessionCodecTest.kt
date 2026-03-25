package com.vodr.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackSessionCodecTest {

    @Test
    fun encodeAndDecode_roundTripsPlaybackSessionWithEscapedText() {
        val snapshot = PlaybackSessionSnapshot(
            queue = listOf(
                PlaybackChapter(
                    id = "chapter-1",
                    title = "Intro\tOne",
                    text = "Hello\\world\nSecond line",
                ),
                PlaybackChapter(
                    id = "chapter-2",
                    title = "Two",
                    text = "Another chapter",
                ),
            ),
            activeDocument = PlaybackDocument(
                title = "My Book",
                sourceUri = "content://books/demo",
                mimeType = "application/epub+zip",
            ),
            currentChapterIndex = 1,
            resumePositionMs = 42_000L,
            playbackSpeed = 1.25f,
        )

        val restored = PlaybackSessionCodec.decode(
            PlaybackSessionCodec.encode(snapshot),
        )

        assertNotNull(restored)
        assertEquals(snapshot, restored)
    }

    @Test
    fun decode_clampsIndexAndRejectsEmptyQueue() {
        val serialized = """
            VODR_PLAYBACK_SESSION_V1
            STATE	99	1500	1.0
            CHAPTER	chapter-1	One	Text
        """.trimIndent()

        val restored = PlaybackSessionCodec.decode(serialized)

        assertNotNull(restored)
        assertEquals(0, restored?.currentChapterIndex)
        assertEquals(1, restored?.queue?.size)
    }
}
