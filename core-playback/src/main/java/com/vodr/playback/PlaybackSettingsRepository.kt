package com.vodr.playback

import com.vodr.data.db.dao.UserSettingsDao
import com.vodr.data.db.entity.UserSettingsEntity
import com.vodr.tts.NarrationProviderType
import com.vodr.tts.NarrationRenderSettings
import com.vodr.tts.VoicePackStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PlaybackSettingsRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
    private val voicePackStore: VoicePackStore,
) {
    suspend fun loadNarrationSettings(): NarrationRenderSettings {
        return withContext(Dispatchers.IO) {
            val settings = userSettingsDao.getById() ?: UserSettingsEntity()
            val voicePacks = voicePackStore.listInstalled()
            NarrationRenderSettings(
                providerType = settings.narrationProviderType.toNarrationProviderType(),
                voiceName = settings.voice.takeIf { it.isNotBlank() && it != UserSettingsEntity.DEFAULT_VOICE },
                speechRate = settings.speechRate.coerceIn(0.5f, 2.0f),
                pitch = settings.style.toPitchTarget(),
                cloudEndpoint = settings.cloudNarrationEndpoint,
                cloudModelName = settings.cloudNarrationModelName,
                offlineOnly = settings.offlineOnly,
                selectedVoicePackId = settings.selectedVoicePackId,
                installedVoicePacks = voicePacks,
            )
        }
    }
}

private fun String.toNarrationProviderType(): NarrationProviderType {
    return runCatching { NarrationProviderType.valueOf(this) }
        .getOrDefault(NarrationProviderType.AUTO)
}

private fun String.toPitchTarget(): Float {
    return when (trim().lowercase()) {
        "calm", "warm" -> 0.96f
        "expressive", "dramatic" -> 1.06f
        else -> 1.0f
    }
}
