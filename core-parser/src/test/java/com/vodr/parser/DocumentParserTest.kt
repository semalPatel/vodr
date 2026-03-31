package com.vodr.parser

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentParserTest {

    private val parser = DocumentParser()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun parsesGoldenPdfWithChapterMarkers() {
        val input = createSamplePdfInputStream()

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

    @Test
    fun sanitizeExtractedPdfText_removesBinaryControlNoise() {
        val sanitized = sanitizeExtractedPdfText(
            "Hello\u0000\u0007World\uFFFD",
        )

        assertEquals("Hello  World ", sanitized)
    }

    private fun createSamplePdfInputStream(): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(72f, 720f)
                listOf(
                    "Chapter 1: Getting Started",
                    "This is the first PDF chapter.",
                    "Chapter 2: Deep Dive",
                    "More PDF details follow.",
                ).forEachIndexed { index, line ->
                    if (index > 0) {
                        content.newLineAtOffset(0f, -18f)
                    }
                    content.showText(line)
                }
                content.endText()
            }
            document.save(output)
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
