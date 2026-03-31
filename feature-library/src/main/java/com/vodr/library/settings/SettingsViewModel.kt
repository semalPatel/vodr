package com.vodr.library.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vodr.ai.PersonalizationProviderType
import com.vodr.data.db.entity.UserSettingsEntity
import com.vodr.tts.NarrationProviderType
import com.vodr.tts.NarratorVoicePack
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
    val narrationProviderType: NarrationProviderType =
        NarrationProviderType.valueOf(UserSettingsEntity.DEFAULT_NARRATION_PROVIDER_TYPE),
    val selectedVoicePackId: String = UserSettingsEntity.DEFAULT_SELECTED_VOICE_PACK_ID,
    val voicePackUrl: String = UserSettingsEntity.DEFAULT_VOICE_PACK_URL,
    val cloudNarrationEndpoint: String = UserSettingsEntity.DEFAULT_CLOUD_NARRATION_ENDPOINT,
    val cloudNarrationModelName: String = UserSettingsEntity.DEFAULT_CLOUD_NARRATION_MODEL_NAME,
    val installedVoicePacks: List<NarratorVoicePack> = emptyList(),
    val statusMessage: String? = null,
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
            val voicePacks = repository.loadInstalledVoicePacks()
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
                narrationProviderType = settings.narrationProviderType.toNarrationProviderType(),
                selectedVoicePackId = settings.selectedVoicePackId,
                voicePackUrl = settings.voicePackUrl,
                cloudNarrationEndpoint = settings.cloudNarrationEndpoint,
                cloudNarrationModelName = settings.cloudNarrationModelName,
                installedVoicePacks = voicePacks,
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

    fun updateNarrationProviderType(providerType: NarrationProviderType) {
        mutableState.update { it.copy(narrationProviderType = providerType) }
        scheduleSave()
    }

    fun selectVoicePack(voicePackId: String) {
        mutableState.update { it.copy(selectedVoicePackId = voicePackId) }
        scheduleSave()
    }

    fun updateVoicePackUrl(url: String) {
        mutableState.update { it.copy(voicePackUrl = url) }
        scheduleSave()
    }

    fun updateCloudNarrationEndpoint(endpoint: String) {
        mutableState.update { it.copy(cloudNarrationEndpoint = endpoint) }
        scheduleSave()
    }

    fun updateCloudNarrationModelName(modelName: String) {
        mutableState.update { it.copy(cloudNarrationModelName = modelName) }
        scheduleSave()
    }

    fun installStarterVoicePack() {
        viewModelScope.launch {
            val installed = runCatching { repository.installStarterVoicePack() }
            val packs = repository.loadInstalledVoicePacks()
            mutableState.update { current ->
                current.copy(
                    installedVoicePacks = packs,
                    selectedVoicePackId = installed.getOrNull()?.id ?: current.selectedVoicePackId,
                    statusMessage = installed.fold(
                        onSuccess = { "${it.displayName} is ready offline." },
                        onFailure = { it.message ?: "Unable to install the starter voice pack." },
                    ),
                )
            }
            scheduleSave()
        }
    }

    fun installVoicePackFromUrl() {
        val url = state.value.voicePackUrl.trim()
        if (url.isBlank()) {
            mutableState.update { it.copy(statusMessage = "Add a voice pack URL first.") }
            return
        }
        viewModelScope.launch {
            val installed = runCatching { repository.installVoicePackFromUrl(url) }
            val packs = repository.loadInstalledVoicePacks()
            mutableState.update { current ->
                current.copy(
                    installedVoicePacks = packs,
                    selectedVoicePackId = installed.getOrNull()?.id ?: current.selectedVoicePackId,
                    statusMessage = installed.fold(
                        onSuccess = { "${it.displayName} was installed." },
                        onFailure = { it.message ?: "Unable to download the voice pack." },
                    ),
                )
            }
            scheduleSave()
        }
    }

    fun removeVoicePack(voicePackId: String) {
        viewModelScope.launch {
            repository.removeVoicePack(voicePackId)
            val packs = repository.loadInstalledVoicePacks()
            mutableState.update { current ->
                current.copy(
                    installedVoicePacks = packs,
                    selectedVoicePackId = if (current.selectedVoicePackId == voicePackId) {
                        ""
                    } else {
                        current.selectedVoicePackId
                    },
                    statusMessage = "Voice pack removed.",
                )
            }
            scheduleSave()
        }
    }

    fun consumeStatusMessage() {
        mutableState.update { it.copy(statusMessage = null) }
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
                    narrationProviderType = current.narrationProviderType.name,
                    selectedVoicePackId = current.selectedVoicePackId,
                    voicePackUrl = current.voicePackUrl,
                    cloudNarrationEndpoint = current.cloudNarrationEndpoint,
                    cloudNarrationModelName = current.cloudNarrationModelName,
                ),
            )
        }
    }
}

private fun String.toNarrationProviderType(): NarrationProviderType {
    return runCatching { NarrationProviderType.valueOf(this) }
        .getOrDefault(NarrationProviderType.AUTO)
}
