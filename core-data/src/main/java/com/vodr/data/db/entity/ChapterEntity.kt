package com.vodr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chapterId")
    val chapterId: Long = 0L,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    val indexInDocument: Int,
    val title: String,
    val sourceText: String = "",
    val renderedAudioPath: String? = null,
    val renderStatus: String = "pending",
    val renderedBy: String? = null,
    val voicePackId: String? = null,
    val estimatedDurationMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)
