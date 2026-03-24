package com.vodr.library

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ImportDocumentUseCase @Inject constructor(
    private val repository: DocumentMetadataRepository,
) {
    private val acceptedMimeTypes: Set<String> = SUPPORTED_MIME_TYPES
    val supportedMimeTypes: Set<String> = acceptedMimeTypes

    fun isSupportedMimeType(mimeType: String): Boolean = mimeType in acceptedMimeTypes

    suspend fun importDocument(
        request: ImportDocumentRequest,
        currentTimeEpochMs: Long = System.currentTimeMillis(),
    ): ImportedDocument {
        require(isSupportedMimeType(request.mimeType)) {
            "Unsupported MIME type: ${request.mimeType}"
        }

        val metadata = DocumentMetadata(
            sourceUri = request.sourceUri,
            displayName = request.displayName,
            mimeType = request.mimeType,
            byteCount = request.byteCount,
            lastModifiedEpochMs = request.lastModifiedEpochMs,
            importedAtEpochMs = currentTimeEpochMs,
        )

        return repository.save(metadata)
    }

    fun observeDocuments(): Flow<List<ImportedDocument>> = repository.observeAll()

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf("application/pdf", "application/epub+zip")
    }
}
