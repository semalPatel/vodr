package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.Personalizer

/**
 * Uses a user-supplied local template/config file to shape offline prompt generation.
 */
class CustomLocalModelPersonalizer(
    private val localTemplateAiClient: LocalTemplateAiClient = LocalTemplateAiClient(),
) : Personalizer {
    override fun buildPrompt(
        inputText: String,
        tone: String,
        style: String,
        customProviderConfig: CustomProviderConfig,
    ): String {
        return localTemplateAiClient.render(
            config = customProviderConfig,
            defaultLabel = "custom-local",
            tone = tone,
            style = style,
            inputText = inputText,
            taskInstruction = "Build a short personalization prompt for the current reading style.",
        )
    }
}
