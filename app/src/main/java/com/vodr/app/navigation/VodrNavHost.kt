package com.vodr.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.PersonalizationPreferences
import com.vodr.generate.GenerationDocumentInput
import com.vodr.generate.GenerationViewModel
import com.vodr.generate.ui.GenerateScreen
import com.vodr.generate.ui.GenerationSourceDocument
import com.vodr.library.LibraryViewModel
import com.vodr.library.ui.LibraryScreen
import com.vodr.library.settings.SettingsScreen
import com.vodr.library.settings.SettingsUiState
import com.vodr.library.settings.SettingsViewModel
import com.vodr.player.ui.PlayerScreen

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
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val generationViewModel: GenerationViewModel = viewModel()
    val generationState by generationViewModel.state.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = VodrNavRoutes.startDestination,
    ) {
        composable(VodrRoute.Library.route) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenGenerate = {
                    navController.navigateTo(VodrRoute.Generate)
                },
                onOpenSettings = {
                    navController.navigateTo(VodrRoute.Settings)
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
            PlayerScreen(queue = generationState.queue)
        }
        composable(VodrRoute.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
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
