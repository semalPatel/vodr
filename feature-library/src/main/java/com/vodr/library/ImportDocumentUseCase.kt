package com.vodr.library

class ImportDocumentUseCase(
    private val repository: DocumentMetadataRepository = InMemoryDocumentMetadataRepository(),
    private val acceptedMimeTypes: Set<String> = setOf("application/pdf", "application/epub+zip"),
) {
    val supportedMimeTypes: Set<String> = acceptedMimeTypes

    fun isSupportedMimeType(mimeType: String): Boolean = mimeType in acceptedMimeTypes

    fun importDocument(
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
}
