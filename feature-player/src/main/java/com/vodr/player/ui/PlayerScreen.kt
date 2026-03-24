package com.vodr.player.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vodr.playback.PlaybackChapter
import com.vodr.player.PlayerViewModel
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    queue: List<PlaybackChapter> = emptyList(),
    viewModel: PlayerViewModel = viewModel(),
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
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val currentChapter = state.queue.getOrNull(state.currentChapterIndex)
    val chapterProgress = if (state.queue.isEmpty()) {
        0f
    } else {
        (state.currentChapterIndex + 1).toFloat() / state.queue.size.toFloat()
    }

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
                        label = { Text(text = if (isTtsReady) "Voice Ready" else "Preparing Voice") },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(text = if (isSpeaking) "Speaking" else "Idle") },
                    )
                }
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
                    modifier = Modifier.semantics {
                        contentDescription = if (isSpeaking) {
                            "Pause narration"
                        } else {
                            "Start narration"
                        }
                    },
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
}
