package com.vodr.library.ui

import android.graphics.Bitmap
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vodr.library.ImportDocumentRequest
import com.vodr.library.ImportedDocument
import com.vodr.library.LibraryViewModel
import com.vodr.parser.DocumentArtworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    viewModel: LibraryViewModel,
    continueListeningDocumentTitle: String? = null,
    continueListeningDocumentSourceUri: String? = null,
    continueListeningDocumentMimeType: String? = null,
    continueListeningChapterTitle: String? = null,
    continueListeningProgress: Float = 0f,
    continueListeningStatus: String? = null,
    recentSessions: List<RecentListeningSessionItem> = emptyList(),
    onOpenGenerate: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onResumePlayback: () -> Unit = {},
    onOpenRecentSession: (String) -> Unit = {},
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
                    enabled = !state.isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Import a PDF or EPUB book" },
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
                modifier = Modifier.semantics { contentDescription = "Add a new book" },
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
                LibraryHeroCard(
                    documentCount = state.documents.size,
                    isImporting = state.isImporting,
                    onOpenGenerate = onOpenGenerate,
                )
                if (continueListeningDocumentTitle != null &&
                    continueListeningDocumentSourceUri != null &&
                    continueListeningDocumentMimeType != null
                ) {
                    ContinueListeningCard(
                        documentTitle = continueListeningDocumentTitle,
                        documentSourceUri = continueListeningDocumentSourceUri,
                        documentMimeType = continueListeningDocumentMimeType,
                        chapterTitle = continueListeningChapterTitle,
                        progress = continueListeningProgress,
                        status = continueListeningStatus ?: "Ready",
                        onResumePlayback = onResumePlayback,
                    )
                }
                if (recentSessions.isNotEmpty()) {
                    RecentSessionsSection(
                        sessions = recentSessions,
                        onOpenSession = onOpenRecentSession,
                    )
                }
                Text(
                    text = "Recently opened books",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Crossfade(targetState = state.documents.isEmpty(), label = "library-empty-list") { isEmpty ->
                    if (isEmpty) {
                        EmptyLibraryCard(
                            onAddBook = { showAddSheet = true },
                        )
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
                                        .animateContentSize()
                                        .clickable(onClick = onOpenGenerate),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    ),
                                ) {
                                    LibraryDocumentCardContent(document = document)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class RecentListeningSessionItem(
    val sessionId: String,
    val documentTitle: String,
    val documentSourceUri: String,
    val documentMimeType: String,
    val chapterTitle: String,
    val progressFraction: Float,
    val updatedAtEpochMs: Long,
    val personalizationProviderLabel: String? = null,
    val transcriptionProviderLabel: String? = null,
)

@Composable
private fun LibraryHeroCard(
    documentCount: Int,
    isImporting: Boolean,
    onOpenGenerate: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Your listening library",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (documentCount == 0) {
                    "Import a book to create your first offline-ready listening session."
                } else {
                    "$documentCount imported book${if (documentCount == 1) "" else "s"} ready for generation and playback."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(text = if (isImporting) "Importing" else "Offline-first")
                    },
                )
                TextButton(onClick = onOpenGenerate, enabled = documentCount > 0) {
                    Text(text = "Open Generate")
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(
    documentTitle: String,
    documentSourceUri: String,
    documentMimeType: String,
    chapterTitle: String?,
    progress: Float,
    status: String,
    onResumePlayback: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentArtworkThumbnail(
                title = documentTitle,
                sourceUri = documentSourceUri,
                mimeType = documentMimeType,
                modifier = Modifier.size(width = 70.dp, height = 96.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Continue listening",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chapterTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onResumePlayback) {
                    Text(text = "Open player")
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsSection(
    sessions: List<RecentListeningSessionItem>,
    onOpenSession: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Recent listening sessions",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(
                items = sessions,
                key = { _, session -> session.sessionId },
            ) { _, session ->
                Card(
                    modifier = Modifier
                        .width(260.dp)
                        .animateContentSize()
                        .clickable { onOpenSession(session.sessionId) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DocumentArtworkThumbnail(
                                title = session.documentTitle,
                                sourceUri = session.documentSourceUri,
                                mimeType = session.documentMimeType,
                                modifier = Modifier.size(width = 56.dp, height = 76.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = session.documentTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = session.chapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = relativeImportedLabel(session.updatedAtEpochMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { session.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            session.personalizationProviderLabel?.let { label ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(text = "AI: $label") },
                                )
                            }
                            session.transcriptionProviderLabel?.let { label ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(text = "Transcript: $label") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onAddBook: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "No books yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Tap Add Book to import a PDF or EPUB, then generate a spoken chapter queue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAddBook) {
                Text(text = "Import your first book")
            }
        }
    }
}

@Composable
private fun LibraryDocumentCardContent(
    document: ImportedDocument,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentArtworkThumbnail(
            title = document.metadata.displayName,
            sourceUri = document.metadata.sourceUri,
            mimeType = document.metadata.mimeType,
            modifier = Modifier.size(width = 56.dp, height = 72.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = document.metadata.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildDocumentSubtitle(document = document),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                AssistChip(
                    onClick = {},
                    label = { Text(text = relativeImportedLabel(document.metadata.importedAtEpochMs)) },
                )
            }
        }
    }
}

@Composable
private fun DocumentArtworkThumbnail(
    title: String,
    sourceUri: String,
    mimeType: String,
    modifier: Modifier = Modifier,
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
            modifier = modifier.clip(MaterialTheme.shapes.medium),
        )
    } else {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
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
) : androidx.compose.runtime.State<Bitmap?> {
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

private fun buildDocumentSubtitle(document: ImportedDocument): String {
    val sizeLabel = document.metadata.byteCount?.let(::toReadableSize) ?: "Unknown size"
    return "$sizeLabel • ${relativeImportedLabel(document.metadata.importedAtEpochMs)}"
}

private fun relativeImportedLabel(
    importedAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    val dayDelta = ((nowEpochMs - importedAtEpochMs).coerceAtLeast(0L) / DAY_IN_MS).toInt()
    return when (dayDelta) {
        0 -> "Added today"
        1 -> "Added yesterday"
        else -> "Added ${dayDelta}d ago"
    }
}

private fun toReadableSize(byteCount: Long): String {
    return when {
        byteCount >= 1_048_576L -> String.format("%.1f MB", byteCount / 1_048_576f)
        byteCount >= 1_024L -> "${byteCount / 1_024} KB"
        else -> "$byteCount B"
    }
}

private const val DAY_IN_MS: Long = 24L * 60L * 60L * 1_000L

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
