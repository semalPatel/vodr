package com.vodr.library

import com.vodr.data.db.dao.DocumentDao
import com.vodr.data.db.entity.DocumentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDocumentMetadataRepository @Inject constructor(
    private val documentDao: DocumentDao,
) : DocumentMetadataRepository {
    override suspend fun save(metadata: DocumentMetadata): ImportedDocument {
        val existing = documentDao.getBySourceUri(metadata.sourceUri)
        val persistedId = documentDao.upsert(
            DocumentEntity(
                documentId = existing?.documentId ?: 0L,
                title = metadata.displayName,
                sourceUri = metadata.sourceUri,
                mimeType = metadata.mimeType,
                byteCount = metadata.byteCount,
                lastModifiedEpochMs = metadata.lastModifiedEpochMs,
                importedAtEpochMs = metadata.importedAtEpochMs,
                createdAtEpochMs = existing?.createdAtEpochMs ?: metadata.importedAtEpochMs,
                updatedAtEpochMs = metadata.importedAtEpochMs,
            ),
        )
        val readBackId = if (existing != null) existing.documentId else persistedId
        val persisted = documentDao.getById(readBackId)
            ?: error("Document was not persisted for source URI: ${metadata.sourceUri}")
        return persisted.toImportedDocument()
    }

    override fun observeAll(): Flow<List<ImportedDocument>> {
        return documentDao.observeAll().map { entities ->
            entities.map { it.toImportedDocument() }
        }
    }
}

private fun DocumentEntity.toImportedDocument(): ImportedDocument {
    return ImportedDocument(
        id = documentId,
        metadata = DocumentMetadata(
            sourceUri = sourceUri,
            displayName = title,
            mimeType = mimeType,
            byteCount = byteCount,
            lastModifiedEpochMs = lastModifiedEpochMs,
            importedAtEpochMs = importedAtEpochMs,
        ),
    )
}
