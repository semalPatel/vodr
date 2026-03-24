package com.vodr.ai

import org.junit.Assert.assertEquals
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
}
