package com.vodr.library.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vodr.library.ImportDocumentRequest
import com.vodr.library.LibraryViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenGenerate: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.lastImportedDocumentId) {
        if (state.lastImportedDocumentId != null) {
            onOpenGenerate()
            viewModel.consumeLastImportedDocument()
        }
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val contentResolver = context.contentResolver
        val detectedMimeType = contentResolver.getType(uri)
        var displayName = uri.lastPathSegment ?: "document"
        var byteCount: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    displayName = cursor.getString(displayNameIndex) ?: displayName
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    byteCount = cursor.getLong(sizeIndex)
                }
            }
        }
        val request = toImportDocumentRequest(
            sourceUri = uri.toString(),
            displayName = displayName,
            detectedMimeType = detectedMimeType,
            byteCount = byteCount,
            lastModifiedEpochMs = null,
        )
        if (request != null) {
            viewModel.importDocument(request = request)
        } else {
            viewModel.reportUnsupportedSelection()
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "New Book",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Import a book or continue converting an existing one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isImporting,
                    onClick = {
                        showAddSheet = false
                        openDocumentLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/epub+zip",
                            ),
                        )
                    },
                ) {
                    Text(text = if (state.isImporting) "Importing..." else "Import PDF/EPUB")
                }
                AnimatedVisibility(visible = state.documents.isNotEmpty()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showAddSheet = false
                            onOpenGenerate()
                        },
                    ) {
                        Text(text = "Convert Existing Book")
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Library") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(text = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                expanded = true,
                onClick = { showAddSheet = true },
                icon = { Text(text = "+") },
                text = { Text(text = "Add Book") },
            )
        },
    ) { contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Recently opened books",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.errorMessage != null) {
                    Text(text = state.errorMessage)
                }
                Crossfade(targetState = state.documents.isEmpty(), label = "library-empty-list") { isEmpty ->
                    if (isEmpty) {
                        Text(text = "No books yet. Tap Add Book to import your first title.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = state.documents,
                                key = { it.id },
                            ) { document ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenGenerate),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = document.metadata.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = if (document.metadata.mimeType.contains("epub")) "EPUB" else "PDF",
                                                    )
                                                },
                                            )
                                            document.metadata.byteCount?.let { size ->
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(text = "${size / 1024} KB") },
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
        }
    }
}

internal fun toImportDocumentRequest(
    sourceUri: String,
    displayName: String,
    detectedMimeType: String?,
    byteCount: Long?,
    lastModifiedEpochMs: Long?,
): ImportDocumentRequest? {
    val normalizedMimeType = normalizeSupportedMimeType(
        detectedMimeType = detectedMimeType,
        displayName = displayName,
    ) ?: return null
    return ImportDocumentRequest(
        sourceUri = sourceUri,
        displayName = displayName,
        mimeType = normalizedMimeType,
        byteCount = byteCount,
        lastModifiedEpochMs = lastModifiedEpochMs,
    )
}

private fun normalizeSupportedMimeType(
    detectedMimeType: String?,
    displayName: String,
): String? {
    val normalizedDetectedMimeType = detectedMimeType?.trim()?.lowercase()
    return when {
        normalizedDetectedMimeType == "application/pdf" -> "application/pdf"
        normalizedDetectedMimeType == "application/x-pdf" -> "application/pdf"
        normalizedDetectedMimeType == "application/epub+zip" -> "application/epub+zip"
        normalizedDetectedMimeType == "application/epub" -> "application/epub+zip"
        displayName.lowercase().endsWith(".pdf") -> "application/pdf"
        displayName.lowercase().endsWith(".epub") -> "application/epub+zip"
        else -> null
    }
}
