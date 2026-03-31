package com.vodr.generate

import com.vodr.data.db.dao.ChapterDao
import com.vodr.data.db.dao.ChunkDao
import com.vodr.data.db.dao.GenerationJobDao
import com.vodr.data.db.entity.ChapterEntity
import com.vodr.data.db.entity.ChunkEntity
import com.vodr.data.db.entity.GenerationJobEntity
import com.vodr.playback.PlaybackChapter
import com.vodr.segmentation.SegmentedChunk
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class GenerationPersistenceRepository @Inject constructor(
    private val chapterDao: ChapterDao,
    private val chunkDao: ChunkDao,
    private val generationJobDao: GenerationJobDao,
) {
    suspend fun persistPlan(
        documentId: Long,
        queue: List<PlaybackChapter>,
        chunks: List<SegmentedChunk>,
        jobs: List<GenerationJob>,
        runtimeSummary: GenerationRuntimeSummary,
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            generationJobDao.deleteByDocumentId(documentId)
            chunkDao.deleteByDocumentId(documentId)
            chapterDao.deleteByDocumentId(documentId)

            val chapterIds = chapterDao.insertAll(
                queue.mapIndexed { index, chapter ->
                    ChapterEntity(
                        documentId = documentId,
                        indexInDocument = index,
                        title = chapter.title,
                        sourceText = chapter.text,
                        renderStatus = "pending",
                        renderedBy = runtimeSummary.personalizationProvider.name,
                        estimatedDurationMs = estimateDurationMs(chapter.text),
                        updatedAtEpochMs = now,
                    )
                },
            )

            val chapterIdByIndex = chapterIds.map { it.toLong() }
            chunkDao.insertAll(
                chunks.map { chunk ->
                    ChunkEntity(
                        documentId = documentId,
                        chapterId = chapterIdByIndex[chunk.chapterIndex],
                        indexInChapter = chunk.chunkIndex,
                        text = chunk.text,
                        startOffset = chunk.startOffset,
                        endOffset = chunk.endOffset,
                        narrationStyle = chunk.narrationStyle,
                        pauseAfterMs = chunk.pauseAfterMs,
                        renderStatus = "pending",
                    )
                },
            )

            generationJobDao.insertAll(
                jobs.map { job ->
                    GenerationJobEntity(
                        documentId = documentId,
                        jobType = "narration-render",
                        chapterIndex = chunks.getOrNull(job.chunkIndex)?.chapterIndex ?: 0,
                        chunkIndex = job.chunkIndex,
                        waveIndex = job.waveIndex,
                        providerLabel = runtimeSummary.transcriptionProvider.name,
                        status = "pending",
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    )
                },
            )
        }
    }
}

private fun estimateDurationMs(text: String): Long {
    val charsPerSecond = 13f
    return ((text.length / charsPerSecond) * 1_000f).toLong().coerceAtLeast(1_000L)
}
