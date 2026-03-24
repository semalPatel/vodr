package com.vodr.library.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.vodr.data.db.VodrDatabase
import com.vodr.data.db.dao.UserSettingsDao
import com.vodr.data.db.entity.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class GenerationRequestPayload(
    val voice: String,
    val speechRate: Float,
    val style: String,
)

class SettingsRepository(
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    modifier: Modifier = Modifier,
    repository: SettingsRepository = rememberSettingsRepository(),
) {
    var voice by rememberSaveable { mutableStateOf(UserSettingsEntity.DEFAULT_VOICE) }
    var speechRate by rememberSaveable { mutableStateOf(UserSettingsEntity.DEFAULT_SPEECH_RATE) }
    var style by rememberSaveable { mutableStateOf(UserSettingsEntity.DEFAULT_STYLE) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        val settings = repository.load()
        voice = settings.voice
        speechRate = settings.speechRate
        style = settings.style
        isLoaded = true
    }

    LaunchedEffect(voice, speechRate, style, isLoaded) {
        if (!isLoaded) {
            return@LaunchedEffect
        }
        delay(300)
        repository.save(
            UserSettingsEntity(
                voice = voice,
                speechRate = speechRate,
                style = style,
            ),
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = "Settings") })
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = voice,
                    onValueChange = {
                        voice = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    text = "Speech rate: ${"%.2f".format(speechRate)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                    },
                    valueRange = 0.5f..2.0f,
                )
                Text(
                    text = "Style",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = style,
                    onValueChange = {
                        style = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun rememberSettingsRepository(): SettingsRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val database = Room.databaseBuilder(
            context,
            VodrDatabase::class.java,
            SETTINGS_DATABASE_NAME,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        SettingsRepository(database.userSettingsDao())
    }
}

private const val SETTINGS_DATABASE_NAME = "vodr-settings.db"
