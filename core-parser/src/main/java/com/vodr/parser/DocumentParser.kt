package com.vodr.parser

import java.io.InputStream

data class ChapterMarker(
    val title: String,
    val startOffset: Int,
)

data class ParsedDocument(
    val text: String,
    val chapters: List<ChapterMarker>,
)

class DocumentParser(
    private val pdfParser: PdfParser = PdfParser(),
    private val epubParser: EpubParser = EpubParser(),
) {
    fun parse(inputStream: InputStream, mimeType: String): ParsedDocument {
        return when (mimeType) {
            "application/pdf" -> pdfParser.parse(inputStream)
            "application/epub+zip" -> epubParser.parse(inputStream)
            else -> error("Unsupported MIME type: $mimeType")
        }
    }
}

internal fun buildParsedDocument(lines: List<String>): ParsedDocument {
    val text = lines.joinToString(separator = "\n")
    val chapters = mutableListOf<ChapterMarker>()
    var offset = 0

    for (line in lines) {
        if (isChapterHeading(line)) {
            chapters += ChapterMarker(title = line, startOffset = offset)
        }
        offset += line.length + 1
    }

    return ParsedDocument(text = text, chapters = chapters)
}

internal fun normalizeDocumentLines(text: String): List<String> {
    return text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotBlank() }
}

internal fun isChapterHeading(line: String): Boolean {
    return line.matches(Regex("""Chapter\s+\d+[:.].*"""))
}
