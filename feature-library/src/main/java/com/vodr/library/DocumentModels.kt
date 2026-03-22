package com.vodr.library

data class ImportDocumentRequest(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val byteCount: Long?,
    val lastModifiedEpochMs: Long?,
)

data class DocumentMetadata(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val byteCount: Long?,
    val lastModifiedEpochMs: Long?,
    val importedAtEpochMs: Long,
)

data class ImportedDocument(
    val id: Long,
    val metadata: DocumentMetadata,
)

interface DocumentMetadataRepository {
    fun save(metadata: DocumentMetadata): ImportedDocument
}

class InMemoryDocumentMetadataRepository : DocumentMetadataRepository {
    private val documents = mutableListOf<ImportedDocument>()
    private var nextId = 1L

    override fun save(metadata: DocumentMetadata): ImportedDocument {
        val document = ImportedDocument(
            id = nextId++,
            metadata = metadata,
        )
        documents.add(document)
        return document
    }

    fun getAll(): List<ImportedDocument> = documents.toList()
}
