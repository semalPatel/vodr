package com.vodr.segmentation

data class ChunkPolicy(
    val maxCharsPerChunk: Int,
) {
    init {
        require(maxCharsPerChunk > 0) {
            "maxCharsPerChunk must be greater than 0"
        }
    }

    fun chunkId(documentId: String, chapterIndex: Int, chunkIndex: Int): String {
        return "$documentId/$chapterIndex/$chunkIndex"
    }
}
