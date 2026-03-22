package com.vodr.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class LibraryUiState(
    val documents: List<ImportedDocument> = emptyList(),
    val lastImportedDocumentId: Long? = null,
    val errorMessage: String? = null,
)

class LibraryViewModel(
    private val importDocumentUseCase: ImportDocumentUseCase = ImportDocumentUseCase(),
) {
    var state by mutableStateOf(LibraryUiState())
        private set

    fun importDocument(
        request: ImportDocumentRequest,
        currentTimeEpochMs: Long = System.currentTimeMillis(),
    ) {
        state = try {
            val document = importDocumentUseCase.importDocument(
                request = request,
                currentTimeEpochMs = currentTimeEpochMs,
            )
            state.copy(
                documents = state.documents + document,
                lastImportedDocumentId = document.id,
                errorMessage = null,
            )
        } catch (exception: IllegalArgumentException) {
            state.copy(
                errorMessage = exception.message,
            )
        }
    }
}
