package com.vodr.library.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vodr.library.ImportDocumentRequest
import com.vodr.library.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = remember { LibraryViewModel() },
    modifier: Modifier = Modifier,
) {
    val state = viewModel.state

    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Import local documents and keep their metadata in the library.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    viewModel.importDocument(
                        request = ImportDocumentRequest(
                            sourceUri = "content://documents/document/sample",
                            displayName = "Sample.pdf",
                            mimeType = "application/pdf",
                            byteCount = 0L,
                            lastModifiedEpochMs = null,
                        ),
                    )
                },
            ) {
                Text(text = "Import sample PDF")
            }
            if (state.errorMessage != null) {
                Text(text = state.errorMessage)
            }
            if (state.documents.isEmpty()) {
                Text(text = "No documents imported yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.documents.forEach { document ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(text = document.metadata.displayName)
                                Text(text = document.metadata.sourceUri)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }
        }
    }
}
