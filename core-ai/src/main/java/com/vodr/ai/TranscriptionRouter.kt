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
    fun resolve(
        preferences: PersonalizationPreferences = PersonalizationPreferences(),
    ): ResolvedProviderSelection {
        return resolveProviderSelection(
            preferences = preferences,
            probeRegistry = probeRegistry,
        )
    }

    fun select(
        preferences: PersonalizationPreferences = PersonalizationPreferences(),
    ): TranscriptionEngine {
        return engineFor(
            providerType = resolve(preferences).providerType,
        )
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
