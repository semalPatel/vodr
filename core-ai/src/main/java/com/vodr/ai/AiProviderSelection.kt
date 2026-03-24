package com.vodr.ai

internal fun personalizationCandidateProviders(
    preferences: PersonalizationPreferences,
): List<PersonalizationProviderType> {
    return when (preferences.providerType) {
        PersonalizationProviderType.AUTO -> buildList {
            add(PersonalizationProviderType.AI_CORE)
            add(PersonalizationProviderType.MEDIA_PIPE)
            if (preferences.customProviderConfig.localModelPath.isNotBlank()) {
                add(PersonalizationProviderType.CUSTOM_LOCAL_MODEL)
            }
            if (!preferences.offlineOnly &&
                preferences.customProviderConfig.localEndpoint.isNotBlank()
            ) {
                add(PersonalizationProviderType.CUSTOM_ENDPOINT)
            }
            add(PersonalizationProviderType.OFFLINE_HEURISTIC)
        }
        PersonalizationProviderType.AI_CORE -> listOf(
            PersonalizationProviderType.AI_CORE,
            PersonalizationProviderType.MEDIA_PIPE,
            PersonalizationProviderType.OFFLINE_HEURISTIC,
        )
        PersonalizationProviderType.MEDIA_PIPE -> listOf(
            PersonalizationProviderType.MEDIA_PIPE,
            PersonalizationProviderType.AI_CORE,
            PersonalizationProviderType.OFFLINE_HEURISTIC,
        )
        PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> listOf(
            PersonalizationProviderType.CUSTOM_LOCAL_MODEL,
            PersonalizationProviderType.OFFLINE_HEURISTIC,
        )
        PersonalizationProviderType.CUSTOM_ENDPOINT -> buildList {
            add(PersonalizationProviderType.CUSTOM_ENDPOINT)
            if (preferences.customProviderConfig.localModelPath.isNotBlank()) {
                add(PersonalizationProviderType.CUSTOM_LOCAL_MODEL)
            }
            add(PersonalizationProviderType.OFFLINE_HEURISTIC)
        }
        PersonalizationProviderType.OFFLINE_HEURISTIC -> listOf(
            PersonalizationProviderType.OFFLINE_HEURISTIC,
        )
    }
}
