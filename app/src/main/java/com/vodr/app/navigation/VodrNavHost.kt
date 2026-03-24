package com.vodr.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vodr.generate.GenerationDocumentInput
import com.vodr.generate.GenerationViewModel
import com.vodr.generate.ui.GenerateScreen
import com.vodr.generate.ui.GenerationSourceDocument
import com.vodr.library.ImportedDocument
import com.vodr.library.ui.LibraryScreen
import com.vodr.library.settings.SettingsScreen
import com.vodr.player.ui.PlayerScreen

object VodrNavRoutes {
    const val libraryRoute = "library"
    const val generateRoute = "generate"
    const val playerRoute = "player"
    const val settingsRoute = "settings"

    val routes = listOf(libraryRoute, generateRoute, playerRoute, settingsRoute)
    const val startDestination = libraryRoute
}

@Composable
fun VodrNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val importedDocuments = remember { mutableStateListOf<ImportedDocument>() }
    val generationViewModel: GenerationViewModel = viewModel()
    val generationState by generationViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = VodrNavRoutes.startDestination,
    ) {
        composable(VodrNavRoutes.libraryRoute) {
            LibraryScreen(
                onDocumentImported = { document ->
                    importedDocuments.add(document)
                },
                onOpenGenerate = {
                    navController.navigate(VodrNavRoutes.generateRoute)
                },
                onOpenSettings = {
                    navController.navigate(VodrNavRoutes.settingsRoute)
                },
            )
        }
        composable(VodrNavRoutes.generateRoute) {
            GenerateScreen(
                generationState = generationState,
                documents = importedDocuments.map {
                    GenerationSourceDocument(
                        id = it.id.toString(),
                        displayName = it.metadata.displayName,
                    )
                },
                onGenerateRequested = { documentId, mode ->
                    val imported = importedDocuments.firstOrNull { it.id.toString() == documentId }
                        ?: return@GenerateScreen
                    generationViewModel.generate(
                        document = GenerationDocumentInput(
                            id = imported.id.toString(),
                            displayName = imported.metadata.displayName,
                            sourceUri = imported.metadata.sourceUri,
                            mimeType = imported.metadata.mimeType,
                        ),
                        mode = mode,
                        openInputStream = { input ->
                            val uri = Uri.parse(input.sourceUri)
                            context.contentResolver.openInputStream(uri)
                        },
                    )
                },
                onOpenPlayer = {
                    navController.navigate(VodrNavRoutes.playerRoute)
                },
            )
        }
        composable(VodrNavRoutes.playerRoute) {
            PlayerScreen(queue = generationState.queue)
        }
        composable(VodrNavRoutes.settingsRoute) {
            SettingsScreen()
        }
    }
}
