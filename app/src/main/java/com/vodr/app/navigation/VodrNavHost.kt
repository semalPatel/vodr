package com.vodr.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vodr.ai.DefaultDeviceCapabilityDetector
import com.vodr.ai.PersonalizationRouter
import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer
import com.vodr.generate.ui.GenerateScreen
import com.vodr.generate.ui.GenerationSourceDocument
import com.vodr.library.ImportedDocument
import com.vodr.library.ui.LibraryScreen
import com.vodr.library.settings.SettingsScreen
import com.vodr.parser.DocumentParser
import com.vodr.playback.PlaybackChapter
import com.vodr.player.ui.PlayerScreen
import com.vodr.segmentation.ChunkPolicy
import com.vodr.segmentation.Segmenter

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
    val generatedQueue = remember { mutableStateListOf<PlaybackChapter>() }

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
            )
        }
        composable(VodrNavRoutes.generateRoute) {
            GenerateScreen(
                documents = importedDocuments.map {
                    GenerationSourceDocument(
                        id = it.id.toString(),
                        displayName = it.metadata.displayName,
                    )
                },
                onGenerateRequested = { documentId, mode ->
                    val imported = importedDocuments.firstOrNull { it.id.toString() == documentId }
                        ?: return@GenerateScreen
                    val uri = Uri.parse(imported.metadata.sourceUri)
                    val stream = context.contentResolver.openInputStream(uri) ?: return@GenerateScreen
                    stream.use { input ->
                        val parser = DocumentParser()
                        val parsed = parser.parse(
                            inputStream = input,
                            mimeType = imported.metadata.mimeType,
                        )
                        val chapters = toChapterTexts(parsed.text, parsed.chapters.map { it.startOffset })
                        val chunks = Segmenter(ChunkPolicy(maxCharsPerChunk = 400)).segment(
                            documentId = imported.id.toString(),
                            chapters = chapters,
                        )
                        val promptBuilder = PersonalizationRouter(
                            deviceCapabilityDetector = DefaultDeviceCapabilityDetector(),
                            aICorePersonalizer = AICorePersonalizer(),
                            mediaPipePersonalizer = MediaPipePersonalizer(),
                        ).select()
                        generatedQueue.clear()
                        generatedQueue.addAll(
                            chapters.indices.map { chapterIndex ->
                                val chapterChunkCount = chunks.count { it.chapterIndex == chapterIndex }
                                val chapterPreview = chapters[chapterIndex].take(60)
                                val prompt = promptBuilder.buildPrompt(
                                    inputText = chapterPreview,
                                    tone = "neutral",
                                    style = mode.name.lowercase(),
                                )
                                PlaybackChapter(
                                    id = "${imported.id}-$chapterIndex",
                                    title = "Chapter ${chapterIndex + 1} (${chapterChunkCount} chunks) ${prompt.take(24)}",
                                )
                            },
                        )
                    }
                },
                onOpenPlayer = {
                    navController.navigate(VodrNavRoutes.playerRoute)
                },
            )
        }
        composable(VodrNavRoutes.playerRoute) {
            PlayerScreen(queue = generatedQueue)
        }
        composable(VodrNavRoutes.settingsRoute) {
            SettingsScreen()
        }
    }
}

private fun toChapterTexts(fullText: String, starts: List<Int>): List<String> {
    if (starts.isEmpty()) {
        return listOf(fullText)
    }
    val sorted = starts.distinct().sorted().filter { it in fullText.indices }
    if (sorted.isEmpty()) {
        return listOf(fullText)
    }
    return sorted.mapIndexed { index, start ->
        val end = if (index + 1 < sorted.size) sorted[index + 1] else fullText.length
        fullText.substring(start, end).trim()
    }.filter { it.isNotBlank() }
}
