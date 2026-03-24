package com.vodr.generate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.vodr.generate.GenerationMode
import com.vodr.generate.GenerationUiState

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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (documents.isEmpty()) {
                    Text(text = "Import a PDF/EPUB in Library first.")
                    return@Column
                }

                Text(
                    text = "Build chapter playback from imported text",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(text = "Document")
                documents.forEach { document ->
                    OutlinedButton(
                        onClick = { selectedDocumentId = document.id },
                    ) {
                        val marker = if (selectedDocumentId == document.id) "[selected] " else ""
                        Text(text = marker + document.displayName)
                    }
                }

                Text(text = "Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GenerationMode.entries.forEach { mode ->
                        OutlinedButton(
                            onClick = { selectedMode = mode },
                        ) {
                            val marker = if (selectedMode == mode) "* " else ""
                            Text(text = marker + mode.name)
                        }
                    }
                }

                Button(
                    enabled = selectedDocumentId != null && !generationState.isGenerating,
                    onClick = {
                        val documentId = selectedDocumentId ?: return@Button
                        onGenerateRequested(documentId, selectedMode)
                    },
                ) {
                    Text(text = if (generationState.isGenerating) "Generating..." else "Generate and Open Player")
                }
                if (generationState.isGenerating) {
                    CircularProgressIndicator()
                }
                if (generationState.errorMessage != null) {
                    Text(text = generationState.errorMessage)
                }
            }
        }
    }
}
