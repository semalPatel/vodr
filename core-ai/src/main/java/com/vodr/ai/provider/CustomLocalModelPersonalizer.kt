package com.vodr.ai.provider

import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

/**
 * Represents a user-supplied local model path while remaining fully offline-safe.
 */
class CustomLocalModelPersonalizer : Personalizer {
    override fun buildPrompt(inputText: String, tone: String, style: String): String {
        return buildPersonalizationPrompt(
            providerLabel = "custom-local",
            inputText = inputText,
            tone = tone,
            style = style,
        )
    }
}
