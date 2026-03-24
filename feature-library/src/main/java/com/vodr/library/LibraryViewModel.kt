package com.vodr.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val documents: List<ImportedDocument> = emptyList(),
    val lastImportedDocumentId: Long? = null,
    val errorMessage: String? = null,
    val isImporting: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val importDocumentUseCase: ImportDocumentUseCase,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            importDocumentUseCase.observeDocuments().collect { documents ->
                mutableState.update { current ->
                    current.copy(documents = documents)
                }
            }
        }
    }

    fun importDocument(
        request: ImportDocumentRequest,
        currentTimeEpochMs: Long = System.currentTimeMillis(),
    ) {
        scope.launch {
            mutableState.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                )
            }
            runCatching {
                importDocumentUseCase.importDocument(
                    request = request,
                    currentTimeEpochMs = currentTimeEpochMs,
                )
            }.fold(
                onSuccess = { document ->
                    mutableState.update {
                        it.copy(
                            lastImportedDocumentId = document.id,
                            errorMessage = null,
                            isImporting = false,
                        )
                    }
                },
                onFailure = { exception ->
                    mutableState.update {
                        it.copy(
                            errorMessage = exception.message,
                            isImporting = false,
                        )
                    }
                },
            )
        }
    }

    fun reportUnsupportedSelection() {
        mutableState.update {
            it.copy(
            errorMessage = "Unsupported file. Please select a PDF or EPUB document.",
            )
        }
    }

    fun consumeLastImportedDocument() {
        mutableState.update {
            it.copy(lastImportedDocumentId = null)
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
