package com.vodr.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.PlaybackRuntimeMetadata
import com.vodr.playback.PlaybackSessionSummary
import com.vodr.playback.PlaybackStatus
import com.vodr.player.PlayerViewModel
import com.vodr.ui.VodrArtworkListRow
import com.vodr.ui.VodrChoiceChip
import com.vodr.ui.DocumentArtworkCover
import com.vodr.ui.PlaybackActionButton
import com.vodr.ui.VodrMessageText
import com.vodr.ui.VodrMessageTone
import com.vodr.ui.VodrInlineAction
import com.vodr.ui.VodrScreenColumn
import com.vodr.ui.VodrScreenScaffold
import com.vodr.ui.theme.VodrMotionSpecs
import com.vodr.ui.theme.VodrSurfaceStyles
import com.vodr.ui.theme.VodrUiTheme
import com.vodr.ui.theme.vodrAnimateContentSize
import com.vodr.ui.VodrMetaChip
import com.vodr.ui.VodrSectionHeader

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    viewModel: PlayerViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val spacing = VodrUiTheme.spacing
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val currentChapter = state.currentChapter
    val chapterProgressTarget = if (state.queue.isEmpty()) {
        0f
    } else {
        (state.currentChapterIndex + 1).toFloat() / state.queue.size.toFloat()
    }
    var isScrubbing by remember(currentChapter?.id) { mutableStateOf(false) }
    var scrubPositionMs by remember(currentChapter?.id) { mutableStateOf(state.resumePositionMs.toFloat()) }
    val currentChapterDurationMs = state.currentChapterDurationMs.coerceAtLeast(0L)
    LaunchedEffect(state.resumePositionMs, state.currentChapterDurationMs, currentChapter?.id, isScrubbing) {
        if (!isScrubbing) {
            scrubPositionMs = state.resumePositionMs
                .coerceIn(0L, currentChapterDurationMs)
                .toFloat()
        }
    }
    val displayedPositionMs = if (isScrubbing) {
        scrubPositionMs.toLong()
    } else {
        state.resumePositionMs
    }.coerceIn(0L, currentChapterDurationMs)
    val chapterListeningProgressTarget = if (currentChapterDurationMs > 0L) {
        (displayedPositionMs.toFloat() / currentChapterDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedChapterProgress by animateFloatAsState(
        targetValue = chapterProgressTarget,
        animationSpec = VodrMotionSpecs.progressFloat(),
        label = "player-chapter-progress",
    )
    val animatedListeningProgress by animateFloatAsState(
        targetValue = chapterListeningProgressTarget,
        animationSpec = VodrMotionSpecs.progressFloat(),
        label = "player-listening-progress",
    )
    val isPlaying = state.playbackStatus == PlaybackStatus.PLAYING ||
        state.playbackStatus == PlaybackStatus.PREPARING
    var isChapterMenuExpanded by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    val currentSessionId = state.sessionHistory.firstOrNull()?.sessionId

    if (showSessionsSheet && state.sessionHistory.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
        ) {
            ListeningSessionsCard(
                sessions = state.sessionHistory,
                currentSessionId = currentSessionId,
                onRestoreSession = {
                    showSessionsSheet = false
                    viewModel.restoreSession(it)
                },
                onRemoveSession = viewModel::removeSession,
                onSetFavorite = viewModel::setSessionFavorite,
            )
        }
    }

    VodrScreenScaffold(
        title = "Player",
        modifier = modifier,
        actions = {
            if (state.sessionHistory.isNotEmpty()) {
                VodrInlineAction(
                    label = "Sessions",
                    onClick = { showSessionsSheet = true },
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                )
            }
        },
    ) { contentPadding ->
        VodrScreenColumn(
            contentPadding = contentPadding,
            fillMaxSize = true,
            scrollable = true,
        ) {
            PlayerHeroCard(
                documentTitle = state.activeDocument?.title,
                documentSourceUri = state.activeDocument?.sourceUri,
                documentMimeType = state.activeDocument?.mimeType,
                chapterTitle = currentChapter?.title ?: "No generated chapter yet.",
                chapterIndex = state.currentChapterIndex + 1,
                chapterCount = state.queue.size.coerceAtLeast(1),
                chapterProgress = animatedChapterProgress,
                listeningProgress = animatedListeningProgress,
                listeningLabel = "${formatPlaybackTime(displayedPositionMs)} / ${formatPlaybackTime(currentChapterDurationMs)}",
                isVoiceReady = state.isVoiceReady,
                playbackStatusLabel = state.playbackStatus.toReadableLabel(),
                runtimeMetadata = state.runtimeMetadata,
            )
            Card(
                colors = VodrSurfaceStyles.subtleCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vodrAnimateContentSize()
                        .padding(spacing.md + spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    VodrSectionHeader(
                        title = "Playback position",
                    )
                    Slider(
                        value = displayedPositionMs.toFloat(),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubPositionMs = value
                        },
                        onValueChangeFinished = {
                            viewModel.updateResumePosition(scrubPositionMs.toLong())
                            isScrubbing = false
                        },
                        valueRange = 0f..currentChapterDurationMs.toFloat(),
                        enabled = currentChapter != null && currentChapterDurationMs > 0L,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatPlaybackTime(displayedPositionMs),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatPlaybackTime(currentChapterDurationMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.queue.isNotEmpty()) {
                        VodrSectionHeader(
                            title = "Book timeline",
                        )
                        ChapterTimelineMarkers(
                            queue = state.queue,
                            currentChapterIndex = state.currentChapterIndex,
                            onSelectChapter = viewModel::selectChapter,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PlaybackActionButton(
                    icon = Icons.Rounded.SkipPrevious,
                    label = "Prev",
                    contentDescription = "Go to previous chapter",
                    onClick = viewModel::goToPreviousChapter,
                    enabled = currentChapter != null,
                )
                PlaybackActionButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (isPlaying) "Pause" else "Play",
                    contentDescription = if (isPlaying) {
                        "Pause narration"
                    } else {
                        "Start narration"
                    },
                    onClick = viewModel::togglePlayback,
                    enabled = currentChapter != null,
                )
                PlaybackActionButton(
                    icon = Icons.Rounded.SkipNext,
                    label = "Next",
                    contentDescription = "Go to next chapter",
                    onClick = viewModel::goToNextChapter,
                    enabled = currentChapter != null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PlaybackActionButton(
                    icon = Icons.Rounded.FastRewind,
                    label = "-15s",
                    contentDescription = "Seek backward 15 seconds",
                    onClick = viewModel::seekBackward,
                    enabled = currentChapter != null,
                )
                PlaybackActionButton(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    label = "Chapters",
                    contentDescription = "Open chapter picker",
                    onClick = { isChapterMenuExpanded = true },
                    enabled = currentChapter != null,
                )
                DropdownMenu(
                    expanded = isChapterMenuExpanded,
                    onDismissRequest = { isChapterMenuExpanded = false },
                ) {
                    state.queue.forEachIndexed { index, chapter ->
                        DropdownMenuItem(
                            text = { Text(text = chapter.title) },
                            onClick = {
                                isChapterMenuExpanded = false
                                viewModel.selectChapter(index)
                            },
                        )
                    }
                }
                PlaybackActionButton(
                    icon = Icons.Rounded.FastForward,
                    label = "+15s",
                    contentDescription = "Seek forward 15 seconds",
                    onClick = viewModel::seekForward,
                    enabled = currentChapter != null,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                VodrSectionHeader(
                    title = "Playback speed",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    listOf(0.85f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                        VodrChoiceChip(
                            label = "${speed}x",
                            selected = state.playbackSpeed == speed,
                            onClick = {
                                viewModel.updatePlaybackSpeed(speed)
                            },
                        )
                    }
                }
            }
            state.errorMessage?.let { errorMessage ->
                VodrMessageText(
                    text = errorMessage,
                    tone = VodrMessageTone.ERROR,
                )
            }
        }
    }
}

@Composable
private fun ListeningSessionsCard(
    sessions: List<PlaybackSessionSummary>,
    currentSessionId: String?,
    onRestoreSession: (String) -> Unit,
    onRemoveSession: (String) -> Unit,
    onSetFavorite: (String, Boolean) -> Unit,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
    val displayedSessions = sessions.sortedWith(
        compareByDescending<PlaybackSessionSummary> { it.sessionId == currentSessionId }
            .thenByDescending { it.isFavorite }
            .thenByDescending { it.updatedAtEpochMs },
    )
    Card(
        colors = VodrSurfaceStyles.subtleCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vodrAnimateContentSize()
                .padding(spacing.md + spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            VodrSectionHeader(
                title = "Listening sessions",
                subtitle = "Switch between saved books and keep favorites pinned near the top.",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                itemsIndexed(
                    items = displayedSessions,
                    key = { _, session -> session.sessionId },
                ) { _, session ->
                    val isCurrent = session.sessionId == currentSessionId
                    Card(
                        modifier = Modifier.width(sizes.playerSessionCardWidth),
                        colors = VodrSurfaceStyles.sessionCardColors(
                            isCurrent = isCurrent,
                            isFavorite = session.isFavorite,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            VodrArtworkListRow(
                                title = session.documentTitle,
                                sourceUri = session.documentSourceUri,
                                mimeType = session.documentMimeType,
                                subtitle = session.chapterTitle,
                                supportingText = session.updatedAtEpochMs.toSessionUpdatedLabel(
                                    isCurrent = isCurrent,
                                ),
                                artworkWidth = sizes.playerSessionArtworkWidth,
                                artworkHeight = sizes.playerSessionArtworkHeight,
                                titleTextStyle = MaterialTheme.typography.titleSmall,
                                subtitleTextStyle = MaterialTheme.typography.bodySmall,
                                supportingTextStyle = MaterialTheme.typography.bodySmall,
                            )
                            LinearProgressIndicator(
                                progress = { session.progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                if (session.isFavorite) {
                                    VodrMetaChip(
                                        label = "Favorite",
                                        leadingIcon = Icons.Rounded.Star,
                                    )
                                }
                                VodrInlineAction(
                                    label = if (isCurrent) "Current session" else "Switch",
                                    onClick = { onRestoreSession(session.sessionId) },
                                    enabled = !isCurrent,
                                    icon = if (isCurrent) {
                                        Icons.Rounded.PlayArrow
                                    } else {
                                        Icons.AutoMirrored.Rounded.MenuBook
                                    },
                                )
                                VodrChoiceChip(
                                    label = "Favorite",
                                    selected = session.isFavorite,
                                    onClick = {
                                        onSetFavorite(
                                            session.sessionId,
                                            !session.isFavorite,
                                        )
                                    },
                                    selectedIcon = Icons.Rounded.Star,
                                    unselectedIcon = Icons.Rounded.StarBorder,
                                )
                                if (!isCurrent) {
                                    VodrInlineAction(
                                        label = "Remove",
                                        onClick = { onRemoveSession(session.sessionId) },
                                        icon = Icons.Rounded.DeleteOutline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterTimelineMarkers(
    queue: List<PlaybackChapter>,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(VodrUiTheme.spacing.xs),
    ) {
        itemsIndexed(
            items = queue,
            key = { _, chapter -> chapter.id },
        ) { index, chapter ->
            val isSelected = index == currentChapterIndex
            val sizes = VodrUiTheme.sizes
            val spacing = VodrUiTheme.spacing
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs - spacing.xxxs),
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = sizes.timelineMarkerWidth,
                            height = if (isSelected) {
                                sizes.timelineMarkerSelectedHeight
                            } else {
                                sizes.timelineMarkerHeight
                            },
                        )
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceTint.copy(
                                    alpha = VodrUiTheme.alpha.inactiveMarker,
                                )
                            },
                        )
                        .clickable { onSelectChapter(index) },
                )
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlayerHeroCard(
    documentTitle: String?,
    documentSourceUri: String?,
    documentMimeType: String?,
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    chapterProgress: Float,
    listeningProgress: Float,
    listeningLabel: String,
    isVoiceReady: Boolean,
    playbackStatusLabel: String,
    runtimeMetadata: PlaybackRuntimeMetadata?,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
    Card(
        colors = VodrSurfaceStyles.heroCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vodrAnimateContentSize()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (documentTitle != null && documentSourceUri != null && documentMimeType != null) {
                    DocumentArtworkCover(
                        title = documentTitle,
                        sourceUri = documentSourceUri,
                        mimeType = documentMimeType,
                        modifier = Modifier.size(
                            width = sizes.heroArtworkWidth,
                            height = sizes.heroArtworkHeight,
                        ),
                        shape = MaterialTheme.shapes.large,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(sizes.heroPlaceholderSize)
                            .semantics {
                                contentDescription = "Current chapter artwork placeholder"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chapterIndex.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            documentTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Chapter $chapterIndex of $chapterCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { chapterProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Chapter progress ${(chapterProgress * 100).toInt()} percent"
                    },
            )
            LinearProgressIndicator(
                progress = { listeningProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = listeningLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                VodrMetaChip(
                    label = if (isVoiceReady) "Voice Ready" else "Preparing Voice",
                )
                VodrMetaChip(
                    label = playbackStatusLabel,
                )
            }
            if (runtimeMetadata?.personalizationProviderLabel != null ||
                runtimeMetadata?.transcriptionProviderLabel != null
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    runtimeMetadata.personalizationProviderLabel?.let { label ->
                        VodrMetaChip(
                            label = "AI: $label",
                        )
                    }
                    runtimeMetadata.transcriptionProviderLabel?.let { label ->
                        VodrMetaChip(
                            label = "Transcript: $label",
                        )
                    }
                }
            }
        }
    }
}

private fun PlaybackStatus.toReadableLabel(): String {
    return when (this) {
        PlaybackStatus.IDLE -> "Idle"
        PlaybackStatus.PREPARING -> "Preparing"
        PlaybackStatus.PLAYING -> "Speaking"
        PlaybackStatus.PAUSED -> "Paused"
        PlaybackStatus.ERROR -> "Error"
    }
}

private fun formatPlaybackTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun Long.toSessionUpdatedLabel(
    isCurrent: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    if (isCurrent) {
        return "Current session"
    }
    val dayDelta = ((nowEpochMs - this).coerceAtLeast(0L) / DAY_IN_MS).toInt()
    return when (dayDelta) {
        0 -> "Updated today"
        1 -> "Updated yesterday"
        else -> "Updated ${dayDelta}d ago"
    }
}

private const val DAY_IN_MS: Long = 24L * 60L * 60L * 1_000L
