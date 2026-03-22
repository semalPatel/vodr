package com.vodr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["sourceUri"], unique = true),
    ],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "documentId")
    val documentId: Long = 0L,
    val title: String,
    val sourceUri: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
