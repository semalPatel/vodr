package com.vodr.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vodr.playback.PlaybackChapter
import com.vodr.player.PlayerViewModel

@Composable
fun PlayerScreen(
    queue: List<PlaybackChapter> = emptyList(),
    viewModel: PlayerViewModel = remember { PlayerViewModel() },
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(queue) {
        if (queue.isNotEmpty()) {
            viewModel.updateQueue(queue)
        }
    }
    val state = viewModel.state

    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Player",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Chapter ${state.currentChapterIndex + 1} of ${state.queue.size.coerceAtLeast(1)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Resume position: ${state.resumePositionMs} ms",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                Button(onClick = viewModel::goToPreviousChapter) {
                    Text(text = "Prev")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = viewModel::goToNextChapter) {
                    Text(text = "Next")
                }
            }
        }
    }
}
