package com.carvoice.agent.knowledge

/**
 * Future RAG boundary. No implementation is registered in the current stage.
 */
interface KnowledgeRetriever {
    suspend fun search(topic: String): KnowledgeSearchResult
}

data class KnowledgeSearchResult(
    val answer: String,
    val citations: List<String>,
)
