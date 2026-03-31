package com.vodr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "generation_jobs",
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
        Index(value = ["status"]),
    ],
)
data class GenerationJobEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "jobId")
    val jobId: Long = 0L,
    @ColumnInfo(name = "documentId")
    val documentId: Long,
    val jobType: String,
    val chapterIndex: Int = 0,
    val chunkIndex: Int = 0,
    val waveIndex: Int = 0,
    val providerLabel: String = "",
    val status: String,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
