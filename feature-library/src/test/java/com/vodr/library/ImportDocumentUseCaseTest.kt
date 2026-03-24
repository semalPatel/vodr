package com.vodr.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class ImportDocumentUseCaseTest {

    private class FakeDocumentMetadataRepository : DocumentMetadataRepository {
        var savedMetadata: DocumentMetadata? = null

        override suspend fun save(metadata: DocumentMetadata): ImportedDocument {
            savedMetadata = metadata
            return ImportedDocument(
                id = 42L,
                metadata = metadata,
            )
        }

        override fun observeAll(): Flow<List<ImportedDocument>> = emptyFlow()
    }

    @Test
    fun acceptsPdfAndEpubMimeTypes() {
        val useCase = ImportDocumentUseCase(repository = FakeDocumentMetadataRepository())

        assertEquals(
            setOf("application/pdf", "application/epub+zip"),
            useCase.supportedMimeTypes,
        )
        assertTrue(useCase.isSupportedMimeType("application/pdf"))
        assertTrue(useCase.isSupportedMimeType("application/epub+zip"))
        assertFalse(useCase.isSupportedMimeType("text/plain"))
    }

    @Test
    fun importDocumentPersistsMetadata() {
        runBlocking {
        val repository = FakeDocumentMetadataRepository()
        val useCase = ImportDocumentUseCase(repository = repository)
        val request = ImportDocumentRequest(
            sourceUri = "content://documents/document/1",
            displayName = "Sample.pdf",
            mimeType = "application/pdf",
            byteCount = 12_345L,
            lastModifiedEpochMs = 1_700_000_000_123L,
        )

        val result = useCase.importDocument(request, currentTimeEpochMs = 1_700_000_000_999L)

        assertEquals(request.sourceUri, repository.savedMetadata?.sourceUri)
        assertEquals(request.displayName, repository.savedMetadata?.displayName)
        assertEquals(request.mimeType, repository.savedMetadata?.mimeType)
        assertEquals(request.byteCount, repository.savedMetadata?.byteCount)
        assertEquals(request.lastModifiedEpochMs, repository.savedMetadata?.lastModifiedEpochMs)
        assertEquals(1_700_000_000_999L, repository.savedMetadata?.importedAtEpochMs)
        assertEquals(42L, result.id)
        assertEquals(repository.savedMetadata, result.metadata)
        }
    }
}
