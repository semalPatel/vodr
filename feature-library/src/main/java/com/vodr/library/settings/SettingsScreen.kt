package com.vodr.library.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.ai.PersonalizationProviderType
import com.vodr.ui.VodrScreenTopBar
import com.vodr.ui.VodrSectionHeader
import com.vodr.ui.theme.VodrUiTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val spacing = VodrUiTheme.spacing

    Scaffold(
        modifier = modifier,
        topBar = {
            VodrScreenTopBar(title = "Settings")
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                VodrSectionHeader(
                    title = "Voice",
                )
                OutlinedTextField(
                    value = state.voice,
                    onValueChange = {
                        viewModel.updateVoice(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                VodrSectionHeader(
                    title = "Speech rate: ${"%.2f".format(state.speechRate)}",
                )
                Slider(
                    value = state.speechRate,
                    onValueChange = {
                        viewModel.updateSpeechRate(it)
                    },
                    valueRange = 0.5f..2.0f,
                )
                VodrSectionHeader(
                    title = "Style",
                )
                OutlinedTextField(
                    value = state.style,
                    onValueChange = {
                        viewModel.updateStyle(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                VodrSectionHeader(
                    title = "Personalization Provider",
                    subtitle = "Auto prefers device AI first, then your configured local model, then offline fallback.",
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    PersonalizationProviderType.entries.forEach { providerType ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.personalizationProviderType == providerType,
                                onClick = {
                                    viewModel.updatePersonalizationProviderType(providerType)
                                },
                            )
                            Text(text = providerType.toReadableLabel())
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Offline-only mode",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(
                        checked = state.offlineOnly,
                        onCheckedChange = viewModel::updateOfflineOnly,
                    )
                }
                OutlinedTextField(
                    value = state.customLocalModelPath,
                    onValueChange = viewModel::updateCustomLocalModelPath,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Local model path") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.customModelName,
                    onValueChange = viewModel::updateCustomModelName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Model name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.customEndpoint,
                    onValueChange = viewModel::updateCustomEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Custom endpoint") },
                    singleLine = true,
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
