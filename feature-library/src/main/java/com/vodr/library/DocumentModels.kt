package com.vodr.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow

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
    suspend fun save(metadata: DocumentMetadata): ImportedDocument
    fun observeAll(): Flow<List<ImportedDocument>>
}

class InMemoryDocumentMetadataRepository : DocumentMetadataRepository {
    private val documents = mutableListOf<ImportedDocument>()
    private val documentsFlow = MutableStateFlow<List<ImportedDocument>>(emptyList())
    private var nextId = 1L

    override suspend fun save(metadata: DocumentMetadata): ImportedDocument {
        val document = ImportedDocument(
            id = nextId++,
            metadata = metadata,
        )
        documents.add(document)
        documentsFlow.value = documents.toList()
        return document
    }

    override fun observeAll(): Flow<List<ImportedDocument>> = documentsFlow.asStateFlow()
}
