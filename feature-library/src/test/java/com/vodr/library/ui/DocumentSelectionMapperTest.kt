package com.vodr.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentSelectionMapperTest {
    @Test
    fun maps_pdf_selection_into_import_request() {
        val request = toImportDocumentRequest(
            sourceUri = "content://docs/1",
            displayName = "Book.pdf",
            detectedMimeType = "application/pdf",
            byteCount = 123L,
            lastModifiedEpochMs = 456L,
        )

        requireNotNull(request)
        assertEquals("application/pdf", request.mimeType)
        assertEquals("Book.pdf", request.displayName)
        assertEquals(123L, request.byteCount)
        assertEquals(456L, request.lastModifiedEpochMs)
    }

    @Test
    fun maps_epub_extension_when_provider_mime_type_is_generic() {
        val request = toImportDocumentRequest(
            sourceUri = "content://docs/2",
            displayName = "Novel.epub",
            detectedMimeType = "application/octet-stream",
            byteCount = null,
            lastModifiedEpochMs = null,
        )

        requireNotNull(request)
        assertEquals("application/epub+zip", request.mimeType)
    }

    @Test
    fun returns_null_for_unsupported_document_type() {
        val request = toImportDocumentRequest(
            sourceUri = "content://docs/3",
            displayName = "notes.txt",
            detectedMimeType = "text/plain",
            byteCount = null,
            lastModifiedEpochMs = null,
        )

        assertNull(request)
    }
}
