package com.vodr.generate

sealed class GenerationError {
    data object DocumentOpenFailure : GenerationError()
    data object EmptyResult : GenerationError()
    data class ProcessingFailure(val reason: String?) : GenerationError()
}

fun GenerationError.toUserMessage(): String {
    return when (this) {
        GenerationError.DocumentOpenFailure -> "Unable to open the selected document."
        GenerationError.EmptyResult -> "No playable chapters were produced."
        is GenerationError.ProcessingFailure -> reason ?: "Generation failed."
    }
}
