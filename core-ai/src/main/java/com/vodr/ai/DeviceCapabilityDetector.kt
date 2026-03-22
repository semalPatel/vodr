package com.vodr.ai

import android.os.Build

data class DeviceCapabilities(
    val isFlagship: Boolean,
    val supportsAICore: Boolean,
    val supportsMediaPipe: Boolean,
)

interface DeviceCapabilityDetector {
    fun detect(): DeviceCapabilities
}

class DefaultDeviceCapabilityDetector : DeviceCapabilityDetector {
    override fun detect(): DeviceCapabilities {
        val model = Build.MODEL.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()

        val isFlagship = when {
            model.contains("pixel 8") -> true
            model.contains("pixel 9") -> true
            model.contains("galaxy s") -> true
            brand.contains("google") && model.contains("pixel") -> true
            fingerprint.contains("generic") -> false
            else -> false
        }

        return DeviceCapabilities(
            isFlagship = isFlagship,
            supportsAICore = isFlagship && !device.contains("go"),
            supportsMediaPipe = true,
        )
    }
}
