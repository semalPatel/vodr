package com.vodr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["chapterId"]),
    ],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chunkId")
    val chunkId: Long = 0L,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    @ColumnInfo(name = "chapterId")
    val chapterId: Long,
    val indexInChapter: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val narrationStyle: String = "NEUTRAL",
    val pauseAfterMs: Long = 0L,
    val renderedAudioPath: String? = null,
    val renderStatus: String = "pending",
)
