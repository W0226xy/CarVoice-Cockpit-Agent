package com.carvoice.cockpit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.knowledgePreferences by preferencesDataStore("knowledge_preferences")
class KnowledgeApiException(val userMessage: String) : RuntimeException(userMessage)
class HttpKnowledgeBaseRepository(private val context: Context, baseUrl: String, private val client: HttpClient = HttpClient {
    install(ContentNegotiation) { json() }; install(HttpTimeout) { connectTimeoutMillis=10_000; requestTimeoutMillis=30_000; socketTimeoutMillis=30_000 }
}) : KnowledgeBaseRepository {
    private val baseUrl = baseUrl.trimEnd('/').also { require(it.startsWith("http://") || it.startsWith("https://")) }
    override suspend fun getKnowledgeBases() = safe { client.get("$baseUrl/api/knowledge-bases").body<List<BaseDto>>().map(BaseDto::domain) }
    override suspend fun createKnowledgeBase(name:String,description:String)=safe { client.post("$baseUrl/api/knowledge-bases"){contentType(ContentType.Application.Json);setBody(CreateBaseDto(name,description))}.body<BaseDto>().domain() }
    override suspend fun getDocuments(id:String)=safe { client.get("$baseUrl/api/knowledge-bases/$id/documents").body<List<DocumentDto>>().map(DocumentDto::domain) }
    override suspend fun getDocument(base:String,id:String)=safe { client.get("$baseUrl/api/knowledge-bases/$base/documents/$id").body<DocumentDto>().domain() }
    override suspend fun uploadDocument(base:String,document:UploadDocument)=safe { client.post("$baseUrl/api/knowledge-bases/$base/documents"){setBody(MultiPartFormDataContent(formData { append("file",document.openStream().readBytes(),Headers.build { append(HttpHeaders.ContentDisposition,"form-data; name=\"file\"; filename=\"${document.fileName.replace("\"","_")}\"");append(HttpHeaders.ContentType,document.contentType) }) }))}.body<DocumentDto>().domain() }
    override suspend fun deleteDocument(base:String,id:String):Result<Unit> = safe { client.delete("$baseUrl/api/knowledge-bases/$base/documents/$id");Unit }
    override suspend fun selectKnowledgeBase(id:String?){context.knowledgePreferences.edit { if(id==null)it.remove(KEY) else it[KEY]=id }}
    override fun observeSelectedKnowledgeBaseId():Flow<String?> = context.knowledgePreferences.data.map { it[KEY] }
    private suspend fun <T> safe(block:suspend()->T):Result<T> = runCatching { block() }.recoverCatching { throw KnowledgeApiException(if(it.message?.contains("KNOWLEDGE_IMPORT_DISABLED")==true) "知识库导入功能未启用。" else "知识库服务暂时不可用，请稍后重试。") }
    private companion object { val KEY=stringPreferencesKey("selected_knowledge_base") }
}
@Serializable private data class CreateBaseDto(val name:String,val description:String)
@Serializable private data class BaseDto(val id:String,val name:String,val description:String){fun domain()=KnowledgeBase(id,name,description)}
@Serializable private data class DocumentDto(val id:String,val fileName:String,val contentType:String,val status:String,val errorMessage:String?=null){fun domain()=KnowledgeDocument(id,fileName,contentType,runCatching{DocumentStatus.valueOf(status)}.getOrDefault(DocumentStatus.FAILED),errorMessage)}
