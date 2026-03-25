package com.vodr.generate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.vodr.ui.VodrMetaChip
import com.vodr.ui.VodrScreenTopBar
import com.vodr.ui.VodrSegmentedSelector
import com.vodr.ui.VodrSelectionButton
import com.vodr.ui.VodrSectionHeader
import com.vodr.ui.theme.VodrCrossfade
import com.vodr.ui.theme.VodrMotionSpecs
import com.vodr.ui.theme.VodrSurfaceStyles
import com.vodr.ui.theme.VodrUiTheme
import com.vodr.ui.theme.vodrAnimateContentSize

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
            VodrScreenTopBar(title = "Generate")
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
                VodrSectionHeader(
                    title = "Document",
                )
                documents.forEach { document ->
                    VodrSelectionButton(
                        label = document.displayName,
                        selected = selectedDocumentId == document.id,
                        onClick = { selectedDocumentId = document.id },
                    )
                }

                VodrSectionHeader(
                    title = "Mode",
                )
                VodrSegmentedSelector(
                    options = GenerationMode.entries,
                    selectedOption = selectedMode,
                    onOptionSelected = { selectedMode = it },
                    optionLabel = { mode -> mode.name.replace('_', ' ') },
                )

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
            animationSpec = VodrMotionSpecs.progressFloat(),
            label = "generation-phase-progress",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vodrAnimateContentSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            VodrCrossfade(
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
                VodrMetaChip(
                    label = if (generationState.isGenerating) {
                        "Working"
                    } else {
                        "Idle"
                    },
                )
                VodrMetaChip(
                    label = when (generationState.phase) {
                        GenerationPhase.IDLE -> "Awaiting input"
                        GenerationPhase.READY -> "Queue ready"
                        else -> "Phase ${(generationState.phase.progress * 100).toInt()}%"
                    },
                )
            }
            generationState.runtimeSummary?.let { runtimeSummary ->
                VodrSectionHeader(
                    title = "Active Providers",
                )
                VodrMetaChip(
                    label = "Personalization: ${runtimeSummary.personalizationProvider.toDisplayName()}",
                )
                VodrMetaChip(
                    label = "Transcription: ${runtimeSummary.transcriptionProvider.toDisplayName()}",
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
