package com.vodr.library.di

import com.vodr.library.DocumentMetadataRepository
import com.vodr.library.RoomDocumentMetadataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryDataModule {
    @Binds
    @Singleton
    abstract fun bindDocumentMetadataRepository(
        repository: RoomDocumentMetadataRepository,
    ): DocumentMetadataRepository
}
