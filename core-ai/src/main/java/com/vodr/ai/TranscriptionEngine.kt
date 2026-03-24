package com.vodr.ai

import com.vodr.ai.provider.EndpointAiClient
import com.vodr.ai.provider.EndpointPromptRequest
import com.vodr.ai.provider.LocalTemplateAiClient

interface TranscriptionEngine {
    fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig = CustomProviderConfig(),
    ): String
}

class AICoreTranscriptionEngine : TranscriptionEngine {
    override fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return toOfflineTranscriptLabel(sourceText)
    }
}

class MediaPipeTranscriptionEngine : TranscriptionEngine {
    override fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return toOfflineTranscriptLabel(sourceText)
    }
}

class CustomLocalModelTranscriptionEngine(
    private val localTemplateAiClient: LocalTemplateAiClient = LocalTemplateAiClient(),
) : TranscriptionEngine {
    override fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return localTemplateAiClient.render(
            config = customProviderConfig,
            defaultLabel = "custom-local-transcript",
            tone = "neutral",
            style = "transcript",
            inputText = sourceText,
            taskInstruction = "Return a concise transcript label for the supplied chapter.",
        ).lineSequence().firstOrNull { it.isNotBlank() }
            ?.take(48)
            ?.trim()
            .orEmpty()
            .ifBlank { toOfflineTranscriptLabel(sourceText) }
    }
}

class CustomEndpointTranscriptionEngine(
    private val endpointAiClient: EndpointAiClient = EndpointAiClient(),
) : TranscriptionEngine {
    override fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return runCatching {
            endpointAiClient.generate(
                config = customProviderConfig,
                request = EndpointPromptRequest(
                    prompt = sourceText.trim(),
                    modelName = customProviderConfig.modelName,
                    taskInstruction = "Return a concise 3 to 6 word chapter label. Output only the label.",
                ),
            )
        }.getOrElse {
            toOfflineTranscriptLabel(sourceText)
        }.lineSequence().firstOrNull { it.isNotBlank() }
            ?.take(48)
            ?.trim()
            .orEmpty()
            .ifBlank { toOfflineTranscriptLabel(sourceText) }
    }
}

class HeuristicTranscriptionEngine : TranscriptionEngine {
    override fun transcribe(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return toOfflineTranscriptLabel(sourceText)
    }
}

internal fun toOfflineTranscriptLabel(sourceText: String): String {
    val normalized = sourceText
        .lineSequence()
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) {
        return "Untitled Section"
    }

    val sentence = normalized
        .split('.', '!', '?')
        .firstOrNull()
        .orEmpty()
        .trim()
        .ifBlank { normalized }
    val words = sentence.split(' ')
        .filter { it.isNotBlank() }
        .take(6)
        .joinToString(" ")
    return words
        .take(36)
        .ifBlank { "Untitled Section" }
}
