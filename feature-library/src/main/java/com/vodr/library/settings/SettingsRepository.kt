package com.vodr.library.settings

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.PersonalizationPreferences
import com.vodr.ai.PersonalizationProviderType
import com.vodr.data.db.dao.UserSettingsDao
import com.vodr.data.db.entity.UserSettingsEntity
import com.vodr.tts.NarratorVoicePack
import com.vodr.tts.VoicePackStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GenerationRequestPayload(
    val voice: String,
    val speechRate: Float,
    val style: String,
    val personalizationPreferences: PersonalizationPreferences,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
    private val voicePackStore: VoicePackStore,
) {
    suspend fun load(): UserSettingsEntity {
        return withContext(Dispatchers.IO) {
            userSettingsDao.getById() ?: UserSettingsEntity()
        }
    }

    suspend fun save(settings: UserSettingsEntity): UserSettingsEntity {
        return withContext(Dispatchers.IO) {
            userSettingsDao.upsert(settings)
            settings
        }
    }

    suspend fun loadInstalledVoicePacks(): List<NarratorVoicePack> {
        return withContext(Dispatchers.IO) {
            voicePackStore.listInstalled()
        }
    }

    suspend fun installStarterVoicePack(): NarratorVoicePack {
        return withContext(Dispatchers.IO) {
            voicePackStore.installStarterPack()
        }
    }

    suspend fun installVoicePackFromUrl(url: String): NarratorVoicePack {
        return withContext(Dispatchers.IO) {
            voicePackStore.installFromUrl(url)
        }
    }

    suspend fun removeVoicePack(voicePackId: String) {
        withContext(Dispatchers.IO) {
            voicePackStore.remove(voicePackId)
        }
    }
}

fun UserSettingsEntity.toGenerationRequestPayload(): GenerationRequestPayload {
    return GenerationRequestPayload(
        voice = voice,
        speechRate = speechRate,
        style = style,
        personalizationPreferences = PersonalizationPreferences(
            providerType = personalizationProviderType.toPersonalizationProviderType(),
            customProviderConfig = CustomProviderConfig(
                localModelPath = customLocalModelPath,
                localEndpoint = customEndpoint,
                modelName = customModelName,
            ),
            offlineOnly = offlineOnly,
        ),
    )
}

fun String.toPersonalizationProviderType(): PersonalizationProviderType {
    return runCatching { PersonalizationProviderType.valueOf(this) }
        .getOrDefault(PersonalizationProviderType.AUTO)
}
