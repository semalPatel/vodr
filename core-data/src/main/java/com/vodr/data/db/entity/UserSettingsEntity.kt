package com.vodr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val settingsId: Long = DEFAULT_SETTINGS_ID,
    val voice: String = DEFAULT_VOICE,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    val style: String = DEFAULT_STYLE,
    val personalizationProviderType: String = DEFAULT_PERSONALIZATION_PROVIDER_TYPE,
    val customLocalModelPath: String = DEFAULT_CUSTOM_LOCAL_MODEL_PATH,
    val customEndpoint: String = DEFAULT_CUSTOM_ENDPOINT,
    val customModelName: String = DEFAULT_CUSTOM_MODEL_NAME,
    val offlineOnly: Boolean = DEFAULT_OFFLINE_ONLY,
) {
    companion object {
        const val DEFAULT_SETTINGS_ID: Long = 1L
        const val DEFAULT_VOICE: String = "default"
        const val DEFAULT_SPEECH_RATE: Float = 1.0f
        const val DEFAULT_STYLE: String = "balanced"
        const val DEFAULT_PERSONALIZATION_PROVIDER_TYPE: String = "AUTO"
        const val DEFAULT_CUSTOM_LOCAL_MODEL_PATH: String = ""
        const val DEFAULT_CUSTOM_ENDPOINT: String = ""
        const val DEFAULT_CUSTOM_MODEL_NAME: String = ""
        const val DEFAULT_OFFLINE_ONLY: Boolean = true
    }
}
