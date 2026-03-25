package com.vodr.player.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.parser.DocumentArtworkLoader
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.PlaybackRuntimeMetadata
import com.vodr.playback.PlaybackStatus
import com.vodr.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    queue: List<PlaybackChapter> = emptyList(),
    viewModel: PlayerViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(queue) {
        if (queue.isNotEmpty()) {
            viewModel.updateQueue(queue)
        }
    }

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
        label = "player-chapter-progress",
    )
    val animatedListeningProgress by animateFloatAsState(
        targetValue = chapterListeningProgressTarget,
        label = "player-listening-progress",
    )
    val isPlaying = state.playbackStatus == PlaybackStatus.PLAYING ||
        state.playbackStatus == PlaybackStatus.PREPARING
    var isChapterMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = "Player") })
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Playback position",
                            style = MaterialTheme.typography.titleMedium,
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
                            Text(
                                text = "Book timeline",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            ChapterTimelineMarkers(
                                queue = state.queue,
                                currentChapterIndex = state.currentChapterIndex,
                                onSelectChapter = viewModel::selectChapter,
                            )
                        }
                    }
                }
                Crossfade(
                    targetState = currentChapter,
                    label = "player-chapter-preview",
                ) { chapter ->
                    chapter?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Chapter preview",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = it.text.take(260),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = currentChapter != null,
                        onClick = viewModel::goToPreviousChapter,
                    ) {
                        Text(text = "Prev")
                    }
                    Button(
                        enabled = currentChapter != null && state.isVoiceReady,
                        modifier = Modifier.semantics {
                            contentDescription = if (isPlaying) {
                                "Pause narration"
                            } else {
                                "Start narration"
                            }
                        },
                        onClick = viewModel::togglePlayback,
                    ) {
                        Text(text = if (isPlaying) "Pause" else "Play")
                    }
                    Button(
                        enabled = currentChapter != null,
                        onClick = viewModel::goToNextChapter,
                    ) {
                        Text(text = "Next")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = currentChapter != null,
                        onClick = viewModel::seekBackward,
                    ) {
                        Text(text = "-15s")
                    }
                    Button(
                        enabled = currentChapter != null,
                        onClick = { isChapterMenuExpanded = true },
                    ) {
                        Text(text = "Chapters")
                    }
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
                    Button(
                        enabled = currentChapter != null,
                        onClick = viewModel::seekForward,
                    ) {
                        Text(text = "+15s")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Playback speed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                            FilterChip(
                                selected = state.playbackSpeed == speed,
                                onClick = {
                                    viewModel.updatePlaybackSpeed(speed)
                                },
                                label = { Text(text = "${speed}x") },
                            )
                        }
                    }
                }
                state.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = queue,
            key = { _, chapter -> chapter.id },
        ) { index, chapter ->
            val isSelected = index == currentChapterIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = if (isSelected) 18.dp else 12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.28f)
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (documentTitle != null && documentSourceUri != null && documentMimeType != null) {
                    PlayerArtwork(
                        title = documentTitle,
                        sourceUri = documentSourceUri,
                        mimeType = documentMimeType,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
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
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(text = if (isVoiceReady) "Voice Ready" else "Preparing Voice")
                    },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(text = playbackStatusLabel) },
                )
            }
            if (runtimeMetadata?.personalizationProviderLabel != null ||
                runtimeMetadata?.transcriptionProviderLabel != null
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    runtimeMetadata.personalizationProviderLabel?.let { label ->
                        AssistChip(
                            onClick = {},
                            label = { Text(text = "AI: $label") },
                        )
                    }
                    runtimeMetadata.transcriptionProviderLabel?.let { label ->
                        AssistChip(
                            onClick = {},
                            label = { Text(text = "Transcript: $label") },
                        )
                    }
                }
                runtimeMetadata.personalizationDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                runtimeMetadata.transcriptionDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerArtwork(
    title: String,
    sourceUri: String,
    mimeType: String,
) {
    val bitmap by rememberDocumentArtworkBitmap(
        title = title,
        sourceUri = sourceUri,
        mimeType = mimeType,
    )
    if (bitmap != null) {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = "$title cover art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 144.dp, height = 192.dp)
                .clip(MaterialTheme.shapes.large),
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 144.dp, height = 192.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(2).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun rememberDocumentArtworkBitmap(
    title: String,
    sourceUri: String,
    mimeType: String,
): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(
        initialValue = null,
        key1 = title,
        key2 = sourceUri,
        key3 = mimeType,
    ) {
        value = withContext(Dispatchers.IO) {
            DocumentArtworkLoader.load(
                context = context,
                sourceUri = sourceUri,
                mimeType = mimeType,
                title = title,
            )
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
