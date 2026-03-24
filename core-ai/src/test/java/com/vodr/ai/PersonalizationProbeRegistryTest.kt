package com.vodr.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PersonalizationProbeRegistryTest {

    @Test
    fun probeResultsAreCachedWithinTtl() {
        val probe = CountingProbe()
        var now = 1_000L
        val registry = PersonalizationProbeRegistry(
            probes = listOf(probe),
            cacheTtlMs = 10_000L,
            timeProvider = { now },
        )

        val preferences = PersonalizationPreferences(
            providerType = PersonalizationProviderType.OFFLINE_HEURISTIC,
        )
        val first = registry.probe(
            providerType = PersonalizationProviderType.OFFLINE_HEURISTIC,
            preferences = preferences,
        )
        now += 500L
        val second = registry.probe(
            providerType = PersonalizationProviderType.OFFLINE_HEURISTIC,
            preferences = preferences,
        )

        assertEquals(1, probe.invocationCount)
        assertSame(first, second)
    }

    private class CountingProbe : PersonalizationRuntimeProbe {
        override val providerType: PersonalizationProviderType =
            PersonalizationProviderType.OFFLINE_HEURISTIC

        var invocationCount: Int = 0

        override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
            invocationCount += 1
            return PersonalizationProbeResult(
                providerType = providerType,
                availability = ProbeAvailability.AVAILABLE,
                detail = "always-available",
            )
        }
    }
}
