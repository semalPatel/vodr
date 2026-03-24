package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

/**
 * Deterministic offline fallback that does not require any local ML runtime.
 */
class HeuristicPersonalizer : Personalizer {
    override fun buildPrompt(
        inputText: String,
        tone: String,
        style: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return buildPersonalizationPrompt(
            providerLabel = "offline",
            inputText = inputText,
            tone = tone,
            style = style,
        )
    }
}
