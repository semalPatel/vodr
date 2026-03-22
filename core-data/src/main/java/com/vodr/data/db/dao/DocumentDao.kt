package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.DocumentEntity

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(document: DocumentEntity): Long

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    fun getById(documentId: Long): DocumentEntity?
}
