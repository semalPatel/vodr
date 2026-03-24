package com.vodr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vodr.data.db.dao.ChapterDao
import com.vodr.data.db.dao.ChunkDao
import com.vodr.data.db.dao.DocumentDao
import com.vodr.data.db.dao.GenerationJobDao
import com.vodr.data.db.dao.UserSettingsDao
import com.vodr.data.db.entity.ChapterEntity
import com.vodr.data.db.entity.ChunkEntity
import com.vodr.data.db.entity.DocumentEntity
import com.vodr.data.db.entity.GenerationJobEntity
import com.vodr.data.db.entity.UserSettingsEntity

@Database(
    entities = [
        DocumentEntity::class,
        ChapterEntity::class,
        ChunkEntity::class,
        GenerationJobEntity::class,
        UserSettingsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class VodrDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chunkDao(): ChunkDao
    abstract fun generationJobDao(): GenerationJobDao
    abstract fun userSettingsDao(): UserSettingsDao
}
