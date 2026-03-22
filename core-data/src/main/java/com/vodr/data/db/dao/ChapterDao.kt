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

    @Query("SELECT * FROM chapters WHERE chapterId = :chapterId")
    fun getById(chapterId: Long): ChapterEntity?
}
