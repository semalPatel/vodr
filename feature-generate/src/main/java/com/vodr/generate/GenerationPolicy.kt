package com.vodr.generate

enum class GenerationMode {
    QUALITY,
    BALANCED,
    FAST_START,
}

class GenerationPolicy(
    private val balancedInitialChunkCount: Int = 2,
    private val fastStartInitialChunkCount: Int = 1,
) {
    init {
        require(balancedInitialChunkCount > 0) {
            "balancedInitialChunkCount must be greater than 0"
        }
        require(fastStartInitialChunkCount > 0) {
            "fastStartInitialChunkCount must be greater than 0"
        }
    }

    fun schedule(
        mode: GenerationMode,
        chunkCount: Int,
    ): List<List<Int>> {
        require(chunkCount >= 0) {
            "chunkCount must be greater than or equal to 0"
        }
        if (chunkCount == 0) {
            return emptyList()
        }

        val firstWaveSize = when (mode) {
            GenerationMode.QUALITY -> chunkCount
            GenerationMode.BALANCED -> minOf(balancedInitialChunkCount, chunkCount)
            GenerationMode.FAST_START -> minOf(fastStartInitialChunkCount, chunkCount)
        }

        val firstWave = (0 until firstWaveSize).toList()
        val remaining = (firstWaveSize until chunkCount).toList()

        return buildList {
            add(firstWave)
            if (remaining.isNotEmpty()) {
                add(remaining)
            }
        }
    }
}
