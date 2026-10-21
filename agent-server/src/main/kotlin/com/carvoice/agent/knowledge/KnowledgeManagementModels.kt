package com.carvoice.agent.knowledge

import java.time.Instant
import java.util.UUID

enum class DocumentStatus { PENDING, PROCESSING, READY, FAILED }
data class KnowledgeBase(val id: UUID, val name: String, val description: String, val createdAt: Instant)
data class ManagedDocument(val id: UUID, val knowledgeBaseId: UUID, val fileName: String, val contentType: String, val status: DocumentStatus, val errorMessage: String?, val createdAt: Instant)
data class DocumentChunk(val content: String, val chunkIndex: Int, val pageNumber: Int?, val sectionTitle: String?, val embedding: List<Float>)

interface KnowledgeManagementRepository : KnowledgeRepository {
    suspend fun createBase(name: String, description: String): KnowledgeBase
    suspend fun listBases(): List<KnowledgeBase>
    suspend fun createDocument(baseId: UUID, fileName: String, contentType: String): ManagedDocument
    suspend fun getDocument(baseId: UUID, documentId: UUID): ManagedDocument?
    suspend fun listDocuments(baseId: UUID): List<ManagedDocument>
    suspend fun updateDocumentStatus(documentId: UUID, status: DocumentStatus, errorMessage: String? = null)
    suspend fun replaceChunks(documentId: UUID, baseId: UUID, chunks: List<DocumentChunk>)
    suspend fun deleteDocument(baseId: UUID, documentId: UUID): Boolean
}
