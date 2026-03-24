package com.vodr.library.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vodr.ai.PersonalizationProviderType
import com.vodr.data.db.entity.UserSettingsEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val voice: String = UserSettingsEntity.DEFAULT_VOICE,
    val speechRate: Float = UserSettingsEntity.DEFAULT_SPEECH_RATE,
    val style: String = UserSettingsEntity.DEFAULT_STYLE,
    val personalizationProviderType: PersonalizationProviderType =
        PersonalizationProviderType.valueOf(UserSettingsEntity.DEFAULT_PERSONALIZATION_PROVIDER_TYPE),
    val customLocalModelPath: String = UserSettingsEntity.DEFAULT_CUSTOM_LOCAL_MODEL_PATH,
    val customEndpoint: String = UserSettingsEntity.DEFAULT_CUSTOM_ENDPOINT,
    val customModelName: String = UserSettingsEntity.DEFAULT_CUSTOM_MODEL_NAME,
    val offlineOnly: Boolean = UserSettingsEntity.DEFAULT_OFFLINE_ONLY,
    val isLoaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = repository.load()
            mutableState.value = SettingsUiState(
                voice = settings.voice,
                speechRate = settings.speechRate,
                style = settings.style,
                personalizationProviderType = settings.personalizationProviderType
                    .toPersonalizationProviderType(),
                customLocalModelPath = settings.customLocalModelPath,
                customEndpoint = settings.customEndpoint,
                customModelName = settings.customModelName,
                offlineOnly = settings.offlineOnly,
                isLoaded = true,
            )
        }
    }

    fun updateVoice(voice: String) {
        mutableState.update { it.copy(voice = voice) }
        scheduleSave()
    }

    fun updateSpeechRate(speechRate: Float) {
        mutableState.update { it.copy(speechRate = speechRate) }
        scheduleSave()
    }

    fun updateStyle(style: String) {
        mutableState.update { it.copy(style = style) }
        scheduleSave()
    }

    fun updatePersonalizationProviderType(providerType: PersonalizationProviderType) {
        mutableState.update { it.copy(personalizationProviderType = providerType) }
        scheduleSave()
    }

    fun updateCustomLocalModelPath(path: String) {
        mutableState.update { it.copy(customLocalModelPath = path) }
        scheduleSave()
    }

    fun updateCustomEndpoint(endpoint: String) {
        mutableState.update { it.copy(customEndpoint = endpoint) }
        scheduleSave()
    }

    fun updateCustomModelName(modelName: String) {
        mutableState.update { it.copy(customModelName = modelName) }
        scheduleSave()
    }

    fun updateOfflineOnly(offlineOnly: Boolean) {
        mutableState.update { it.copy(offlineOnly = offlineOnly) }
        scheduleSave()
    }

    private fun scheduleSave() {
        if (!state.value.isLoaded) {
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            val current = state.value
            repository.save(
                UserSettingsEntity(
                    voice = current.voice,
                    speechRate = current.speechRate,
                    style = current.style,
                    personalizationProviderType = current.personalizationProviderType.name,
                    customLocalModelPath = current.customLocalModelPath,
                    customEndpoint = current.customEndpoint,
                    customModelName = current.customModelName,
                    offlineOnly = current.offlineOnly,
                ),
            )
        }
    }
}
