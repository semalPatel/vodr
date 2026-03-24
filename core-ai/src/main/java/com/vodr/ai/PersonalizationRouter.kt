package com.vodr.ai

import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.CustomEndpointPersonalizer
import com.vodr.ai.provider.CustomLocalModelPersonalizer
import com.vodr.ai.provider.HeuristicPersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer

class PersonalizationRouter(
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
    private val aICorePersonalizer: AICorePersonalizer,
    private val mediaPipePersonalizer: MediaPipePersonalizer,
    private val customLocalModelPersonalizer: CustomLocalModelPersonalizer = CustomLocalModelPersonalizer(),
    private val customEndpointPersonalizer: CustomEndpointPersonalizer = CustomEndpointPersonalizer(),
    private val heuristicPersonalizer: HeuristicPersonalizer = HeuristicPersonalizer(),
    private val probeRegistry: PersonalizationProbeRegistry = PersonalizationProbeRegistry(
        probes = listOf(
            AICoreRuntimeProbe(deviceCapabilityDetector),
            MediaPipeRuntimeProbe(deviceCapabilityDetector),
            CustomLocalModelRuntimeProbe(),
            CustomEndpointRuntimeProbe(),
            OfflineHeuristicRuntimeProbe(),
        )
    ),
) {
    fun select(
        preferences: PersonalizationPreferences = PersonalizationPreferences(),
    ): Personalizer {
        val candidates = personalizationCandidateProviders(preferences)
        return candidates.firstNotNullOfOrNull { providerType ->
            val result = probeRegistry.probe(
                providerType = providerType,
                preferences = preferences,
            )
            if (result.availability == ProbeAvailability.AVAILABLE) {
                personalizerFor(providerType)
            } else {
                null
            }
        } ?: heuristicPersonalizer
    }

    private fun personalizerFor(
        providerType: PersonalizationProviderType,
    ): Personalizer {
        return when (providerType) {
            PersonalizationProviderType.AI_CORE -> aICorePersonalizer
            PersonalizationProviderType.MEDIA_PIPE -> mediaPipePersonalizer
            PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> customLocalModelPersonalizer
            PersonalizationProviderType.CUSTOM_ENDPOINT -> customEndpointPersonalizer
            PersonalizationProviderType.OFFLINE_HEURISTIC,
            PersonalizationProviderType.AUTO,
            -> heuristicPersonalizer
        }
    }
}

interface Personalizer {
    fun buildPrompt(
        inputText: String,
        tone: String,
        style: String,
        customProviderConfig: CustomProviderConfig = CustomProviderConfig(),
    ): String
}
