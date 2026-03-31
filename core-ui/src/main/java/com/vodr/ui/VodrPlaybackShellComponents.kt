package com.vodr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vodr.ui.theme.VodrUiTheme
import com.vodr.ui.theme.vodrAnimateContentSize

@Composable
fun VodrAppShell(
    modifier: Modifier = Modifier,
    bottomBar: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = bottomBar,
            )
        },
        content = content,
    )
}

@Composable
fun VodrRuntimeProviderStrip(
    personalizationProviderLabel: String?,
    transcriptionProviderLabel: String?,
    narrationProviderLabel: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = VodrUiTheme.spacing
    val chips = listOfNotNull(
        personalizationProviderLabel?.let { "AI: $it" },
        transcriptionProviderLabel?.let { "Labels: $it" },
        narrationProviderLabel?.let { "Narrator: $it" },
    )
    if (chips.isEmpty()) {
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { label ->
            VodrMetaChip(label = label)
        }
    }
}

@Composable
fun VodrMiniPlayerCard(
    documentTitle: String,
    documentSourceUri: String?,
    documentMimeType: String?,
    chapterTitle: String,
    progress: Float,
    status: String,
    narrationProviderLabel: String?,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
    val elevation = VodrUiTheme.elevation
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.floatingCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vodrAnimateContentSize()
                .padding(
                    horizontal = spacing.miniPlayerInsetHorizontal,
                    vertical = spacing.miniPlayerContentVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (documentSourceUri != null && documentMimeType != null) {
                    DocumentArtworkCover(
                        title = documentTitle,
                        sourceUri = documentSourceUri,
                        mimeType = documentMimeType,
                        modifier = Modifier
                            .size(
                                width = sizes.miniPlayerArtworkWidth,
                                height = sizes.miniPlayerArtworkHeight,
                            )
                            .padding(end = spacing.sm),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxxs),
                ) {
                    Text(
                        text = documentTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = narrationProviderLabel?.let { "$status • $it" } ?: status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactPlaybackIconButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) {
                            "Pause playback"
                        } else {
                            "Resume playback"
                        },
                        onClick = onTogglePlayback,
                    )
                    CompactPlaybackIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Skip to next chapter",
                        onClick = onSkipNext,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
