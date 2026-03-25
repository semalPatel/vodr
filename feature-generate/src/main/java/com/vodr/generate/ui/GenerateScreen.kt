package com.vodr.generate.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import com.vodr.ai.toDisplayName
import com.vodr.generate.GenerationPhase
import com.vodr.generate.GenerationMode
import com.vodr.generate.GenerationUiState
import com.vodr.generate.toUserMessage
import com.vodr.ui.theme.VodrSurfaceStyles
import com.vodr.ui.theme.VodrUiTheme

data class GenerationSourceDocument(
    val id: String,
    val displayName: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GenerateScreen(
    documents: List<GenerationSourceDocument>,
    generationState: GenerationUiState,
    onGenerateRequested: (documentId: String, mode: GenerationMode) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VodrUiTheme.spacing
    var selectedDocumentId by remember(documents) {
        mutableStateOf(documents.firstOrNull()?.id)
    }
    var selectedMode by remember { mutableStateOf(GenerationMode.BALANCED) }
    LaunchedEffect(generationState.queue) {
        if (generationState.queue.isNotEmpty()) {
            onOpenPlayer()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = "Generate") })
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                if (documents.isEmpty()) {
                    Text(text = "Import a PDF/EPUB in Library first.")
                    return@Column
                }

                Text(
                    text = "Turn any imported book into a focused listening session.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                GenerationStatusCard(generationState = generationState)
                Text(
                    text = "Document",
                    style = MaterialTheme.typography.titleMedium,
                )
                documents.forEach { document ->
                    val isSelected = selectedDocumentId == document.id
                    if (isSelected) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedDocumentId = document.id },
                        ) {
                            Text(text = document.displayName)
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedDocumentId = document.id },
                        ) {
                            Text(text = document.displayName)
                        }
                    }
                }

                Text(
                    text = "Mode",
                    style = MaterialTheme.typography.titleMedium,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GenerationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GenerationMode.entries.size,
                            ),
                        ) {
                            Text(text = mode.name.replace('_', ' '))
                        }
                    }
                }

                Button(
                    enabled = selectedDocumentId != null && !generationState.isGenerating,
                    modifier = Modifier.semantics { contentDescription = "Generate audio chapters and open player" },
                    onClick = {
                        val documentId = selectedDocumentId ?: return@Button
                        onGenerateRequested(documentId, selectedMode)
                    },
                ) {
                    Text(text = if (generationState.isGenerating) "Generating..." else "Generate and Open Player")
                }
            }
        }
    }
}

@Composable
private fun GenerationStatusCard(
    generationState: GenerationUiState,
) {
    val spacing = VodrUiTheme.spacing
    Card(
        colors = VodrSurfaceStyles.mutedCardColors(),
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = generationState.phase.progress,
            label = "generation-phase-progress",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Crossfade(
                targetState = generationState.phase,
                label = "generation-phase-copy",
            ) { phase ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = phase.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = phase.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (generationState.isGenerating) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (generationState.isGenerating) {
                                "Working"
                            } else {
                                "Idle"
                            },
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (generationState.phase) {
                                GenerationPhase.IDLE -> "Awaiting input"
                                GenerationPhase.READY -> "Queue ready"
                                else -> "Phase ${(generationState.phase.progress * 100).toInt()}%"
                            },
                        )
                    },
                )
            }
            generationState.runtimeSummary?.let { runtimeSummary ->
                Text(
                    text = "Active Providers",
                    style = MaterialTheme.typography.titleMedium,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Personalization: ${runtimeSummary.personalizationProvider.toDisplayName()}",
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Transcription: ${runtimeSummary.transcriptionProvider.toDisplayName()}",
                        )
                    },
                )
                runtimeSummary.personalizationDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                runtimeSummary.transcriptionDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            generationState.error?.let {
                Text(
                    text = it.toUserMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
