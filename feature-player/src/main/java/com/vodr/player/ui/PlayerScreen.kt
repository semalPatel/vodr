package com.vodr.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.playback.PlaybackChapter
import com.vodr.playback.PlaybackStatus
import com.vodr.player.PlayerViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    queue: List<PlaybackChapter> = emptyList(),
    personalizationProviderLabel: String? = null,
    transcriptionProviderLabel: String? = null,
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
    val chapterProgress = if (state.queue.isEmpty()) {
        0f
    } else {
        (state.currentChapterIndex + 1).toFloat() / state.queue.size.toFloat()
    }
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
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LinearProgressIndicator(
                    progress = { chapterProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Chapter progress ${(chapterProgress * 100).toInt()} percent"
                        },
                )
                Text(
                    text = "Chapter ${state.currentChapterIndex + 1} of ${state.queue.size.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(text = if (state.isVoiceReady) "Voice Ready" else "Preparing Voice")
                        },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(text = state.playbackStatus.toReadableLabel()) },
                    )
                }
                if (personalizationProviderLabel != null || transcriptionProviderLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        personalizationProviderLabel?.let { label ->
                            AssistChip(
                                onClick = {},
                                label = { Text(text = "AI: $label") },
                            )
                        }
                        transcriptionProviderLabel?.let { label ->
                            AssistChip(
                                onClick = {},
                                label = { Text(text = "Transcript: $label") },
                            )
                        }
                    }
                }
                Text(
                    text = "Resume position: ${state.resumePositionMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = currentChapter?.title ?: "No generated chapter yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = currentChapter != null,
                        onClick = viewModel::goToPreviousChapter,
                    ) {
                        Text(text = "Prev")
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
                    Text(text = if (isPlaying) "Pause Narration" else "Play Narration")
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

private fun PlaybackStatus.toReadableLabel(): String {
    return when (this) {
        PlaybackStatus.IDLE -> "Idle"
        PlaybackStatus.PREPARING -> "Preparing"
        PlaybackStatus.PLAYING -> "Speaking"
        PlaybackStatus.PAUSED -> "Paused"
        PlaybackStatus.ERROR -> "Error"
    }
}
