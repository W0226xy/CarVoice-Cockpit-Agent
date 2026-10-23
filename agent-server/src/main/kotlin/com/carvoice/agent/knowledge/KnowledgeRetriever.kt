package com.carvoice.agent.knowledge

/** Boundary between task orchestration and a retrievable vehicle knowledge source. */
interface KnowledgeRetriever {
    suspend fun search(topic: String): KnowledgeSearchResult
    suspend fun search(topic: String, knowledgeBaseId: String?): KnowledgeSearchResult = search(topic)
}

data class KnowledgePassage(
    val id: String,
    val title: String,
    val content: String,
    val score: Double,
    val documentId: String = id,
    val pageNumber: Int? = null,
    val sectionTitle: String? = null,
    val knowledgeBaseId: String = "system-vehicle-manual",
)

data class KnowledgeSearchResult(
    val topic: String,
    val passages: List<KnowledgePassage>,
)

data class KnowledgeSearchQuery(val text: String, val embedding: List<Float>, val knowledgeBaseId: String? = null)

interface KnowledgeRepository {
    suspend fun search(query: KnowledgeSearchQuery, limit: Int): List<KnowledgePassage>
}

interface EmbeddingService {
    suspend fun embed(text: String): List<Float>

    suspend fun embedAll(texts: List<String>): List<List<Float>> = texts.map { embed(it) }
}

/** Generates answers solely from retrieved passages; it may not invoke tools or web search. */
interface KnowledgeAnswerService {
    fun answer(
        question: String,
        passages: List<KnowledgePassage>,
    ): kotlinx.coroutines.flow.Flow<String>
}

class DefaultKnowledgeRetriever(
    private val repository: KnowledgeRepository,
    private val embeddingService: EmbeddingService,
    private val limit: Int = 3,
) : KnowledgeRetriever {
    override suspend fun search(topic: String): KnowledgeSearchResult = search(topic, null)
    override suspend fun search(topic: String, knowledgeBaseId: String?): KnowledgeSearchResult {
        val normalizedTopic = topic.trim()
        require(normalizedTopic.isNotEmpty()) { "Knowledge topic must not be blank" }
        return KnowledgeSearchResult(
            normalizedTopic,
            repository.search(
                KnowledgeSearchQuery(normalizedTopic, embeddingService.embed(normalizedTopic), knowledgeBaseId),
                limit,
            ),
        )
    }
}
