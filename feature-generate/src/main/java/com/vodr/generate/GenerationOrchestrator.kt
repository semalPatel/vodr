package com.vodr.generate

import com.vodr.ai.DefaultDeviceCapabilityDetector
import com.vodr.ai.OfflineHeuristicRuntimeProbe
import com.vodr.ai.PersonalizationPreferences
import com.vodr.ai.PersonalizationProbeRegistry
import com.vodr.ai.PersonalizationRouter
import com.vodr.ai.MediaPipeRuntimeProbe
import com.vodr.ai.AICoreRuntimeProbe
import com.vodr.ai.ChapterLabelRouter
import com.vodr.ai.CustomEndpointRuntimeProbe
import com.vodr.ai.CustomLocalModelRuntimeProbe
import com.vodr.ai.provider.AICorePersonalizer
import com.vodr.ai.provider.CustomEndpointPersonalizer
import com.vodr.ai.provider.CustomLocalModelPersonalizer
import com.vodr.ai.provider.HeuristicPersonalizer
import com.vodr.ai.provider.MediaPipePersonalizer
import com.vodr.parser.DocumentParser
import com.vodr.parser.ParsedDocument
import com.vodr.playback.PlaybackChapter
import com.vodr.segmentation.ChunkPolicy
import com.vodr.segmentation.SegmentedChunk
import com.vodr.segmentation.Segmenter
import java.io.InputStream
import javax.inject.Inject

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
    val segments: List<SegmentedChunk>,
    val renderJobs: List<GenerationJob>,
)

class GenerationOrchestrator @Inject constructor() {
    private val parser = DocumentParser()
    private val segmenter = Segmenter(policy = ChunkPolicy(maxCharsPerChunk = 400))
    private val generationWorker = GenerationWorker()
    private val deviceCapabilityDetector = DefaultDeviceCapabilityDetector()
    private val probeRegistry = PersonalizationProbeRegistry(
        probes = listOf(
            AICoreRuntimeProbe(deviceCapabilityDetector),
            MediaPipeRuntimeProbe(deviceCapabilityDetector),
            CustomLocalModelRuntimeProbe(),
            CustomEndpointRuntimeProbe(),
            OfflineHeuristicRuntimeProbe(),
        ),
    )
    private val personalizationRouter = PersonalizationRouter(
        deviceCapabilityDetector = deviceCapabilityDetector,
        aICorePersonalizer = AICorePersonalizer(),
        mediaPipePersonalizer = MediaPipePersonalizer(),
        customLocalModelPersonalizer = CustomLocalModelPersonalizer(),
        customEndpointPersonalizer = CustomEndpointPersonalizer(),
        heuristicPersonalizer = HeuristicPersonalizer(),
        probeRegistry = probeRegistry,
    )
    private val chapterLabelRouter = ChapterLabelRouter(
        probeRegistry = probeRegistry,
    )

    fun buildPlaybackQueue(
        document: GenerationDocumentInput,
        mode: GenerationMode,
        personalizationPreferences: PersonalizationPreferences = PersonalizationPreferences(),
        inputStream: InputStream,
        onProgress: (GenerationPhase) -> Unit = {},
    ): GenerationOutput {
        onProgress(GenerationPhase.PARSING_DOCUMENT)
        val parsed = parser.parse(
            inputStream = inputStream,
            mimeType = document.mimeType,
        )
        onProgress(GenerationPhase.SEGMENTING_CONTENT)
        val chapterTexts = toChapterTexts(
            fullText = parsed.text,
            starts = parsed.chapters.map { it.startOffset },
        )
        if (chapterTexts.isEmpty()) {
            throw IllegalStateException("No readable chapters were found in the selected document.")
        }
        val segmentedChunks = segmenter.segment(
            documentId = document.id,
            chapters = chapterTexts,
        )
        val chapterTitles = toChapterTitles(
            parsed = parsed,
            chapterTexts = chapterTexts,
        )
        onProgress(GenerationPhase.RESOLVING_PROVIDERS)
        val personalizationSelection = personalizationRouter.resolve(
            preferences = personalizationPreferences,
        )
        val transcriptionSelection = chapterLabelRouter.resolve(
            preferences = personalizationPreferences,
        )
        onProgress(GenerationPhase.BUILDING_QUEUE)
        val queue = chapterTexts.indices.map { chapterIndex ->
            PlaybackChapter(
                id = "${document.id}-$chapterIndex",
                title = chapterTitles[chapterIndex],
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
            segments = segmentedChunks,
            renderJobs = generationWorker.schedule(
                documentId = document.id,
                mode = mode,
                chunkCount = segmentedChunks.size,
            ),
        )
    }

    private fun toChapterTexts(fullText: String, starts: List<Int>): List<String> {
        if (starts.isEmpty()) {
            return listOf(fullText).filter { it.isNotBlank() }
        }
        val sorted = starts.distinct().sorted().filter { it in fullText.indices }
        if (sorted.isEmpty()) {
            return listOf(fullText).filter { it.isNotBlank() }
        }
        return sorted.mapIndexed { index, start ->
            val end = if (index + 1 < sorted.size) sorted[index + 1] else fullText.length
            fullText.substring(start, end).trim()
        }.filter { it.isNotBlank() }
    }

    private fun toChapterTitles(
        parsed: ParsedDocument,
        chapterTexts: List<String>,
    ): List<String> {
        val markerTitles = parsed.chapters
            .sortedBy { it.startOffset }
            .distinctBy { it.startOffset }
            .map { it.title.sanitizeVisibleChapterTitle() }
        return chapterTexts.mapIndexed { index, chapterText ->
            buildReadableChapterTitle(
                explicitTitle = markerTitles.getOrNull(index),
                chapterText = chapterText,
                chapterIndex = index,
            )
        }
    }
}

internal fun buildReadableChapterTitle(
    explicitTitle: String?,
    chapterText: String,
    chapterIndex: Int,
): String {
    explicitTitle?.sanitizeVisibleChapterTitle()?.let { return it }
    val fallbackLabel = deriveFallbackChapterLabel(chapterText)
    return if (fallbackLabel == null) {
        "Section ${chapterIndex + 1}"
    } else {
        "Section ${chapterIndex + 1}: $fallbackLabel"
    }
}

internal fun deriveFallbackChapterLabel(chapterText: String): String? {
    val firstLine = chapterText.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val firstSentence = firstLine
        .split('.', '!', '?')
        .firstOrNull()
        .orEmpty()
        .trim()
        .ifBlank { firstLine }
    return firstSentence
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(8)
        .joinToString(" ")
        .sanitizeVisibleChapterTitle()
        ?.takeIf { it.isNotBlank() }
}

private fun String.sanitizeVisibleChapterTitle(): String? {
    return trim()
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ',', ';', ':', '-', '•')
        .take(72)
        .takeIf { it.isNotBlank() }
}
