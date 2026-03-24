package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity): Long

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun getById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun getBySourceUri(sourceUri: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY importedAtEpochMs DESC, updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DocumentEntity>>
}
