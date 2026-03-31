package com.vodr.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vodr.ai.PersonalizationPreferences
import com.vodr.playback.PlaybackChapter
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GenerationUiState(
    val isGenerating: Boolean = false,
    val queue: List<PlaybackChapter> = emptyList(),
    val error: GenerationError? = null,
    val runtimeSummary: GenerationRuntimeSummary? = null,
    val phase: GenerationPhase = GenerationPhase.IDLE,
    val activeDocument: GeneratedDocumentSummary? = null,
)

data class GeneratedDocumentSummary(
    val displayName: String,
    val sourceUri: String,
    val mimeType: String,
)

class GenerationViewModel(
    private val orchestrator: GenerationOrchestrator = GenerationOrchestrator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GenerationUiState())
    val state: StateFlow<GenerationUiState> = mutableState.asStateFlow()

    fun generate(
        document: GenerationDocumentInput,
        mode: GenerationMode,
        personalizationPreferences: PersonalizationPreferences = PersonalizationPreferences(),
        openInputStream: suspend (GenerationDocumentInput) -> InputStream?,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isGenerating = true,
                    queue = emptyList(),
                    error = null,
                    runtimeSummary = null,
                    phase = GenerationPhase.PREPARING_INPUT,
                    activeDocument = null,
                )
            }
            val result = runCatching {
                withContext(ioDispatcher) {
                    val inputStream = openInputStream(document)
                        ?: throw DocumentStreamUnavailableException()
                    inputStream.use {
                        orchestrator.buildPlaybackQueue(
                            document = document,
                            mode = mode,
                            personalizationPreferences = personalizationPreferences,
                            inputStream = it,
                            onProgress = { phase ->
                                mutableState.update { current ->
                                    current.copy(phase = phase)
                                }
                            },
                        )
                    }
                }
            }
            mutableState.value = result.fold(
                onSuccess = { output ->
                    if (output.queue.isEmpty()) {
                        GenerationUiState(
                            isGenerating = false,
                            queue = emptyList(),
                            error = GenerationError.EmptyResult,
                            runtimeSummary = output.runtimeSummary,
                            phase = GenerationPhase.READY,
                            activeDocument = GeneratedDocumentSummary(
                                displayName = document.displayName,
                                sourceUri = document.sourceUri,
                                mimeType = document.mimeType,
                            ),
                        )
                    } else {
                        GenerationUiState(
                            isGenerating = false,
                            queue = output.queue,
                            error = null,
                            runtimeSummary = output.runtimeSummary,
                            phase = GenerationPhase.READY,
                            activeDocument = GeneratedDocumentSummary(
                                displayName = document.displayName,
                                sourceUri = document.sourceUri,
                                mimeType = document.mimeType,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    GenerationUiState(
                        isGenerating = false,
                        queue = emptyList(),
                        error = if (error is DocumentStreamUnavailableException || error is SecurityException) {
                            GenerationError.DocumentOpenFailure
                        } else {
                            GenerationError.ProcessingFailure(error.message)
                        },
                        runtimeSummary = null,
                        phase = GenerationPhase.IDLE,
                        activeDocument = null,
                    )
                },
            )
        }
    }

    fun clearQueue() {
        mutableState.update {
            it.copy(
                isGenerating = false,
                queue = emptyList(),
                error = null,
                runtimeSummary = null,
                phase = GenerationPhase.IDLE,
                activeDocument = null,
            )
        }
    }
}

private class DocumentStreamUnavailableException : IllegalStateException("Document stream unavailable")
