package com.vodr.ai

import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer

class PersonalizationRouter(
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
    private val aICorePersonalizer: AICorePersonalizer,
    private val mediaPipePersonalizer: MediaPipePersonalizer,
) {

    fun select(): Personalizer {
        val capabilities = deviceCapabilityDetector.detect()

        return when {
            capabilities.supportsAICore && capabilities.isFlagship -> aICorePersonalizer
            capabilities.supportsMediaPipe -> mediaPipePersonalizer
            else -> aICorePersonalizer
        }
    }
}

interface Personalizer {
    fun buildPrompt(inputText: String, tone: String, style: String): String
}
