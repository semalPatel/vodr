package com.vodr.parser

import java.io.InputStream
import java.nio.charset.Charset

class PdfParser {

    fun parse(inputStream: InputStream): ParsedDocument {
        val raw = inputStream.readBytes().toString(PDF_CHARSET)
        val fragments = extractTextFragments(raw)
        val lines = normalizeDocumentLines(fragments.joinToString(separator = "\n"))
        return buildParsedDocument(lines)
    }

    private fun extractTextFragments(raw: String): List<String> {
        val fragments = mutableListOf<String>()
        val pattern = Regex("""\((?:\\.|[^\\)])*\)""")
        for (match in pattern.findAll(raw)) {
            fragments += unescapePdfString(match.value.drop(1).dropLast(1))
        }
        return fragments
    }

    private fun unescapePdfString(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                val escaped = value[index + 1]
                builder.append(
                    when (escaped) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        '\\' -> '\\'
                        '(' -> '('
                        ')' -> ')'
                        else -> escaped
                    },
                )
                index += 2
            } else {
                builder.append(character)
                index += 1
            }
        }
        return builder.toString()
    }

    private companion object {
        private val PDF_CHARSET: Charset = Charsets.ISO_8859_1
    }
}
