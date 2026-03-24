package com.vodr.ai.provider

import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

/**
 * Represents a user-supplied endpoint-backed provider without making routing depend on it.
 */
class CustomEndpointPersonalizer : Personalizer {
    override fun buildPrompt(inputText: String, tone: String, style: String): String {
        return buildPersonalizationPrompt(
            providerLabel = "custom-endpoint",
            inputText = inputText,
            tone = tone,
            style = style,
        )
    }
}
