package com.vodr.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.PersonalizationPreferences
import com.vodr.ai.toDisplayName
import com.vodr.generate.GenerationDocumentInput
import com.vodr.generate.GenerationViewModel
import com.vodr.generate.ui.GenerateScreen
import com.vodr.generate.ui.GenerationSourceDocument
import com.vodr.library.LibraryViewModel
import com.vodr.library.ui.RecentListeningSessionItem
import com.vodr.library.ui.LibraryScreen
import com.vodr.library.settings.SettingsScreen
import com.vodr.library.settings.SettingsUiState
import com.vodr.library.settings.SettingsViewModel
import com.vodr.playback.PlaybackDocument
import com.vodr.playback.PlaybackRuntimeMetadata
import com.vodr.playback.PlaybackStatus
import com.vodr.player.PlayerViewModel
import com.vodr.player.ui.PlayerScreen
import com.vodr.ui.VodrAppShell
import com.vodr.ui.VodrMiniPlayerCard
import com.vodr.ui.theme.VodrUiTheme

sealed interface VodrRoute {
    val route: String

    data object Library : VodrRoute { override val route: String = "library" }
    data object Generate : VodrRoute { override val route: String = "generate" }
    data object Player : VodrRoute { override val route: String = "player" }
    data object Settings : VodrRoute { override val route: String = "settings" }
}

object VodrNavRoutes {
    val routes = listOf(
        VodrRoute.Library.route,
        VodrRoute.Generate.route,
        VodrRoute.Player.route,
        VodrRoute.Settings.route,
    )
    const val startDestination = "library"
}

private fun NavHostController.navigateTo(
    route: VodrRoute,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route.route, builder)
}

@Composable
fun VodrNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val spacing = VodrUiTheme.spacing
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val generationViewModel: GenerationViewModel = viewModel()
    val generationState by generationViewModel.state.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val playerViewModel: PlayerViewModel = viewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showMiniPlayer = playerState.queue.isNotEmpty() && currentRoute != VodrRoute.Player.route
    val playbackProgress = if (playerState.currentChapterDurationMs > 0L) {
        (playerState.resumePositionMs.toFloat() / playerState.currentChapterDurationMs.toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(generationState.queue) {
        if (generationState.queue.isNotEmpty()) {
            playerViewModel.updateQueue(
                queue = generationState.queue,
                activeDocument = generationState.activeDocument?.toPlaybackDocument(),
                runtimeMetadata = generationState.runtimeSummary?.toPlaybackRuntimeMetadata(),
            )
        }
    }

    VodrAppShell(
        bottomBar = {
            if (showMiniPlayer) {
                VodrMiniPlayerCard(
                    documentTitle = playerState.activeDocument?.title.orEmpty(),
                    documentSourceUri = playerState.activeDocument?.sourceUri,
                    documentMimeType = playerState.activeDocument?.mimeType,
                    chapterTitle = playerState.currentChapter?.title.orEmpty(),
                    progress = playbackProgress,
                    status = playerState.errorMessage ?: playerState.playbackStatus.toMiniPlayerLabel(),
                    narrationProviderLabel = playerState.runtimeMetadata?.narrationProviderLabel,
                    isPlaying = playerState.playbackStatus == PlaybackStatus.PLAYING ||
                        playerState.playbackStatus == PlaybackStatus.PREPARING,
                    onOpenPlayer = {
                        navController.navigateTo(VodrRoute.Player)
                    },
                    onTogglePlayback = playerViewModel::togglePlayback,
                    onSkipNext = playerViewModel::goToNextChapter,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            horizontal = spacing.miniPlayerInsetHorizontal,
                            vertical = spacing.miniPlayerInsetVertical,
                        ),
                )
            }
        },
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = VodrNavRoutes.startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = shellPadding.calculateBottomPadding() +
                        if (showMiniPlayer) spacing.bottomBarClearance else 0.dp,
                ),
        ) {
            composable(VodrRoute.Library.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    continueListeningDocumentTitle = playerState.activeDocument?.title,
                    continueListeningDocumentSourceUri = playerState.activeDocument?.sourceUri,
                    continueListeningDocumentMimeType = playerState.activeDocument?.mimeType,
                    continueListeningChapterTitle = playerState.currentChapter?.title,
                    continueListeningProgress = playbackProgress,
                    continueListeningStatus = playerState.errorMessage ?: playerState.playbackStatus.toMiniPlayerLabel(),
                    continueListeningIsFavorite = playerState.sessionHistory.firstOrNull()?.isFavorite == true,
                    continueListeningNarrationProviderLabel = playerState.runtimeMetadata?.narrationProviderLabel,
                    recentSessions = playerState.sessionHistory.drop(1).map { it.toRecentListeningSessionItem() },
                    onOpenGenerate = {
                        navController.navigateTo(VodrRoute.Generate)
                    },
                    onOpenSettings = {
                        navController.navigateTo(VodrRoute.Settings)
                    },
                    onResumePlayback = {
                        navController.navigateTo(VodrRoute.Player)
                    },
                    onToggleContinueFavorite = {
                        playerState.sessionHistory.firstOrNull()?.let { session ->
                            playerViewModel.setSessionFavorite(
                                sessionId = session.sessionId,
                                isFavorite = !session.isFavorite,
                            )
                        }
                    },
                    onOpenRecentSession = { sessionId ->
                        playerViewModel.restoreSession(sessionId)
                        navController.navigateTo(VodrRoute.Player)
                    },
                    onRemoveRecentSession = playerViewModel::removeSession,
                    onToggleRecentSessionFavorite = { sessionId, isFavorite ->
                        playerViewModel.setSessionFavorite(
                            sessionId = sessionId,
                            isFavorite = isFavorite,
                        )
                    },
                    onDeleteLibraryDocumentData = { sourceUri ->
                        if (generationState.activeDocument?.sourceUri == sourceUri) {
                            generationViewModel.clearQueue()
                        }
                        playerViewModel.removeDocumentSessions(sourceUri)
                    },
                    onClearLibraryData = {
                        generationViewModel.clearQueue()
                        playerViewModel.clearAll()
                    },
                )
            }
            composable(VodrRoute.Generate.route) {
                GenerateScreen(
                    generationState = generationState,
                    documents = libraryState.documents.map {
                        GenerationSourceDocument(
                            id = it.id.toString(),
                            displayName = it.metadata.displayName,
                        )
                    },
                    onGenerateRequested = { documentId, mode ->
                        val imported = libraryState.documents.firstOrNull { it.id.toString() == documentId }
                            ?: return@GenerateScreen
                        generationViewModel.generate(
                            document = GenerationDocumentInput(
                                id = imported.id.toString(),
                                displayName = imported.metadata.displayName,
                                sourceUri = imported.metadata.sourceUri,
                                mimeType = imported.metadata.mimeType,
                            ),
                            mode = mode,
                            personalizationPreferences = settingsState.toPersonalizationPreferences(),
                            openInputStream = { input ->
                                val uri = Uri.parse(input.sourceUri)
                                context.contentResolver.openInputStream(uri)
                            },
                        )
                    },
                    onOpenPlayer = {
                        navController.navigateTo(VodrRoute.Player)
                    },
                )
            }
            composable(VodrRoute.Player.route) {
                PlayerScreen(
                    viewModel = playerViewModel,
                )
            }
            composable(VodrRoute.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}

private fun SettingsUiState.toPersonalizationPreferences(): PersonalizationPreferences {
    return PersonalizationPreferences(
        providerType = personalizationProviderType,
        customProviderConfig = CustomProviderConfig(
            localModelPath = customLocalModelPath,
            localEndpoint = customEndpoint,
            modelName = customModelName,
        ),
        offlineOnly = offlineOnly,
    )
}

private fun com.vodr.generate.GeneratedDocumentSummary.toPlaybackDocument(): PlaybackDocument {
    return PlaybackDocument(
        title = displayName,
        sourceUri = sourceUri,
        mimeType = mimeType,
    )
}

private fun com.vodr.generate.GenerationRuntimeSummary.toPlaybackRuntimeMetadata(): PlaybackRuntimeMetadata {
    return PlaybackRuntimeMetadata(
        personalizationProviderLabel = personalizationProvider.toDisplayName(),
        personalizationDetail = personalizationDetail,
        transcriptionProviderLabel = transcriptionProvider.toDisplayName(),
        transcriptionDetail = transcriptionDetail,
    )
}

private fun com.vodr.playback.PlaybackSessionSummary.toRecentListeningSessionItem(): RecentListeningSessionItem {
    return RecentListeningSessionItem(
        sessionId = sessionId,
        documentTitle = documentTitle,
        documentSourceUri = documentSourceUri,
        documentMimeType = documentMimeType,
        chapterTitle = chapterTitle,
        progressFraction = progressFraction,
        updatedAtEpochMs = updatedAtEpochMs,
        isFavorite = isFavorite,
        personalizationProviderLabel = personalizationProviderLabel,
        transcriptionProviderLabel = transcriptionProviderLabel,
        narrationProviderLabel = narrationProviderLabel,
    )
}

private fun PlaybackStatus.toMiniPlayerLabel(): String {
    return when (this) {
        PlaybackStatus.IDLE -> "Ready"
        PlaybackStatus.PREPARING -> "Preparing voice"
        PlaybackStatus.PLAYING -> "Speaking now"
        PlaybackStatus.PAUSED -> "Paused"
        PlaybackStatus.ERROR -> "Playback issue"
    }
}
