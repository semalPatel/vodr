package com.vodr.parser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfParser {

    fun parse(inputStream: InputStream): ParsedDocument {
        val extractedText = runCatching {
            val pdfBytes = inputStream.readBytes()
            require(pdfBytes.isNotEmpty()) { "PDF file is empty." }
            PDDocument.load(pdfBytes).use { document ->
                if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) {
                    throw IllegalStateException("This PDF blocks text extraction.")
                }
                PDFTextStripper().apply {
                    sortByPosition = true
                }.getText(document)
            }
        }.getOrElse { error ->
            throw IllegalStateException("Unable to read this PDF as text.", error)
        }
        val lines = normalizeDocumentLines(
            sanitizeExtractedPdfText(extractedText),
        )
        if (lines.isEmpty()) {
            throw IllegalStateException(
                "This PDF does not contain readable text. If it is a scanned document, export it with OCR first."
            )
        }
        return buildParsedDocument(lines)
    }
}

internal fun sanitizeExtractedPdfText(text: String): String {
    return text
        .replace(NON_RENDERED_CONTROL_REGEX, " ")
        .replace('\u0000', ' ')
        .replace('\uFFFD', ' ')
        .replace('\u00A0', ' ')
}

private val NON_RENDERED_CONTROL_REGEX = Regex("[\\u0001-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
