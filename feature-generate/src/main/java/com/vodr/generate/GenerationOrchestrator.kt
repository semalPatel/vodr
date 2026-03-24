package com.vodr.generate

import com.vodr.ai.DefaultDeviceCapabilityDetector
import com.vodr.ai.PersonalizationPreferences
import com.vodr.ai.PersonalizationRouter
import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.CustomEndpointPersonalizer
import com.vodr.ai.provider.CustomLocalModelPersonalizer
import com.vodr.ai.provider.HeuristicPersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer
import com.vodr.parser.DocumentParser
import com.vodr.playback.PlaybackChapter
import com.vodr.segmentation.ChunkPolicy
import com.vodr.segmentation.Segmenter
import java.io.InputStream

data class GenerationDocumentInput(
    val id: String,
    val displayName: String,
    val sourceUri: String,
    val mimeType: String,
)

class GenerationOrchestrator(
    private val parser: DocumentParser = DocumentParser(),
    private val segmenter: Segmenter = Segmenter(policy = ChunkPolicy(maxCharsPerChunk = 400)),
    private val personalizationRouter: PersonalizationRouter = PersonalizationRouter(
        deviceCapabilityDetector = DefaultDeviceCapabilityDetector(),
        aICorePersonalizer = AICorePersonalizer(),
        mediaPipePersonalizer = MediaPipePersonalizer(),
        customLocalModelPersonalizer = CustomLocalModelPersonalizer(),
        customEndpointPersonalizer = CustomEndpointPersonalizer(),
        heuristicPersonalizer = HeuristicPersonalizer(),
    ),
) {
    fun buildPlaybackQueue(
        document: GenerationDocumentInput,
        mode: GenerationMode,
        personalizationPreferences: PersonalizationPreferences = PersonalizationPreferences(),
        inputStream: InputStream,
    ): List<PlaybackChapter> {
        val parsed = parser.parse(
            inputStream = inputStream,
            mimeType = document.mimeType,
        )
        val chapterTexts = toChapterTexts(
            fullText = parsed.text,
            starts = parsed.chapters.map { it.startOffset },
        )
        val chunks = segmenter.segment(
            documentId = document.id,
            chapters = chapterTexts,
        )
        val promptBuilder = personalizationRouter.select(
            preferences = personalizationPreferences,
        )
        return chapterTexts.indices.map { chapterIndex ->
            val chapterChunkCount = chunks.count { it.chapterIndex == chapterIndex }
            val chapterPreview = chapterTexts[chapterIndex].take(60)
            val prompt = promptBuilder.buildPrompt(
                inputText = chapterPreview,
                tone = "neutral",
                style = mode.name.lowercase(),
            )
            PlaybackChapter(
                id = "${document.id}-$chapterIndex",
                title = "Chapter ${chapterIndex + 1} (${chapterChunkCount} chunks) ${prompt.take(24)}",
                text = chapterTexts[chapterIndex],
            )
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
}
