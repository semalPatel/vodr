package com.vodr.player.ui

import android.speech.tts.TextToSpeech
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vodr.playback.PlaybackChapter
import com.vodr.player.PlayerViewModel
import java.util.Locale

@Composable
fun PlayerScreen(
    queue: List<PlaybackChapter> = emptyList(),
    viewModel: PlayerViewModel = remember { PlayerViewModel() },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }

    LaunchedEffect(queue) {
        if (queue.isNotEmpty()) {
            viewModel.updateQueue(queue)
        }
    }
    val state = viewModel.state
    val currentChapter = state.queue.getOrNull(state.currentChapterIndex)

    LaunchedEffect(currentChapter?.id) {
        textToSpeech?.stop()
        isSpeaking = false
    }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
        textToSpeech = tts
        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isSpeaking = false
            isTtsReady = false
        }
    }

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
            Text(
                text = currentChapter?.title ?: "No generated chapter yet.",
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
            Button(
                enabled = currentChapter != null && isTtsReady,
                onClick = {
                    val chapter = currentChapter ?: return@Button
                    val tts = textToSpeech ?: return@Button
                    if (isSpeaking) {
                        tts.stop()
                        isSpeaking = false
                    } else {
                        tts.speak(chapter.text, TextToSpeech.QUEUE_FLUSH, null, chapter.id)
                        isSpeaking = true
                    }
                },
            ) {
                Text(text = if (isSpeaking) "Pause Narration" else "Play Narration")
            }
        }
    }
}
