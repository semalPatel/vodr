package com.vodr.generate

data class GenerationUiState(
    val jobs: List<GenerationJob> = emptyList(),
    val errorMessage: String? = null,
)

class GenerationViewModel(
    private val worker: GenerationWorker = GenerationWorker(),
) {
    var state: GenerationUiState = GenerationUiState()
        private set

    fun planGeneration(
        documentId: String,
        mode: GenerationMode,
        chunkCount: Int,
    ) {
        state = try {
            GenerationUiState(
                jobs = worker.schedule(
                    documentId = documentId,
                    mode = mode,
                    chunkCount = chunkCount,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            state.copy(errorMessage = exception.message)
        }
    }
}
