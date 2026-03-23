package com.vodr.generate

data class GenerationJob(
    val documentId: String,
    val chunkIndex: Int,
    val waveIndex: Int,
)

class GenerationWorker(
    private val policy: GenerationPolicy = GenerationPolicy(),
) {
    fun schedule(
        documentId: String,
        mode: GenerationMode,
        chunkCount: Int,
    ): List<GenerationJob> {
        return policy.schedule(
            mode = mode,
            chunkCount = chunkCount,
        ).flatMapIndexed { waveIndex, wave ->
            wave.map { chunkIndex ->
                GenerationJob(
                    documentId = documentId,
                    chunkIndex = chunkIndex,
                    waveIndex = waveIndex,
                )
            }
        }
    }
}
