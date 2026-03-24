package com.vodr.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityDetectorTest {

    @Test
    fun flagshipModelOnModernSdk_enablesAICore() {
        val detector = DefaultDeviceCapabilityDetector(
            buildProfileProvider = FakeBuildProfileProvider(
                DeviceBuildProfile(
                    model = "Pixel 9 Pro",
                    brand = "google",
                    device = "komodo",
                    fingerprint = "google/komodo/komodo:15/xyz:user/release-keys",
                    sdkInt = 35,
                    totalRamMb = 12_288,
                )
            )
        )

        val result = detector.detect()

        assertTrue(result.isFlagship)
        assertTrue(result.supportsAICore)
        assertTrue(result.supportsMediaPipe)
        assertEquals(DeviceTier.FLAGSHIP, result.tier)
    }

    @Test
    fun emulatorProfile_disablesLocalPaths() {
        val detector = DefaultDeviceCapabilityDetector(
            buildProfileProvider = FakeBuildProfileProvider(
                DeviceBuildProfile(
                    model = "sdk_gphone64_arm64",
                    brand = "google",
                    device = "emu64a",
                    fingerprint = "generic/sdk/generic:15/abc:userdebug/test-keys",
                    sdkInt = 35,
                    totalRamMb = 8_192,
                )
            )
        )

        val result = detector.detect()

        assertFalse(result.isFlagship)
        assertFalse(result.supportsAICore)
        assertFalse(result.supportsMediaPipe)
        assertEquals(DeviceTier.LOW_END, result.tier)
    }

    @Test
    fun androidGoHint_disablesAICoreEvenOnHighRam() {
        val detector = DefaultDeviceCapabilityDetector(
            buildProfileProvider = FakeBuildProfileProvider(
                DeviceBuildProfile(
                    model = "Budget Go Edition",
                    brand = "example",
                    device = "example_go",
                    fingerprint = "example/example/device:14/abc:user/release-keys",
                    sdkInt = 35,
                    totalRamMb = 8_192,
                )
            )
        )

        val result = detector.detect()

        assertFalse(result.supportsAICore)
        assertTrue(result.supportsMediaPipe)
    }

    private class FakeBuildProfileProvider(
        private val profile: DeviceBuildProfile,
    ) : BuildProfileProvider {
        override fun current(): DeviceBuildProfile = profile
    }
}
