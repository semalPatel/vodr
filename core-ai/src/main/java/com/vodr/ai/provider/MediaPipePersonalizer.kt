package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.Personalizer
import com.vodr.ai.buildPersonalizationPrompt

class MediaPipePersonalizer : Personalizer {
    override fun buildPrompt(
        inputText: String,
        tone: String,
        style: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return buildPersonalizationPrompt(
            providerLabel = "mediapipe",
            inputText = inputText,
            tone = tone,
            style = style,
        )
    }
}
