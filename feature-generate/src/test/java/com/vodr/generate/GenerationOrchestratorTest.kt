package com.vodr.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationOrchestratorTest {
    @Test
    fun buildReadableChapterTitle_prefersExplicitHeading() {
        val title = buildReadableChapterTitle(
            explicitTitle = " Chapter 3: Deep Dive. ",
            chapterText = "Ignored body text.",
            chapterIndex = 2,
        )

        assertEquals("Chapter 3: Deep Dive", title)
    }

    @Test
    fun buildReadableChapterTitle_fallsBackToReadableSectionSnippet() {
        val title = buildReadableChapterTitle(
            explicitTitle = null,
            chapterText = "This chapter explains how offline playback works. More detail follows.",
            chapterIndex = 1,
        )

        assertEquals(
            "Section 2: This chapter explains how offline playback works",
            title,
        )
    }

    @Test
    fun deriveFallbackChapterLabel_returnsNullForBlankInput() {
        assertNull(deriveFallbackChapterLabel("   \n\t  "))
    }
}
