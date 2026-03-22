package com.vodr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vodr.data.db.entity.GenerationJobEntity

@Dao
interface GenerationJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(job: GenerationJobEntity): Long

    @Query("SELECT * FROM generation_jobs WHERE jobId = :jobId")
    fun getById(jobId: Long): GenerationJobEntity?
}
