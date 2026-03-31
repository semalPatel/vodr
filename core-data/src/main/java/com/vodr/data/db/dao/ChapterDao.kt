package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.ChapterEntity

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Query("SELECT * FROM chapters WHERE chapterId = :chapterId")
    fun getById(chapterId: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE documentId = :documentId ORDER BY indexInDocument ASC")
    fun getByDocumentId(documentId: Long): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE documentId = :documentId")
    fun deleteByDocumentId(documentId: Long)

    @Query("""
        UPDATE chapters
        SET renderedAudioPath = :renderedAudioPath,
            renderStatus = :renderStatus,
            renderedBy = :renderedBy,
            voicePackId = :voicePackId,
            estimatedDurationMs = :estimatedDurationMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE documentId = :documentId AND indexInDocument = :chapterIndex
    """)
    fun updateRenderInfo(
        documentId: Long,
        chapterIndex: Int,
        renderedAudioPath: String?,
        renderStatus: String,
        renderedBy: String?,
        voicePackId: String?,
        estimatedDurationMs: Long,
        updatedAtEpochMs: Long,
    )
}
