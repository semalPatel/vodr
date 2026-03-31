package com.vodr.segmentation

data class SegmentedChunk(
    val id: String,
    val documentId: String,
    val chapterIndex: Int,
    val chunkIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val narrationStyle: String,
    val pauseAfterMs: Long,
)

class Segmenter(
    private val policy: ChunkPolicy,
) {
    fun segment(documentId: String, chapters: List<String>): List<SegmentedChunk> {
        return buildList {
            chapters.forEachIndexed { chapterIndex, chapterText ->
                if (chapterText.isBlank()) {
                    return@forEachIndexed
                }

                sentenceAwareChunks(chapterText, policy.maxCharsPerChunk)
                    .forEachIndexed { chunkIndex, chunk ->
                        add(
                            SegmentedChunk(
                                id = policy.chunkId(documentId, chapterIndex, chunkIndex),
                                documentId = documentId,
                                chapterIndex = chapterIndex,
                                chunkIndex = chunkIndex,
                                text = chunk.text,
                                startOffset = chunk.startOffset,
                                endOffset = chunk.endOffset,
                                narrationStyle = chunk.style,
                                pauseAfterMs = chunk.pauseAfterMs,
                            ),
                        )
                    }
            }
        }
    }
}

private data class SentenceAwareChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val style: String,
    val pauseAfterMs: Long,
)

private data class SentencePiece(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

private fun sentenceAwareChunks(
    chapterText: String,
    maxCharsPerChunk: Int,
): List<SentenceAwareChunk> {
    val sentencePieces = buildSentencePieces(chapterText, maxCharsPerChunk)
    if (sentencePieces.isEmpty()) {
        return emptyList()
    }
    val chunks = mutableListOf<SentenceAwareChunk>()
    var currentText = StringBuilder()
    var currentStartOffset = 0
    var currentEndOffset = 0
    sentencePieces.forEach { piece ->
        val candidate = if (currentText.isEmpty()) {
            piece.text
        } else {
            "${currentText.toString()} ${piece.text}"
        }
        if (candidate.length <= maxCharsPerChunk || currentText.isEmpty()) {
            if (currentText.isEmpty()) {
                currentStartOffset = piece.startOffset
            }
            currentText = StringBuilder(candidate)
            currentEndOffset = piece.endOffset
        } else {
            chunks += currentText.toString().toChunk(
                startOffset = currentStartOffset,
                endOffset = currentEndOffset,
            )
            currentText = StringBuilder(piece.text)
            currentStartOffset = piece.startOffset
            currentEndOffset = piece.endOffset
        }
    }
    if (currentText.isNotEmpty()) {
        chunks += currentText.toString().toChunk(
            startOffset = currentStartOffset,
            endOffset = currentEndOffset,
        )
    }
    return chunks
}

private fun buildSentencePieces(
    chapterText: String,
    maxCharsPerChunk: Int,
): List<SentencePiece> {
    val sentenceMatches = SENTENCE_REGEX.findAll(chapterText)
        .map { match ->
            SentencePiece(
                text = match.value.trim(),
                startOffset = match.range.first,
                endOffset = match.range.last + 1,
            )
        }
        .filter { it.text.isNotBlank() }
        .toList()
    if (sentenceMatches.isEmpty()) {
        return fallbackPieces(chapterText, maxCharsPerChunk)
    }
    return sentenceMatches.flatMap { sentence ->
        if (sentence.text.length <= maxCharsPerChunk) {
            listOf(sentence)
        } else {
            fallbackPieces(sentence.text, maxCharsPerChunk, offsetBase = sentence.startOffset)
        }
    }
}

private fun fallbackPieces(
    text: String,
    maxCharsPerChunk: Int,
    offsetBase: Int = 0,
): List<SentencePiece> {
    val pieces = mutableListOf<SentencePiece>()
    var currentStart = 0
    var currentText = StringBuilder()
    text.split(WHITESPACE_REGEX)
        .filter { it.isNotBlank() }
        .forEach { word ->
            val candidate = if (currentText.isEmpty()) word else "${currentText.toString()} $word"
            if (candidate.length <= maxCharsPerChunk || currentText.isEmpty()) {
                currentText = StringBuilder(candidate)
            } else {
                val chunkText = currentText.toString()
                pieces += SentencePiece(
                    text = chunkText,
                    startOffset = offsetBase + currentStart,
                    endOffset = offsetBase + currentStart + chunkText.length,
                )
                currentStart += chunkText.length + 1
                currentText = StringBuilder(word)
            }
        }
    if (currentText.isNotEmpty()) {
        val chunkText = currentText.toString()
        pieces += SentencePiece(
            text = chunkText,
            startOffset = offsetBase + currentStart,
            endOffset = offsetBase + currentStart + chunkText.length,
        )
    }
    return pieces
}

private fun String.toChunk(
    startOffset: Int,
    endOffset: Int,
): SentenceAwareChunk {
    val trimmed = trim()
    val style = when {
        contains('"') || contains('“') || contains('”') -> "DIALOGUE"
        endsWith("?") -> "QUESTION"
        endsWith("!") -> "EXCITED"
        contains("...") || contains("…") -> "SUSPENSE"
        length > 180 -> "REFLECTIVE"
        else -> "NEUTRAL"
    }
    val pauseAfterMs = when (style) {
        "DIALOGUE" -> 260L
        "QUESTION" -> 380L
        "EXCITED" -> 220L
        "SUSPENSE" -> 520L
        "REFLECTIVE" -> 440L
        else -> 320L
    }
    return SentenceAwareChunk(
        text = trimmed,
        startOffset = startOffset,
        endOffset = endOffset,
        style = style,
        pauseAfterMs = pauseAfterMs,
    )
}

private val SENTENCE_REGEX = Regex("[^.!?…]+[.!?…]*")
private val WHITESPACE_REGEX = Regex("\\s+")
