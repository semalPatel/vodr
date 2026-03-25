package com.vodr.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackSessionCodecTest {

    @Test
    fun encodeAndDecode_roundTripsPlaybackSessionWithEscapedText() {
        val snapshot = PlaybackSessionSnapshot(
            sessionId = "content://books/demo",
            updatedAtEpochMs = 123_456L,
            isFavorite = true,
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
            runtimeMetadata = PlaybackRuntimeMetadata(
                personalizationProviderLabel = "Device AI",
                personalizationDetail = "Using on-device model",
                transcriptionProviderLabel = "Offline Heuristic",
                transcriptionDetail = "Offline-only fallback active",
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
    fun historyCodec_roundTripsMultipleSnapshotsInOrder() {
        val first = PlaybackSessionSnapshot(
            sessionId = "content://books/one",
            updatedAtEpochMs = 2_000L,
            isFavorite = true,
            queue = listOf(
                PlaybackChapter(id = "chapter-1", title = "One", text = "One"),
            ),
            activeDocument = PlaybackDocument(
                title = "Book One",
                sourceUri = "content://books/one",
                mimeType = "application/pdf",
            ),
            runtimeMetadata = null,
            currentChapterIndex = 0,
            resumePositionMs = 900L,
            playbackSpeed = 1.0f,
        )
        val second = PlaybackSessionSnapshot(
            sessionId = "content://books/two",
            updatedAtEpochMs = 1_000L,
            queue = listOf(
                PlaybackChapter(id = "chapter-2", title = "Two", text = "Two"),
            ),
            activeDocument = PlaybackDocument(
                title = "Book Two",
                sourceUri = "content://books/two",
                mimeType = "application/epub+zip",
            ),
            runtimeMetadata = PlaybackRuntimeMetadata(
                personalizationProviderLabel = "Device AI",
                transcriptionProviderLabel = "Offline Heuristic",
            ),
            currentChapterIndex = 0,
            resumePositionMs = 1_200L,
            playbackSpeed = 1.25f,
        )

        val restored = PlaybackSessionHistoryCodec.decode(
            PlaybackSessionHistoryCodec.encode(listOf(first, second)),
        )

        assertEquals(listOf(first, second), restored)
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
