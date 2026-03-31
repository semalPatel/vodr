package com.vodr.tts

enum class NarrationProviderType {
    AUTO,
    SYSTEM_TTS,
    OFFLINE_VOICE_PACK,
    CLOUD_ENDPOINT,
}

data class NarratorVoicePack(
    val id: String,
    val displayName: String,
    val description: String,
    val languageTag: String = "en-US",
    val systemVoiceName: String? = null,
    val speechRateMultiplier: Float = 1.0f,
    val pitchMultiplier: Float = 1.0f,
    val pauseMultiplier: Float = 1.0f,
    val expressiveness: Float = 0.5f,
    val providerLabel: String = "Offline Voice Pack",
    val version: Int = 1,
    val installedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val stableCacheKey: String
        get() = "$id-v$version"
}

enum class NarrationStyle {
    NEUTRAL,
    DIALOGUE,
    QUESTION,
    EXCITED,
    SUSPENSE,
    REFLECTIVE,
}

data class NarrationSegment(
    val text: String,
    val style: NarrationStyle,
    val pauseAfterMs: Long,
)

data class NarrationPlan(
    val dominantStyle: NarrationStyle,
    val segments: List<NarrationSegment>,
) {
    companion object {
        val Empty: NarrationPlan = NarrationPlan(
            dominantStyle = NarrationStyle.NEUTRAL,
            segments = emptyList(),
        )
    }
}

data class NarrationRenderSettings(
    val providerType: NarrationProviderType = NarrationProviderType.AUTO,
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val cloudEndpoint: String = "",
    val cloudModelName: String = "",
    val offlineOnly: Boolean = true,
    val selectedVoicePackId: String = "",
    val installedVoicePacks: List<NarratorVoicePack> = emptyList(),
) {
    val selectedVoicePack: NarratorVoicePack?
        get() = installedVoicePacks.firstOrNull { it.id == selectedVoicePackId }
}

data class NarrationResolution(
    val providerType: NarrationProviderType,
    val providerLabel: String,
    val detail: String,
    val voicePack: NarratorVoicePack? = null,
)
