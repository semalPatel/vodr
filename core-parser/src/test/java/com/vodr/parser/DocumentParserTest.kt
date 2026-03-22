package com.vodr.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserTest {

    private val parser = DocumentParser()

    @Test
    fun parsesGoldenPdfWithChapterMarkers() {
        val input = requireNotNull(javaClass.getResourceAsStream("/samples/sample.pdf")) {
            "Missing PDF sample"
        }

        val result = parser.parse(input, "application/pdf")

        assertTrue(result.text.contains("Chapter 1: Getting Started"))
        assertTrue(result.text.contains("This is the first PDF chapter."))
        assertTrue(result.text.contains("Chapter 2: Deep Dive"))
        assertTrue(result.text.contains("More PDF details follow."))
        assertEquals(
            listOf(
                "Chapter 1: Getting Started",
                "Chapter 2: Deep Dive",
            ),
            result.chapters.map { it.title },
        )
    }

    @Test
    fun parsesGoldenEpubWithChapterMarkers() {
        val input = requireNotNull(javaClass.getResourceAsStream("/samples/sample.epub")) {
            "Missing EPUB sample"
        }

        val result = parser.parse(input, "application/epub+zip")

        assertTrue(result.text.contains("Chapter 1: Overview"))
        assertTrue(result.text.contains("This is the first EPUB chapter."))
        assertTrue(result.text.contains("Chapter 2: Details"))
        assertTrue(result.text.contains("More EPUB details follow."))
        assertEquals(
            listOf(
                "Chapter 1: Overview",
                "Chapter 2: Details",
            ),
            result.chapters.map { it.title },
        )
    }
}
