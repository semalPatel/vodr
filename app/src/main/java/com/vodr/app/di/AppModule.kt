package com.vodr.app.di

import android.content.Context
import androidx.room.Room
import com.vodr.data.db.VodrDatabase
import com.vodr.data.db.dao.ChapterDao
import com.vodr.data.db.dao.ChunkDao
import com.vodr.data.db.dao.DocumentDao
import com.vodr.data.db.dao.GenerationJobDao
import com.vodr.data.db.dao.UserSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideVodrDatabase(
        @ApplicationContext context: Context,
    ): VodrDatabase {
        return Room.databaseBuilder(
            context,
            VodrDatabase::class.java,
            APP_DATABASE_NAME,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideDocumentDao(database: VodrDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideChapterDao(database: VodrDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideChunkDao(database: VodrDatabase): ChunkDao = database.chunkDao()

    @Provides
    fun provideGenerationJobDao(database: VodrDatabase): GenerationJobDao = database.generationJobDao()

    @Provides
    fun provideUserSettingsDao(database: VodrDatabase): UserSettingsDao = database.userSettingsDao()
}

private const val APP_DATABASE_NAME = "vodr.db"
