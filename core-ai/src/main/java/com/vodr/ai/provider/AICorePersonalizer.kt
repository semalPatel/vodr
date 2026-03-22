package com.vodr.ai.provider

import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

class AICorePersonalizer : Personalizer {
    override fun buildPrompt(inputText: String, tone: String, style: String): String {
        return buildPersonalizationPrompt(
            providerLabel = "aicore",
            inputText = inputText,
            tone = tone,
            style = style,
        )
    }
}
