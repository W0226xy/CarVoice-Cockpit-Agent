package com.carvoice.agent.routes

import com.carvoice.agent.config.RagConfig
import com.carvoice.agent.knowledge.DocumentIngestionService
import com.carvoice.agent.knowledge.KnowledgeManagementRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class CreateKnowledgeBaseRequest(val name: String, val description: String = "")
@Serializable data class KnowledgeBaseResponse(val id: String, val name: String, val description: String)
@Serializable data class DocumentResponse(val id: String, val fileName: String, val contentType: String, val status: String, val errorMessage: String? = null)
@Serializable data class ApiError(val code: String, val message: String)

fun Application.configureKnowledgeManagementRoutes(config: RagConfig, repository: KnowledgeManagementRepository?, ingestion: DocumentIngestionService?) {
    routing { route("/api/knowledge-bases") {
        fun disabled() = repository == null || ingestion == null || !config.importEnabled
        post {
            if (disabled()) return@post call.respond(HttpStatusCode.Forbidden, ApiError("KNOWLEDGE_IMPORT_DISABLED", "知识库导入功能未启用。"))
            val request = runCatching { call.receive<CreateKnowledgeBaseRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "请求格式无效。"))
            if (request.name.isBlank() || request.name.length > 160) return@post call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_NAME", "知识库名称无效。"))
            val base = requireNotNull(repository).createBase(request.name.trim(), request.description.take(2000))
            call.respond(HttpStatusCode.Created, KnowledgeBaseResponse(base.id.toString(), base.name, base.description))
        }
        get {
            if (disabled()) return@get call.respond(HttpStatusCode.Forbidden, ApiError("KNOWLEDGE_IMPORT_DISABLED", "知识库导入功能未启用。"))
            call.respond(requireNotNull(repository).listBases().map { KnowledgeBaseResponse(it.id.toString(), it.name, it.description) })
        }
        route("{baseId}/documents") {
            post {
                if (disabled()) return@post call.respond(HttpStatusCode.Forbidden, ApiError("KNOWLEDGE_IMPORT_DISABLED", "知识库导入功能未启用。"))
                val baseId = call.parameters["baseId"].uuidOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_ID", "知识库标识无效。"))
                var fileName: String? = null; var type: String? = null; var bytes: ByteArray? = null
                call.receiveMultipart().forEachPart { part -> if (part is PartData.FileItem && part.name == "file") { fileName=part.originalFileName; type=part.contentType?.withoutParameters()?.toString(); bytes=part.provider().readRemaining(config.maxFileSizeBytes + 1).readBytes() }; part.dispose() }
                val allowed = mapOf("application/pdf" to "pdf", "text/plain" to "txt")
                if (type !in allowed || bytes == null || fileName.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_FILE", "只允许 PDF 或 TXT 文件。"))
                if (bytes!!.size > config.maxFileSizeBytes) return@post call.respond(HttpStatusCode.PayloadTooLarge, ApiError("FILE_TOO_LARGE", "文件超过大小限制。"))
                val document = requireNotNull(ingestion).ingest(baseId, fileName!!, type!!, bytes!!)
                call.respond(HttpStatusCode.Accepted, document.response())
            }
            get {
                if (disabled()) return@get call.respond(HttpStatusCode.Forbidden, ApiError("KNOWLEDGE_IMPORT_DISABLED", "知识库导入功能未启用。"))
                val baseId=call.parameters["baseId"].uuidOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_ID", "知识库标识无效。")); call.respond(requireNotNull(repository).listDocuments(baseId).map { it.response() })
            }
            route("{documentId}") {
                get { if(disabled()) return@get call.respond(HttpStatusCode.Forbidden,ApiError("KNOWLEDGE_IMPORT_DISABLED","知识库导入功能未启用。")); val base=call.parameters["baseId"].uuidOrNull(); val doc=call.parameters["documentId"].uuidOrNull(); if(base==null||doc==null) return@get call.respond(HttpStatusCode.BadRequest,ApiError("INVALID_ID","文档标识无效。")); val value=requireNotNull(repository).getDocument(base,doc) ?: return@get call.respond(HttpStatusCode.NotFound,ApiError("NOT_FOUND","文档不存在。"));call.respond(value.response()) }
                delete { if(disabled()) return@delete call.respond(HttpStatusCode.Forbidden,ApiError("KNOWLEDGE_IMPORT_DISABLED","知识库导入功能未启用。")); val base=call.parameters["baseId"].uuidOrNull(); val doc=call.parameters["documentId"].uuidOrNull(); if(base==null||doc==null) return@delete call.respond(HttpStatusCode.BadRequest,ApiError("INVALID_ID","文档标识无效。")); if(requireNotNull(repository).deleteDocument(base,doc)) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound,ApiError("NOT_FOUND","文档不存在。")) }
            }
        }
    } }
}
private fun String?.uuidOrNull() = this?.let { runCatching { UUID.fromString(it) }.getOrNull() }
private fun com.carvoice.agent.knowledge.ManagedDocument.response() = DocumentResponse(id.toString(),fileName,contentType,status.name,errorMessage)
