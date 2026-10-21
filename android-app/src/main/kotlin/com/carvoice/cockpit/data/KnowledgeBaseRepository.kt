package com.carvoice.cockpit.data

import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Server-backed knowledge management boundary.
 */
interface KnowledgeBaseRepository {
    suspend fun getKnowledgeBases(): Result<List<KnowledgeBase>>
    suspend fun createKnowledgeBase(name: String, description: String): Result<KnowledgeBase>
    suspend fun getDocuments(knowledgeBaseId: String): Result<List<KnowledgeDocument>>
    suspend fun uploadDocument(knowledgeBaseId: String, document: UploadDocument): Result<KnowledgeDocument>
    suspend fun getDocument(knowledgeBaseId: String, documentId: String): Result<KnowledgeDocument>
    suspend fun deleteDocument(knowledgeBaseId: String, documentId: String): Result<Unit>
    suspend fun selectKnowledgeBase(id: String?)
    fun observeSelectedKnowledgeBaseId(): Flow<String?>
}

data class KnowledgeBase(val id: String, val name: String, val description: String)
data class KnowledgeDocument(val id: String, val fileName: String, val contentType: String, val status: DocumentStatus, val errorMessage: String? = null)
enum class DocumentStatus { PENDING, PROCESSING, READY, FAILED }
data class UploadDocument(val fileName: String, val contentType: String, val sizeBytes: Long?, val openStream: () -> InputStream)
