package com.vodr.library.settings

import com.vodr.data.db.dao.UserSettingsDao
import com.vodr.data.db.entity.UserSettingsEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GenerationRequestPayload(
    val voice: String,
    val speechRate: Float,
    val style: String,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
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
}

fun UserSettingsEntity.toGenerationRequestPayload(): GenerationRequestPayload {
    return GenerationRequestPayload(
        voice = voice,
        speechRate = speechRate,
        style = style,
    )
}
