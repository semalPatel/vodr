package com.vodr.ai

import android.os.Build

enum class DeviceTier {
    UNKNOWN,
    LOW_END,
    MID_RANGE,
    HIGH_END,
    FLAGSHIP,
}

data class DeviceCapabilities(
    val isFlagship: Boolean,
    val supportsAICore: Boolean,
    val supportsMediaPipe: Boolean,
    val tier: DeviceTier = DeviceTier.UNKNOWN,
)

interface DeviceCapabilityDetector {
    fun detect(): DeviceCapabilities
}

data class DeviceCapabilityPolicy(
    val flagshipScoreThreshold: Int = 4,
    val highEndScoreThreshold: Int = 2,
    val aiCoreScoreThreshold: Int = 2,
    val aiCoreMinSdk: Int = 34,
    val modernDeviceSdk: Int = 33,
    val flagshipRamMb: Long = 8_192,
    val highEndRamMb: Long = 6_144,
)

data class DeviceBuildProfile(
    val model: String,
    val brand: String,
    val device: String,
    val fingerprint: String,
    val sdkInt: Int,
    val totalRamMb: Long? = null,
)

interface BuildProfileProvider {
    fun current(): DeviceBuildProfile
}

class AndroidBuildProfileProvider : BuildProfileProvider {
    override fun current(): DeviceBuildProfile {
        return DeviceBuildProfile(
            model = Build.MODEL.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            totalRamMb = null,
        )
    }
}

class DefaultDeviceCapabilityDetector(
    private val buildProfileProvider: BuildProfileProvider = AndroidBuildProfileProvider(),
    private val policy: DeviceCapabilityPolicy = DeviceCapabilityPolicy(),
) : DeviceCapabilityDetector {
    override fun detect(): DeviceCapabilities {
        val profile = buildProfileProvider.current()
        val model = profile.model.lowercase()
        val brand = profile.brand.lowercase()
        val device = profile.device.lowercase()
        val fingerprint = profile.fingerprint.lowercase()

        val isEmulator = isEmulator(model = model, device = device, fingerprint = fingerprint)
        val isAndroidGo = device.contains("go") || model.contains("go edition")
        val isKnownFlagship = isKnownFlagship(model = model, brand = brand)

        val score = capabilityScore(
            profile = profile,
            isKnownFlagship = isKnownFlagship,
            isAndroidGo = isAndroidGo,
            isEmulator = isEmulator,
        )
        val tier = when {
            score >= policy.flagshipScoreThreshold -> DeviceTier.FLAGSHIP
            score >= policy.highEndScoreThreshold -> DeviceTier.HIGH_END
            score >= 1 -> DeviceTier.MID_RANGE
            score <= -1 -> DeviceTier.LOW_END
            else -> DeviceTier.UNKNOWN
        }

        val isFlagship = tier == DeviceTier.FLAGSHIP
        val supportsMediaPipe = !isEmulator
        val supportsAICore = !isEmulator &&
            !isAndroidGo &&
            profile.sdkInt >= policy.aiCoreMinSdk &&
            (isFlagship || score >= policy.aiCoreScoreThreshold)

        return DeviceCapabilities(
            isFlagship = isFlagship,
            supportsAICore = supportsAICore,
            supportsMediaPipe = supportsMediaPipe,
            tier = tier,
        )
    }

    private fun capabilityScore(
        profile: DeviceBuildProfile,
        isKnownFlagship: Boolean,
        isAndroidGo: Boolean,
        isEmulator: Boolean,
    ): Int {
        var score = 0
        if (isKnownFlagship) {
            score += 3
        }
        val ramMb = profile.totalRamMb
        if (ramMb != null) {
            score += when {
                ramMb >= policy.flagshipRamMb -> 2
                ramMb >= policy.highEndRamMb -> 1
                else -> 0
            }
        }
        if (profile.sdkInt >= policy.modernDeviceSdk) {
            score += 1
        }
        if (isAndroidGo) {
            score -= 2
        }
        if (isEmulator) {
            score -= 4
        }
        return score
    }

    private fun isKnownFlagship(model: String, brand: String): Boolean {
        return when {
            model.contains("pixel 8") -> true
            model.contains("pixel 9") -> true
            model.contains("galaxy s") -> true
            brand.contains("google") && model.contains("pixel") -> true
            else -> false
        }
    }

    private fun isEmulator(model: String, device: String, fingerprint: String): Boolean {
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("emulator") ||
            device.contains("emulator")
    }
}
