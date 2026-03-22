package com.vodr.segmentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {

    private val segmenter = Segmenter(ChunkPolicy(maxCharsPerChunk = 5))

    @Test
    fun producesStableChunkIdsForTheSameInput() {
        val documentId = "doc-123"
        val chapters = listOf(
            "abcdefgh",
            "ijkl",
        )

        val firstRun = segmenter.segment(documentId, chapters)
        val secondRun = segmenter.segment(documentId, chapters)

        assertEquals(firstRun.map { it.id }, secondRun.map { it.id })
        assertEquals(
            listOf(
                "doc-123/0/0",
                "doc-123/0/1",
                "doc-123/1/0",
            ),
            firstRun.map { it.id },
        )
    }

    @Test
    fun enforcesMaxCharsPerChunkWhilePreservingOrderAndSkippingEmptyChunks() {
        val documentId = "doc-456"
        val chapters = listOf(
            "abcde123",
            "",
            "xy",
        )

        val chunks = segmenter.segment(documentId, chapters)

        assertEquals(
            listOf(
                "abcde",
                "123",
                "xy",
            ),
            chunks.map { it.text },
        )
        assertTrue(chunks.all { it.text.isNotEmpty() })
        assertTrue(chunks.all { it.text.length <= 5 })
        assertEquals(
            listOf(
                0,
                0,
                2,
            ),
            chunks.map { it.chapterIndex },
        )
        assertEquals(
            listOf(
                0,
                1,
                0,
            ),
            chunks.map { it.chunkIndex },
        )
    }
}
