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
                )
            }
            val result = runCatching {
                withContext(ioDispatcher) {
                    val inputStream = openInputStream(document)
                        ?: throw IllegalStateException("Document stream unavailable")
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
                        )
                    } else {
                        GenerationUiState(
                            isGenerating = false,
                            queue = output.queue,
                            error = null,
                            runtimeSummary = output.runtimeSummary,
                            phase = GenerationPhase.READY,
                        )
                    }
                },
                onFailure = { error ->
                    GenerationUiState(
                        isGenerating = false,
                        queue = emptyList(),
                        error = if (error is IllegalStateException) {
                            GenerationError.DocumentOpenFailure
                        } else {
                            GenerationError.ProcessingFailure(error.message)
                        },
                        runtimeSummary = null,
                        phase = GenerationPhase.IDLE,
                    )
                },
            )
        }
    }

    fun clearQueue() {
        mutableState.update {
            it.copy(
                queue = emptyList(),
                phase = GenerationPhase.IDLE,
            )
        }
    }
}
