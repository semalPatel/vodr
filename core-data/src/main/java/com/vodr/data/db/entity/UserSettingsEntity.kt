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
) {
    companion object {
        const val DEFAULT_SETTINGS_ID: Long = 1L
        const val DEFAULT_VOICE: String = "default"
        const val DEFAULT_SPEECH_RATE: Float = 1.0f
        const val DEFAULT_STYLE: String = "balanced"
    }
}
