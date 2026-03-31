package com.vodr.playback

import com.vodr.data.db.dao.ChapterDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PlaybackRenderRepository @Inject constructor(
    private val chapterDao: ChapterDao,
) {
    suspend fun markChapterRendered(
        chapter: PlaybackChapter,
        renderedAudioPath: String,
        providerLabel: String,
        voicePackId: String?,
        estimatedDurationMs: Long,
    ) {
        val documentId = chapter.id.substringBefore('-').toLongOrNull() ?: return
        val chapterIndex = chapter.id.substringAfter('-', missingDelimiterValue = "")
            .toIntOrNull()
            ?: return
        withContext(Dispatchers.IO) {
            chapterDao.updateRenderInfo(
                documentId = documentId,
                chapterIndex = chapterIndex,
                renderedAudioPath = renderedAudioPath,
                renderStatus = "ready",
                renderedBy = providerLabel,
                voicePackId = voicePackId,
                estimatedDurationMs = estimatedDurationMs,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }
}
