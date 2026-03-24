package com.vodr.ai

import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.CustomEndpointPersonalizer
import com.vodr.ai.provider.CustomLocalModelPersonalizer
import com.vodr.ai.provider.HeuristicPersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer
import java.io.File
import org.junit.Assert.assertSame
import org.junit.Test

class PersonalizationRouterTest {

    @Test
    fun supportedFlagshipRoutesToAICoreProvider() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = true,
                supportsAICore = true,
                supportsMediaPipe = true,
            )
        )
        val aicore = AICorePersonalizer()
        val mediapipe = MediaPipePersonalizer()
        val router = PersonalizationRouter(detector, aicore, mediapipe)

        assertSame(aicore, router.select())
    }

    @Test
    fun unsupportedRoutesToMediaPipeProvider() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = false,
                supportsAICore = false,
                supportsMediaPipe = true,
            )
        )
        val aicore = AICorePersonalizer()
        val mediapipe = MediaPipePersonalizer()
        val router = PersonalizationRouter(detector, aicore, mediapipe)

        assertSame(mediapipe, router.select())
    }

    @Test
    fun fallsBackWhenPreferredPathUnavailable() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = true,
                supportsAICore = false,
                supportsMediaPipe = true,
            )
        )
        val aicore = AICorePersonalizer()
        val mediapipe = MediaPipePersonalizer()
        val router = PersonalizationRouter(detector, aicore, mediapipe)

        assertSame(mediapipe, router.select())
    }

    @Test
    fun fallsBackToOfflineHeuristicWhenNoRuntimeSupported() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = false,
                supportsAICore = false,
                supportsMediaPipe = false,
            )
        )
        val aicore = AICorePersonalizer()
        val mediapipe = MediaPipePersonalizer()
        val heuristic = HeuristicPersonalizer()
        val router = PersonalizationRouter(
            deviceCapabilityDetector = detector,
            aICorePersonalizer = aicore,
            mediaPipePersonalizer = mediapipe,
            heuristicPersonalizer = heuristic,
        )

        assertSame(heuristic, router.select())
    }

    @Test
    fun customLocalModelSelectionUsesConfiguredReadableModel() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = false,
                supportsAICore = false,
                supportsMediaPipe = false,
            )
        )
        val localModelFile = File.createTempFile("vodr-personalizer", ".gguf")
        localModelFile.writeText("stub")
        val localPersonalizer = CustomLocalModelPersonalizer()
        val router = PersonalizationRouter(
            deviceCapabilityDetector = detector,
            aICorePersonalizer = AICorePersonalizer(),
            mediaPipePersonalizer = MediaPipePersonalizer(),
            customLocalModelPersonalizer = localPersonalizer,
            customEndpointPersonalizer = CustomEndpointPersonalizer(),
            heuristicPersonalizer = HeuristicPersonalizer(),
        )

        val selected = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.CUSTOM_LOCAL_MODEL,
                customProviderConfig = CustomProviderConfig(
                    localModelPath = localModelFile.absolutePath,
                ),
                offlineOnly = true,
            )
        )

        assertSame(localPersonalizer, selected)
    }

    @Test
    fun autoModePrefersDeviceProviderBeforeConfiguredLocalModel() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = true,
                supportsAICore = true,
                supportsMediaPipe = true,
            )
        )
        val localModelFile = File.createTempFile("vodr-auto-priority", ".gguf")
        localModelFile.writeText("stub")
        val aicore = AICorePersonalizer()
        val localPersonalizer = CustomLocalModelPersonalizer()
        val router = PersonalizationRouter(
            deviceCapabilityDetector = detector,
            aICorePersonalizer = aicore,
            mediaPipePersonalizer = MediaPipePersonalizer(),
            customLocalModelPersonalizer = localPersonalizer,
            customEndpointPersonalizer = CustomEndpointPersonalizer(),
            heuristicPersonalizer = HeuristicPersonalizer(),
        )

        val selected = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.AUTO,
                customProviderConfig = CustomProviderConfig(
                    localModelPath = localModelFile.absolutePath,
                ),
                offlineOnly = true,
            )
        )

        assertSame(aicore, selected)
    }

    @Test
    fun explicitLocalModelSelectionDoesNotFallBackToDeviceProvider() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = true,
                supportsAICore = true,
                supportsMediaPipe = true,
            )
        )
        val heuristic = HeuristicPersonalizer()
        val router = PersonalizationRouter(
            deviceCapabilityDetector = detector,
            aICorePersonalizer = AICorePersonalizer(),
            mediaPipePersonalizer = MediaPipePersonalizer(),
            customLocalModelPersonalizer = CustomLocalModelPersonalizer(),
            customEndpointPersonalizer = CustomEndpointPersonalizer(),
            heuristicPersonalizer = heuristic,
        )

        val selected = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.CUSTOM_LOCAL_MODEL,
                customProviderConfig = CustomProviderConfig(
                    localModelPath = "/path/that/does/not/exist.gguf",
                ),
                offlineOnly = true,
            )
        )

        assertSame(heuristic, selected)
    }

    @Test
    fun offlineOnlyBlocksCustomEndpointAndFallsBackOffline() {
        val detector = FakeDeviceCapabilityDetector(
            DeviceCapabilities(
                isFlagship = false,
                supportsAICore = false,
                supportsMediaPipe = false,
            )
        )
        val endpointPersonalizer = CustomEndpointPersonalizer()
        val heuristic = HeuristicPersonalizer()
        val router = PersonalizationRouter(
            deviceCapabilityDetector = detector,
            aICorePersonalizer = AICorePersonalizer(),
            mediaPipePersonalizer = MediaPipePersonalizer(),
            customEndpointPersonalizer = endpointPersonalizer,
            heuristicPersonalizer = heuristic,
        )

        val selected = router.select(
            preferences = PersonalizationPreferences(
                providerType = PersonalizationProviderType.CUSTOM_ENDPOINT,
                customProviderConfig = CustomProviderConfig(
                    localEndpoint = "https://example.com/v1",
                ),
                offlineOnly = true,
            )
        )

        assertSame(heuristic, selected)
    }

    private class FakeDeviceCapabilityDetector(
        private val capabilities: DeviceCapabilities,
    ) : DeviceCapabilityDetector {
        override fun detect(): DeviceCapabilities = capabilities
    }
}
