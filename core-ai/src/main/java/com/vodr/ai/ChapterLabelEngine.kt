package com.vodr.ai

interface ChapterLabelEngine {
    fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig = CustomProviderConfig(),
    ): String
}

class AICoreChapterLabelEngine(
    private val delegate: AICoreTranscriptionEngine = AICoreTranscriptionEngine(),
) : ChapterLabelEngine {
    override fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String = delegate.transcribe(sourceText, customProviderConfig)
}

class MediaPipeChapterLabelEngine(
    private val delegate: MediaPipeTranscriptionEngine = MediaPipeTranscriptionEngine(),
) : ChapterLabelEngine {
    override fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String = delegate.transcribe(sourceText, customProviderConfig)
}

class CustomLocalModelChapterLabelEngine(
    private val delegate: CustomLocalModelTranscriptionEngine = CustomLocalModelTranscriptionEngine(),
) : ChapterLabelEngine {
    override fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String = delegate.transcribe(sourceText, customProviderConfig)
}

class CustomEndpointChapterLabelEngine(
    private val delegate: CustomEndpointTranscriptionEngine = CustomEndpointTranscriptionEngine(),
) : ChapterLabelEngine {
    override fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String = delegate.transcribe(sourceText, customProviderConfig)
}

class HeuristicChapterLabelEngine(
    private val delegate: HeuristicTranscriptionEngine = HeuristicTranscriptionEngine(),
) : ChapterLabelEngine {
    override fun label(
        sourceText: String,
        customProviderConfig: CustomProviderConfig,
    ): String = delegate.transcribe(sourceText, customProviderConfig)
}

class ChapterLabelRouter(
    private val aICoreChapterLabelEngine: AICoreChapterLabelEngine = AICoreChapterLabelEngine(),
    private val mediaPipeChapterLabelEngine: MediaPipeChapterLabelEngine = MediaPipeChapterLabelEngine(),
    private val customLocalModelChapterLabelEngine: CustomLocalModelChapterLabelEngine =
        CustomLocalModelChapterLabelEngine(),
    private val customEndpointChapterLabelEngine: CustomEndpointChapterLabelEngine =
        CustomEndpointChapterLabelEngine(),
    private val heuristicChapterLabelEngine: HeuristicChapterLabelEngine = HeuristicChapterLabelEngine(),
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
    ): ChapterLabelEngine {
        return when (resolve(preferences).providerType) {
            PersonalizationProviderType.AI_CORE -> aICoreChapterLabelEngine
            PersonalizationProviderType.MEDIA_PIPE -> mediaPipeChapterLabelEngine
            PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> customLocalModelChapterLabelEngine
            PersonalizationProviderType.CUSTOM_ENDPOINT -> customEndpointChapterLabelEngine
            PersonalizationProviderType.OFFLINE_HEURISTIC,
            PersonalizationProviderType.AUTO,
            -> heuristicChapterLabelEngine
        }
    }
}
