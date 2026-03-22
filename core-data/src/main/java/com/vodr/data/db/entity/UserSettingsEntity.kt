package com.vodr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val settingsId: Long = DEFAULT_SETTINGS_ID,
    val preferredVoice: String,
    val speechRate: Float,
    val themeMode: String,
    val autoPlay: Boolean,
    val lastOpenedDocumentId: Long? = null,
) {
    companion object {
        const val DEFAULT_SETTINGS_ID: Long = 1L
    }
}
