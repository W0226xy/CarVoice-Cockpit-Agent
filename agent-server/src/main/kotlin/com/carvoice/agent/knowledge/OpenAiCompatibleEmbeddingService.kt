package com.carvoice.agent.knowledge

import com.carvoice.agent.config.RagConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

class EmbeddingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class OpenAiCompatibleEmbeddingService(
    private val config: RagConfig,
    private val client: HttpClient,
    private val timeoutMillis: Long = 15_000,
    private val batchSize: Int = 32,
) : EmbeddingService {
    init { config.requirePgVectorConfiguration(); require(batchSize in 1..128) }

    override suspend fun embed(text: String): List<Float> = embedAll(listOf(text)).single()

    override suspend fun embedAll(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()
        return texts.chunked(batchSize).flatMap { batch -> request(batch) }
    }

    private suspend fun request(inputs: List<String>): List<List<Float>> = try {
        withTimeout(timeoutMillis) {
            val response = client.post("${requireNotNull(config.embeddingBaseUrl)}/v1/embeddings") {
                contentType(ContentType.Application.Json)
                bearerAuth(requireNotNull(config.embeddingApiKey))
                setBody(EmbeddingRequest(requireNotNull(config.embeddingModel), inputs))
            }
            if (!response.status.isSuccess()) throw EmbeddingException("Embedding service returned ${response.status.value}")
            val body = response.body<EmbeddingResponse>()
            if (body.data.size != inputs.size) throw EmbeddingException("Embedding response count is invalid")
            body.data.sortedBy { it.index }.map { item ->
                item.embedding.takeIf { it.isNotEmpty() } ?: throw EmbeddingException("Embedding response contains an empty vector")
            }.onEach { vector ->
                if (vector.size != config.embeddingDimension) throw EmbeddingException("Embedding dimension does not match EMBEDDING_DIMENSION")
                if (vector.any { !it.isFinite() }) throw EmbeddingException("Embedding response contains invalid values")
            }
        }
    } catch (error: EmbeddingException) { throw error
    } catch (error: Exception) { throw EmbeddingException("Embedding service is unavailable", error) }
}

@Serializable private data class EmbeddingRequest(val model: String, val input: List<String>)
@Serializable private data class EmbeddingResponse(val data: List<EmbeddingData>)
@Serializable private data class EmbeddingData(val index: Int, val embedding: List<Float>)
