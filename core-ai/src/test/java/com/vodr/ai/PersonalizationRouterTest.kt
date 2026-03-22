package com.vodr.ai

import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer
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

    private class FakeDeviceCapabilityDetector(
        private val capabilities: DeviceCapabilities,
    ) : DeviceCapabilityDetector {
        override fun detect(): DeviceCapabilities = capabilities
    }
}
