package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.ChunkEntity

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chunk: ChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(chunks: List<ChunkEntity>): List<Long>

    @Query("SELECT * FROM chunks WHERE chunkId = :chunkId")
    fun getById(chunkId: Long): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY chapterId ASC, indexInChapter ASC")
    fun getByDocumentId(documentId: Long): List<ChunkEntity>

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    fun deleteByDocumentId(documentId: Long)
}
