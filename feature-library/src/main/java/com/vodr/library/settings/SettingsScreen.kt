package com.vodr.library.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.ai.PersonalizationProviderType
import com.vodr.tts.NarrationProviderType
import com.vodr.ui.VodrChoiceChip
import com.vodr.ui.VodrInlineAction
import com.vodr.ui.VodrMessageText
import com.vodr.ui.VodrMessageTone
import com.vodr.ui.VodrRadioOptionRow
import com.vodr.ui.VodrScreenColumn
import com.vodr.ui.VodrScreenScaffold
import com.vodr.ui.VodrSectionHeader
import com.vodr.ui.VodrSliderSetting
import com.vodr.ui.VodrTextSettingField
import com.vodr.ui.VodrToggleSettingRow
import com.vodr.ui.theme.VodrUiTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val spacing = VodrUiTheme.spacing

    VodrScreenScaffold(
        title = "Settings",
        modifier = modifier,
    ) { contentPadding ->
        VodrScreenColumn(
            contentPadding = contentPadding,
            scrollable = true,
        ) {
            VodrSectionHeader(
                title = "Voice",
            )
            VodrTextSettingField(
                label = "Voice",
                value = state.voice,
                onValueChange = viewModel::updateVoice,
            )
            VodrSliderSetting(
                title = "Speech rate: ${"%.2f".format(state.speechRate)}",
                value = state.speechRate,
                onValueChange = viewModel::updateSpeechRate,
                valueRange = 0.5f..2.0f,
            )
            VodrSectionHeader(
                title = "Narrator",
                subtitle = "Auto prefers an installed offline voice pack, then system speech, then the optional cloud narrator when allowed.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                NarrationProviderType.entries.forEach { providerType ->
                    VodrRadioOptionRow(
                        label = providerType.toReadableLabel(),
                        selected = state.narrationProviderType == providerType,
                        onClick = {
                            viewModel.updateNarrationProviderType(providerType)
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                VodrInlineAction(
                    label = "Install starter pack",
                    onClick = viewModel::installStarterVoicePack,
                )
                VodrInlineAction(
                    label = "Download pack",
                    onClick = viewModel::installVoicePackFromUrl,
                )
            }
            VodrTextSettingField(
                label = "Voice pack URL",
                value = state.voicePackUrl,
                onValueChange = viewModel::updateVoicePackUrl,
            )
            if (state.installedVoicePacks.isNotEmpty()) {
                VodrSectionHeader(
                    title = "Installed voice packs",
                    subtitle = "Select an offline narrator profile or remove the ones you no longer need.",
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    state.installedVoicePacks.forEach { pack ->
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            VodrChoiceChip(
                                label = pack.displayName,
                                selected = state.selectedVoicePackId == pack.id,
                                onClick = {
                                    viewModel.selectVoicePack(pack.id)
                                },
                            )
                            VodrInlineAction(
                                label = "Remove",
                                onClick = {
                                    viewModel.removeVoicePack(pack.id)
                                },
                            )
                        }
                    }
                }
            }
            VodrTextSettingField(
                label = "Cloud narration endpoint",
                value = state.cloudNarrationEndpoint,
                onValueChange = viewModel::updateCloudNarrationEndpoint,
            )
            VodrTextSettingField(
                label = "Cloud narration model",
                value = state.cloudNarrationModelName,
                onValueChange = viewModel::updateCloudNarrationModelName,
            )
            VodrSectionHeader(
                title = "Style",
            )
            VodrTextSettingField(
                label = "Style",
                value = state.style,
                onValueChange = viewModel::updateStyle,
            )
            VodrSectionHeader(
                title = "Personalization Provider",
                subtitle = "Auto prefers device AI first, then your configured local model, then offline fallback.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                PersonalizationProviderType.entries.forEach { providerType ->
                    VodrRadioOptionRow(
                        label = providerType.toReadableLabel(),
                        selected = state.personalizationProviderType == providerType,
                        onClick = {
                            viewModel.updatePersonalizationProviderType(providerType)
                        },
                    )
                }
            }
            VodrToggleSettingRow(
                title = "Offline-only mode",
                checked = state.offlineOnly,
                onCheckedChange = viewModel::updateOfflineOnly,
            )
            VodrTextSettingField(
                label = "Local model path",
                value = state.customLocalModelPath,
                onValueChange = viewModel::updateCustomLocalModelPath,
            )
            VodrTextSettingField(
                label = "Model name",
                value = state.customModelName,
                onValueChange = viewModel::updateCustomModelName,
            )
            VodrTextSettingField(
                label = "Custom endpoint",
                value = state.customEndpoint,
                onValueChange = viewModel::updateCustomEndpoint,
            )
            state.statusMessage?.let { message ->
                VodrMessageText(
                    text = message,
                    tone = VodrMessageTone.DEFAULT,
                )
            }
        }
    }
}

private fun PersonalizationProviderType.toReadableLabel(): String {
    return when (this) {
        PersonalizationProviderType.AUTO -> "Auto (Prefer Device AI)"
        PersonalizationProviderType.AI_CORE -> "AI Core"
        PersonalizationProviderType.MEDIA_PIPE -> "MediaPipe"
        PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> "Custom Local Model (Override Device AI)"
        PersonalizationProviderType.CUSTOM_ENDPOINT -> "Custom Endpoint (Override Device AI)"
        PersonalizationProviderType.OFFLINE_HEURISTIC -> "Offline Fallback"
    }
}

private fun NarrationProviderType.toReadableLabel(): String {
    return when (this) {
        NarrationProviderType.AUTO -> "Auto"
        NarrationProviderType.SYSTEM_TTS -> "System TTS"
        NarrationProviderType.OFFLINE_VOICE_PACK -> "Offline Voice Pack"
        NarrationProviderType.CLOUD_ENDPOINT -> "Cloud Narrator"
    }
}
