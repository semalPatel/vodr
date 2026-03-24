package com.vodr.library.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                ),
            )
        }
    }
}
