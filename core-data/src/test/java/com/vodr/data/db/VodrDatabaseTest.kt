package com.vodr.data.db

import androidx.test.core.app.ApplicationProvider
import com.vodr.data.db.entity.ChapterEntity
import com.vodr.data.db.entity.ChunkEntity
import com.vodr.data.db.entity.DocumentEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VodrDatabaseTest {

    private lateinit var database: VodrDatabase

    @Before
    fun setUp() {
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VodrDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadDocument() {
        val document = DocumentEntity(
            title = "Sample document",
            sourceUri = "file:///tmp/sample.pdf",
            createdAtEpochMs = 1_700_000_000_000,
            updatedAtEpochMs = 1_700_000_000_123,
        )

        val documentId = database.documentDao().insert(document)
        val readBack = database.documentDao().getById(documentId)

        assertNotNull(readBack)
        assertEquals(document.title, readBack?.title)
        assertEquals(document.sourceUri, readBack?.sourceUri)
        assertEquals(document.createdAtEpochMs, readBack?.createdAtEpochMs)
        assertEquals(document.updatedAtEpochMs, readBack?.updatedAtEpochMs)
    }

    @Test
    fun insertAndReadChunk() {
        val documentId = database.documentDao().insert(
            DocumentEntity(
                title = "Sample document",
                sourceUri = "file:///tmp/sample.pdf",
                createdAtEpochMs = 1_700_000_000_000,
                updatedAtEpochMs = 1_700_000_000_123,
            )
        )
        val chapterId = database.chapterDao().insert(
            ChapterEntity(
                documentId = documentId,
                indexInDocument = 0,
                title = "Chapter 1",
            )
        )

        val chunk = ChunkEntity(
            documentId = documentId,
            chapterId = chapterId,
            indexInChapter = 0,
            text = "First chunk of text.",
            startOffset = 0,
            endOffset = 19,
        )

        val chunkId = database.chunkDao().insert(chunk)
        val readBack = database.chunkDao().getById(chunkId)

        assertNotNull(readBack)
        assertEquals(chunk.documentId, readBack?.documentId)
        assertEquals(chunk.chapterId, readBack?.chapterId)
        assertEquals(chunk.indexInChapter, readBack?.indexInChapter)
        assertEquals(chunk.text, readBack?.text)
        assertEquals(chunk.startOffset, readBack?.startOffset)
        assertEquals(chunk.endOffset, readBack?.endOffset)
    }
}
