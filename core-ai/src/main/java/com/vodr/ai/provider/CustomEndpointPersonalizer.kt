package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

/**
 * Talks to a user-supplied endpoint when allowed, but degrades locally on any failure.
 */
class CustomEndpointPersonalizer(
    private val endpointAiClient: EndpointAiClient = EndpointAiClient(),
) : Personalizer {
    override fun buildPrompt(
        inputText: String,
        tone: String,
        style: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return runCatching {
            endpointAiClient.generate(
                config = customProviderConfig,
                request = EndpointPromptRequest(
                    prompt = buildPersonalizationPrompt(
                        providerLabel = "custom-endpoint",
                        inputText = inputText,
                        tone = tone,
                        style = style,
                    ),
                    modelName = customProviderConfig.modelName,
                    taskInstruction = "Return a concise personalization prompt line for an audiobook generation pipeline.",
                ),
            )
        }.getOrElse {
            buildPersonalizationPrompt(
                providerLabel = "custom-endpoint-fallback",
                inputText = inputText,
                tone = tone,
                style = style,
            )
        }.trim()
    }
}
