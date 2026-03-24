package com.vodr.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionRouterTest {

    @Test
    fun offlineFallbackProducesStableShortLabel() {
        val router = TranscriptionRouter(
            probeRegistry = PersonalizationProbeRegistry(
                probes = listOf(OfflineHeuristicRuntimeProbe()),
            ),
        )

        val result = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.OFFLINE_HEURISTIC,
            )
        ).transcribe(
            sourceText = "A long opening paragraph about the hero entering a forgotten city.",
        )

        assertEquals("A long opening paragraph about the", result)
    }

    @Test
    fun autoModePrefersDeviceTranscriptionBeforeConfiguredLocalModel() {
        val localModelFile = File.createTempFile("vodr-transcription", ".json")
        localModelFile.writeText("""{"template":"{{inputText}}"}""")
        val router = TranscriptionRouter(
            probeRegistry = PersonalizationProbeRegistry(
                probes = listOf(
                    object : PersonalizationRuntimeProbe {
                        override val providerType: PersonalizationProviderType =
                            PersonalizationProviderType.AI_CORE

                        override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
                            return PersonalizationProbeResult(
                                providerType = providerType,
                                availability = ProbeAvailability.AVAILABLE,
                            )
                        }
                    },
                    CustomLocalModelRuntimeProbe(),
                    OfflineHeuristicRuntimeProbe(),
                ),
            ),
        )

        val engine = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.AUTO,
                customProviderConfig = CustomProviderConfig(
                    localModelPath = localModelFile.absolutePath,
                ),
            )
        )

        assertTrue(engine is AICoreTranscriptionEngine)
    }

    @Test
    fun explicitCustomLocalTranscriptionDoesNotFallBackToDeviceEngine() {
        val router = TranscriptionRouter(
            probeRegistry = PersonalizationProbeRegistry(
                probes = listOf(
                    object : PersonalizationRuntimeProbe {
                        override val providerType: PersonalizationProviderType =
                            PersonalizationProviderType.AI_CORE

                        override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
                            return PersonalizationProbeResult(
                                providerType = providerType,
                                availability = ProbeAvailability.AVAILABLE,
                            )
                        }
                    },
                    CustomLocalModelRuntimeProbe(),
                    OfflineHeuristicRuntimeProbe(),
                ),
            ),
        )

        val engine = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.CUSTOM_LOCAL_MODEL,
                customProviderConfig = CustomProviderConfig(
                    localModelPath = "/missing/model.gguf",
                ),
            )
        )

        assertTrue(engine is HeuristicTranscriptionEngine)
    }
}
