package com.vodr.segmentation

data class SegmentedChunk(
    val id: String,
    val documentId: String,
    val chapterIndex: Int,
    val chunkIndex: Int,
    val text: String,
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

                var chunkIndex = 0
                var start = 0
                while (start < chapterText.length) {
                    val end = minOf(start + policy.maxCharsPerChunk, chapterText.length)
                    val chunkText = chapterText.substring(start, end)
                    if (chunkText.isNotEmpty()) {
                        add(
                            SegmentedChunk(
                                id = policy.chunkId(documentId, chapterIndex, chunkIndex),
                                documentId = documentId,
                                chapterIndex = chapterIndex,
                                chunkIndex = chunkIndex,
                                text = chunkText,
                            ),
                        )
                        chunkIndex += 1
                    }
                    start = end
                }
            }
        }
    }
}
