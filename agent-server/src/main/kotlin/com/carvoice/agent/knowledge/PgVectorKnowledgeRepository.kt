package com.carvoice.agent.knowledge

import com.carvoice.agent.config.RagConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

class KnowledgeRepositoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PgVectorKnowledgeRepository(private val config: RagConfig) : KnowledgeManagementRepository {
    init {
        config.requirePgVectorConfiguration()
        Flyway.configure().dataSource(requireNotNull(config.databaseUrl), config.databaseUser, config.databasePassword).load().migrate()
        connection<Unit> {
            prepareStatement("SELECT count(*) FROM document_chunks WHERE vector_dims(embedding) <> ?").use { statement ->
                statement.setInt(1, requireNotNull(config.embeddingDimension)); statement.executeQuery().use { result ->
                    if (result.next() && result.getInt(1) > 0) error("Existing pgvector embeddings do not match EMBEDDING_DIMENSION")
                }
            }; Unit
        }
    }

    override suspend fun search(query: KnowledgeSearchQuery, limit: Int): List<KnowledgePassage> = db {
        require(query.embedding.size == config.embeddingDimension) { "Query embedding dimension mismatch" }
        val baseId = query.knowledgeBaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw KnowledgeRepositoryException("请选择有效的知识库后再提问")
        val bounded = limit.coerceIn(1, config.topK.coerceAtMost(20))
        prepareStatement("""SELECT c.id, c.document_id, d.file_name, c.content, c.page_number, c.section_title, 1 - (c.embedding <=> ?::vector) AS score
            FROM document_chunks c JOIN knowledge_documents d ON d.id=c.document_id
            WHERE d.status='READY' AND c.knowledge_base_id=? AND 1 - (c.embedding <=> ?::vector) >= ?
            ORDER BY c.embedding <=> ?::vector LIMIT ?""").use { statement ->
            val vector = query.embedding.asVector(); statement.setString(1, vector); statement.setObject(2, baseId); statement.setString(3, vector); statement.setDouble(4, config.minScore); statement.setString(5, vector); statement.setInt(6, bounded)
            statement.executeQuery().use { results -> buildList {
                while (results.next()) {
                    val page = results.getObject("page_number") as? Int
                    val section = results.getString("section_title")
                    val suffix = listOfNotNull(page?.let { "第${it}页" }, section).joinToString(" · ")
                    add(KnowledgePassage(results.getString(1), results.getString(3), results.getString(4), results.getDouble("score"), results.getString(2), page, section))
                }
            } }
        }
    }

    override suspend fun createBase(name: String, description: String): KnowledgeBase = db {
        val id = UUID.randomUUID(); prepareStatement("INSERT INTO knowledge_bases(id,name,description) VALUES(?,?,?)").use { it.setObject(1,id); it.setString(2,name); it.setString(3,description); it.executeUpdate() }
        KnowledgeBase(id, name, description, Instant.now())
    }
    override suspend fun listBases(): List<KnowledgeBase> = db { prepareStatement("SELECT id,name,description,created_at FROM knowledge_bases ORDER BY created_at").use { s -> s.executeQuery().use { r -> buildList { while(r.next()) add(KnowledgeBase(r.getObject(1,UUID::class.java),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant())) } } } }
    override suspend fun createDocument(baseId: UUID, fileName: String, contentType: String): ManagedDocument = db {
        val id=UUID.randomUUID(); prepareStatement("INSERT INTO knowledge_documents(id,knowledge_base_id,file_name,content_type,status) VALUES(?,?,?,?, 'PENDING')").use { it.setObject(1,id);it.setObject(2,baseId);it.setString(3,fileName);it.setString(4,contentType);it.executeUpdate() }; ManagedDocument(id,baseId,fileName,contentType,DocumentStatus.PENDING,null,Instant.now())
    }
    override suspend fun getDocument(baseId: UUID, documentId: UUID): ManagedDocument? = db { prepareStatement("SELECT id,knowledge_base_id,file_name,content_type,status,error_message,created_at FROM knowledge_documents WHERE knowledge_base_id=? AND id=?").use { s ->s.setObject(1,baseId);s.setObject(2,documentId);s.executeQuery().use { r ->if(r.next()) ManagedDocument(r.getObject(1,UUID::class.java),r.getObject(2,UUID::class.java),r.getString(3),r.getString(4),DocumentStatus.valueOf(r.getString(5)),r.getString(6),r.getTimestamp(7).toInstant()) else null } } }
    override suspend fun listDocuments(baseId: UUID): List<ManagedDocument> = db { prepareStatement("SELECT id,knowledge_base_id,file_name,content_type,status,error_message,created_at FROM knowledge_documents WHERE knowledge_base_id=? ORDER BY created_at").use { s ->s.setObject(1,baseId);s.executeQuery().use { r ->buildList { while(r.next()) add(ManagedDocument(r.getObject(1,UUID::class.java),r.getObject(2,UUID::class.java),r.getString(3),r.getString(4),DocumentStatus.valueOf(r.getString(5)),r.getString(6),r.getTimestamp(7).toInstant())) } } } }
    override suspend fun updateDocumentStatus(documentId: UUID, status: DocumentStatus, errorMessage: String?) { db { prepareStatement("UPDATE knowledge_documents SET status=?, error_message=?, updated_at=now() WHERE id=?").use { it.setString(1,status.name);it.setString(2,errorMessage?.take(500));it.setObject(3,documentId);it.executeUpdate() }; Unit } }
    override suspend fun replaceChunks(documentId: UUID, baseId: UUID, chunks: List<DocumentChunk>) = db { autoCommit=false; try { prepareStatement("DELETE FROM document_chunks WHERE document_id=?").use { it.setObject(1,documentId);it.executeUpdate() }; prepareStatement("INSERT INTO document_chunks(id,document_id,knowledge_base_id,content,chunk_index,page_number,section_title,embedding) VALUES(?,?,?,?,?,?,?,?::vector)").use { s -> chunks.forEach { c -> require(c.embedding.size==config.embeddingDimension){"Document embedding dimension mismatch"};s.setObject(1,UUID.randomUUID());s.setObject(2,documentId);s.setObject(3,baseId);s.setString(4,c.content);s.setInt(5,c.chunkIndex);s.setObject(6,c.pageNumber);s.setString(7,c.sectionTitle);s.setString(8,c.embedding.asVector());s.addBatch() };s.executeBatch() }; commit() } catch(e:Exception){rollback();throw e} finally {autoCommit=true} }
    override suspend fun deleteDocument(baseId: UUID, documentId: UUID): Boolean = db { prepareStatement("DELETE FROM knowledge_documents WHERE knowledge_base_id=? AND id=?").use { it.setObject(1,baseId);it.setObject(2,documentId);it.executeUpdate()>0 } }
    private suspend fun <T> db(block: Connection.() -> T): T = try { withContext(Dispatchers.IO) { connection(block) } } catch(e: Exception) { if(e is KnowledgeRepositoryException) throw e; throw KnowledgeRepositoryException("Knowledge repository operation failed",e) }
    private fun <T> connection(block: Connection.() -> T): T = DriverManager.getConnection(requireNotNull(config.databaseUrl), config.databaseUser, config.databasePassword).use(block)
}
private fun List<Float>.asVector() = joinToString(prefix="[", postfix="]") { it.toString() }
