package com.vodr.library.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vodr.library.ImportDocumentRequest
import com.vodr.library.ImportedDocument
import com.vodr.library.LibraryViewModel
import com.vodr.ui.VodrArtworkListRow
import com.vodr.ui.VodrChoiceChip
import com.vodr.ui.VodrEmptyStateCard
import com.vodr.ui.VodrInlineAction
import com.vodr.ui.VodrMetaChip
import com.vodr.ui.VodrMessageText
import com.vodr.ui.VodrMessageTone
import com.vodr.ui.VodrRuntimeProviderStrip
import com.vodr.ui.VodrScreenColumn
import com.vodr.ui.VodrScreenScaffold
import com.vodr.ui.VodrSectionHeader
import com.vodr.ui.theme.VodrAnimatedVisibility
import com.vodr.ui.theme.VodrCrossfade
import com.vodr.ui.theme.VodrSurfaceStyles
import com.vodr.ui.theme.VodrUiTheme
import com.vodr.ui.theme.vodrAnimateContentSize

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
    continueListeningIsFavorite: Boolean = false,
    continueListeningNarrationProviderLabel: String? = null,
    recentSessions: List<RecentListeningSessionItem> = emptyList(),
    onOpenGenerate: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onResumePlayback: () -> Unit = {},
    onToggleContinueFavorite: () -> Unit = {},
    onOpenRecentSession: (String) -> Unit = {},
    onRemoveRecentSession: (String) -> Unit = {},
    onToggleRecentSessionFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteLibraryDocumentData: (String) -> Unit = {},
    onClearLibraryData: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = VodrUiTheme.spacing
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showClearLibraryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val hasContinueListening = continueListeningDocumentTitle != null &&
        continueListeningDocumentSourceUri != null &&
        continueListeningDocumentMimeType != null
    val displayedSessions = recentSessions.sortedWith(
        compareByDescending<RecentListeningSessionItem> { it.isFavorite }
            .thenByDescending { it.updatedAtEpochMs },
    )
    val showEmptyLibraryState = state.documents.isEmpty() &&
        displayedSessions.isEmpty() &&
        !hasContinueListening
    val hasSavedLibraryData = state.documents.isNotEmpty() ||
        displayedSessions.isNotEmpty() ||
        hasContinueListening
    val pendingDeleteDocument = state.documents.firstOrNull { it.id == pendingDeleteDocumentId }

    LaunchedEffect(state.lastImportedDocumentId) {
        if (state.lastImportedDocumentId != null) {
            onOpenGenerate()
            viewModel.consumeLastImportedDocument()
        }
    }
    LaunchedEffect(state.lastDeletedDocumentSourceUri) {
        val deletedSourceUri = state.lastDeletedDocumentSourceUri ?: return@LaunchedEffect
        releasePersistedReadPermission(
            sourceUri = deletedSourceUri,
            releaseReadPermission = { uri ->
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            },
        )
        onDeleteLibraryDocumentData(deletedSourceUri)
        viewModel.consumeLastDeletedDocument()
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val contentResolver = context.contentResolver
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
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

    pendingDeleteDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDocumentId = null },
            title = {
                Text(text = "Delete book?")
            },
            text = {
                Text(text = "Remove ${document.metadata.displayName} from your library and clear its saved listening session.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteDocumentId = null
                        viewModel.deleteDocument(document)
                    },
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDocumentId = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (showClearLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showClearLibraryDialog = false },
            title = {
                Text(text = "Clear library?")
            },
            text = {
                Text(text = "This removes all imported books and saved listening sessions from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLibraryDialog = false
                        state.documents.forEach { document ->
                            releasePersistedReadPermission(
                                sourceUri = document.metadata.sourceUri,
                                releaseReadPermission = { uri ->
                                    context.contentResolver.releasePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                },
                            )
                        }
                        viewModel.clearLibrary()
                        onClearLibraryData()
                    },
                ) {
                    Text(text = "Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryDialog = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl, vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                VodrAnimatedVisibility(visible = state.documents.isNotEmpty()) {
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

    VodrScreenScaffold(
        title = "Library",
        modifier = modifier,
        actions = {
            if (hasSavedLibraryData) {
                VodrInlineAction(
                    label = "Clear",
                    onClick = { showClearLibraryDialog = true },
                    icon = Icons.Rounded.DeleteOutline,
                )
            }
            VodrInlineAction(
                label = "Settings",
                onClick = onOpenSettings,
                icon = Icons.Rounded.Settings,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                expanded = true,
                onClick = { showAddSheet = true },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                    )
                },
                text = { Text(text = "Add Book") },
                modifier = Modifier.semantics { contentDescription = "Add a new book" },
            )
        },
    ) { contentPadding ->
        VodrScreenColumn(
            contentPadding = contentPadding,
            fillMaxSize = true,
        ) {
            state.errorMessage?.let {
                VodrMessageText(
                    text = it,
                    tone = VodrMessageTone.ERROR,
                )
            }
            if (hasContinueListening) {
                ContinueListeningCard(
                    documentTitle = continueListeningDocumentTitle!!,
                    documentSourceUri = continueListeningDocumentSourceUri!!,
                    documentMimeType = continueListeningDocumentMimeType!!,
                    chapterTitle = continueListeningChapterTitle,
                    progress = continueListeningProgress,
                    status = continueListeningStatus ?: "Ready",
                    isFavorite = continueListeningIsFavorite,
                    narrationProviderLabel = continueListeningNarrationProviderLabel,
                    onResumePlayback = onResumePlayback,
                    onToggleFavorite = onToggleContinueFavorite,
                )
            }
            if (displayedSessions.isNotEmpty()) {
                SessionShelfSection(
                    title = "Listening",
                    subtitle = null,
                    sessions = displayedSessions,
                    emphasized = displayedSessions.any { it.isFavorite },
                    onOpenSession = onOpenRecentSession,
                    onRemoveSession = onRemoveRecentSession,
                    onToggleFavorite = onToggleRecentSessionFavorite,
                )
            }
            VodrCrossfade(targetState = showEmptyLibraryState, label = "library-empty-list") { isEmpty ->
                if (isEmpty) {
                    EmptyLibraryCard(
                        onAddBook = { showAddSheet = true },
                    )
                } else {
                    if (state.documents.isNotEmpty()) {
                        VodrSectionHeader(
                            title = "Books",
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            items(
                                items = state.documents,
                                key = { it.id },
                            ) { document ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .vodrAnimateContentSize()
                                        .clickable(onClick = onOpenGenerate),
                                    colors = VodrSurfaceStyles.subtleCardColors(),
                                ) {
                                    LibraryDocumentCardContent(
                                        document = document,
                                        onOpenGenerate = onOpenGenerate,
                                        onDelete = { pendingDeleteDocumentId = document.id },
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

data class RecentListeningSessionItem(
    val sessionId: String,
    val documentTitle: String,
    val documentSourceUri: String,
    val documentMimeType: String,
    val chapterTitle: String,
    val progressFraction: Float,
    val updatedAtEpochMs: Long,
    val isFavorite: Boolean = false,
    val personalizationProviderLabel: String? = null,
    val transcriptionProviderLabel: String? = null,
    val narrationProviderLabel: String? = null,
)

@Composable
private fun ContinueListeningCard(
    documentTitle: String,
    documentSourceUri: String,
    documentMimeType: String,
    chapterTitle: String?,
    progress: Float,
    status: String,
    isFavorite: Boolean,
    narrationProviderLabel: String?,
    onResumePlayback: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
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
            VodrArtworkListRow(
                title = documentTitle,
                sourceUri = documentSourceUri,
                mimeType = documentMimeType,
                subtitle = chapterTitle ?: "Resume your latest session",
                supportingText = status,
                artworkWidth = sizes.continueArtworkWidth,
                artworkHeight = sizes.continueArtworkHeight,
                titleTextStyle = MaterialTheme.typography.titleMedium,
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            VodrRuntimeProviderStrip(
                personalizationProviderLabel = null,
                transcriptionProviderLabel = null,
                narrationProviderLabel = narrationProviderLabel,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VodrInlineAction(
                    label = "Resume",
                    onClick = onResumePlayback,
                    icon = Icons.Rounded.PlayArrow,
                )
                VodrChoiceChip(
                    label = "Favorite",
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                    selectedIcon = Icons.Rounded.Star,
                    unselectedIcon = Icons.Rounded.StarBorder,
                )
            }
        }
    }
}

@Composable
private fun FavoriteSessionsSection(
    sessions: List<RecentListeningSessionItem>,
    onOpenSession: (String) -> Unit,
    onRemoveSession: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    SessionShelfSection(
        title = "Favorites",
        subtitle = "Keep your best books one tap away.",
        sessions = sessions,
        emphasized = true,
        onOpenSession = onOpenSession,
        onRemoveSession = onRemoveSession,
        onToggleFavorite = onToggleFavorite,
    )
}

@Composable
private fun RecentSessionsSection(
    sessions: List<RecentListeningSessionItem>,
    onOpenSession: (String) -> Unit,
    onRemoveSession: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    SessionShelfSection(
        title = "Recent listening sessions",
        subtitle = "Jump back into saved books without regenerating.",
        sessions = sessions,
        emphasized = false,
        onOpenSession = onOpenSession,
        onRemoveSession = onRemoveSession,
        onToggleFavorite = onToggleFavorite,
    )
}

@Composable
private fun SessionShelfSection(
    title: String,
    subtitle: String?,
    sessions: List<RecentListeningSessionItem>,
    emphasized: Boolean,
    onOpenSession: (String) -> Unit,
    onRemoveSession: ((String) -> Unit)?,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        VodrSectionHeader(
            title = title,
            subtitle = subtitle,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            itemsIndexed(
                items = sessions,
                key = { _, session -> session.sessionId },
            ) { _, session ->
                Card(
                    modifier = Modifier
                        .width(sizes.libraryShelfCardWidth)
                        .vodrAnimateContentSize()
                        .clickable { onOpenSession(session.sessionId) },
                    colors = VodrSurfaceStyles.shelfCardColors(emphasized = emphasized),
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
                            supportingText = relativeImportedLabel(session.updatedAtEpochMs),
                            artworkWidth = sizes.sessionArtworkWidth,
                            artworkHeight = sizes.sessionArtworkHeight,
                            titleTextStyle = MaterialTheme.typography.titleSmall,
                            subtitleTextStyle = MaterialTheme.typography.bodySmall,
                            supportingTextStyle = MaterialTheme.typography.bodySmall,
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { session.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (session.isFavorite) {
                            VodrMetaChip(
                                label = "Favorite",
                                leadingIcon = Icons.Rounded.Star,
                            )
                        }
                        VodrRuntimeProviderStrip(
                            personalizationProviderLabel = session.personalizationProviderLabel,
                            transcriptionProviderLabel = session.transcriptionProviderLabel,
                            narrationProviderLabel = session.narrationProviderLabel,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            VodrInlineAction(
                                label = "Open",
                                onClick = { onOpenSession(session.sessionId) },
                                icon = Icons.Rounded.PlayArrow,
                            )
                            VodrChoiceChip(
                                label = "Favorite",
                                selected = session.isFavorite,
                                onClick = {
                                    onToggleFavorite(
                                        session.sessionId,
                                        !session.isFavorite,
                                    )
                                },
                                selectedIcon = Icons.Rounded.Star,
                                unselectedIcon = Icons.Rounded.StarBorder,
                            )
                            onRemoveSession?.let { remove ->
                                VodrInlineAction(
                                    label = "Remove",
                                    onClick = { remove(session.sessionId) },
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

@Composable
private fun EmptyLibraryCard(
    onAddBook: () -> Unit,
) {
    VodrEmptyStateCard(
        title = "Start your library",
        message = "Import a PDF or EPUB to create your first listening session.",
        actionLabel = "Import book",
        onAction = onAddBook,
    )
}

@Composable
private fun LibraryDocumentCardContent(
    document: ImportedDocument,
    onOpenGenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VodrUiTheme.spacing
    val sizes = VodrUiTheme.sizes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vodrAnimateContentSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        VodrArtworkListRow(
            title = document.metadata.displayName,
            sourceUri = document.metadata.sourceUri,
            mimeType = document.metadata.mimeType,
            subtitle = buildDocumentSubtitle(document = document),
            artworkWidth = sizes.documentArtworkWidth,
            artworkHeight = sizes.documentArtworkHeight,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            VodrMetaChip(
                label = if (document.metadata.mimeType.contains("epub")) "EPUB" else "PDF",
            )
            VodrMetaChip(
                label = relativeImportedLabel(document.metadata.importedAtEpochMs),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            VodrInlineAction(
                label = "Generate",
                onClick = onOpenGenerate,
                icon = Icons.Rounded.PlayArrow,
            )
            VodrInlineAction(
                label = "Delete",
                onClick = onDelete,
                icon = Icons.Rounded.DeleteOutline,
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

private fun releasePersistedReadPermission(
    sourceUri: String,
    releaseReadPermission: (android.net.Uri) -> Unit,
) {
    val uri = runCatching { android.net.Uri.parse(sourceUri) }.getOrNull() ?: return
    runCatching {
        releaseReadPermission(uri)
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
