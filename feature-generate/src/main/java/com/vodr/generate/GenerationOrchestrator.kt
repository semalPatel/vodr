package com.vodr.generate

import com.vodr.ai.DefaultDeviceCapabilityDetector
import com.vodr.ai.OfflineHeuristicRuntimeProbe
import com.vodr.ai.PersonalizationPreferences
import com.vodr.ai.PersonalizationProbeRegistry
import com.vodr.ai.PersonalizationRouter
import com.vodr.ai.MediaPipeRuntimeProbe
import com.vodr.ai.AICoreRuntimeProbe
import com.vodr.ai.CustomEndpointRuntimeProbe
import com.vodr.ai.CustomLocalModelRuntimeProbe
import com.vodr.ai.TranscriptionRouter
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

data class GenerationRuntimeSummary(
    val personalizationProvider: com.vodr.ai.PersonalizationProviderType,
    val personalizationDetail: String? = null,
    val transcriptionProvider: com.vodr.ai.PersonalizationProviderType,
    val transcriptionDetail: String? = null,
)

data class GenerationOutput(
    val queue: List<PlaybackChapter>,
    val runtimeSummary: GenerationRuntimeSummary,
)

class GenerationOrchestrator(
    private val parser: DocumentParser = DocumentParser(),
    private val segmenter: Segmenter = Segmenter(policy = ChunkPolicy(maxCharsPerChunk = 400)),
    private val deviceCapabilityDetector: DefaultDeviceCapabilityDetector = DefaultDeviceCapabilityDetector(),
    private val probeRegistry: PersonalizationProbeRegistry = PersonalizationProbeRegistry(
        probes = listOf(
            AICoreRuntimeProbe(deviceCapabilityDetector),
            MediaPipeRuntimeProbe(deviceCapabilityDetector),
            CustomLocalModelRuntimeProbe(),
            CustomEndpointRuntimeProbe(),
            OfflineHeuristicRuntimeProbe(),
        ),
    ),
    private val personalizationRouter: PersonalizationRouter = PersonalizationRouter(
        deviceCapabilityDetector = deviceCapabilityDetector,
        aICorePersonalizer = AICorePersonalizer(),
        mediaPipePersonalizer = MediaPipePersonalizer(),
        customLocalModelPersonalizer = CustomLocalModelPersonalizer(),
        customEndpointPersonalizer = CustomEndpointPersonalizer(),
        heuristicPersonalizer = HeuristicPersonalizer(),
        probeRegistry = probeRegistry,
    ),
    private val transcriptionRouter: TranscriptionRouter = TranscriptionRouter(
        probeRegistry = probeRegistry,
    ),
) {
    fun buildPlaybackQueue(
        document: GenerationDocumentInput,
        mode: GenerationMode,
        personalizationPreferences: PersonalizationPreferences = PersonalizationPreferences(),
        inputStream: InputStream,
    ): GenerationOutput {
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
        val personalizationSelection = personalizationRouter.resolve(
            preferences = personalizationPreferences,
        )
        val transcriptionSelection = transcriptionRouter.resolve(
            preferences = personalizationPreferences,
        )
        val promptBuilder = personalizationRouter.select(
            preferences = personalizationPreferences,
        )
        val transcriptionEngine = transcriptionRouter.select(
            preferences = personalizationPreferences,
        )
        val queue = chapterTexts.indices.map { chapterIndex ->
            val chapterChunkCount = chunks.count { it.chapterIndex == chapterIndex }
            val chapterPreview = chapterTexts[chapterIndex].take(60)
            val prompt = promptBuilder.buildPrompt(
                inputText = chapterPreview,
                tone = "neutral",
                style = mode.name.lowercase(),
                customProviderConfig = personalizationPreferences.customProviderConfig,
            )
            val transcriptLabel = transcriptionEngine.transcribe(
                sourceText = chapterTexts[chapterIndex].take(240),
                customProviderConfig = personalizationPreferences.customProviderConfig,
            )
            PlaybackChapter(
                id = "${document.id}-$chapterIndex",
                title = "Chapter ${chapterIndex + 1}: ${transcriptLabel.take(24)} (${chapterChunkCount} chunks) ${prompt.take(16)}",
                text = chapterTexts[chapterIndex],
            )
        }
        return GenerationOutput(
            queue = queue,
            runtimeSummary = GenerationRuntimeSummary(
                personalizationProvider = personalizationSelection.providerType,
                personalizationDetail = personalizationSelection.detail,
                transcriptionProvider = transcriptionSelection.providerType,
                transcriptionDetail = transcriptionSelection.detail,
            ),
        )
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
