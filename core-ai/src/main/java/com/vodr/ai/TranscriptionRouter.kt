package com.vodr.ai

class TranscriptionRouter(
    private val aICoreTranscriptionEngine: AICoreTranscriptionEngine = AICoreTranscriptionEngine(),
    private val mediaPipeTranscriptionEngine: MediaPipeTranscriptionEngine = MediaPipeTranscriptionEngine(),
    private val customLocalModelTranscriptionEngine: CustomLocalModelTranscriptionEngine =
        CustomLocalModelTranscriptionEngine(),
    private val customEndpointTranscriptionEngine: CustomEndpointTranscriptionEngine =
        CustomEndpointTranscriptionEngine(),
    private val heuristicTranscriptionEngine: HeuristicTranscriptionEngine =
        HeuristicTranscriptionEngine(),
    private val probeRegistry: PersonalizationProbeRegistry,
) {
    fun select(
        preferences: PersonalizationPreferences = PersonalizationPreferences(),
    ): TranscriptionEngine {
        val candidates = personalizationCandidateProviders(preferences)
        return candidates.firstNotNullOfOrNull { providerType ->
            val result = probeRegistry.probe(
                providerType = providerType,
                preferences = preferences,
            )
            if (result.availability == ProbeAvailability.AVAILABLE) {
                engineFor(providerType)
            } else {
                null
            }
        } ?: heuristicTranscriptionEngine
    }

    private fun engineFor(
        providerType: PersonalizationProviderType,
    ): TranscriptionEngine {
        return when (providerType) {
            PersonalizationProviderType.AI_CORE -> aICoreTranscriptionEngine
            PersonalizationProviderType.MEDIA_PIPE -> mediaPipeTranscriptionEngine
            PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> customLocalModelTranscriptionEngine
            PersonalizationProviderType.CUSTOM_ENDPOINT -> customEndpointTranscriptionEngine
            PersonalizationProviderType.OFFLINE_HEURISTIC,
            PersonalizationProviderType.AUTO,
            -> heuristicTranscriptionEngine
        }
    }
}
