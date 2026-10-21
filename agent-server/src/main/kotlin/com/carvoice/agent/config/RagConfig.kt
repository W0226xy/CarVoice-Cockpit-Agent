package com.carvoice.agent.config

enum class RagStorage { MEMORY, PGVECTOR }

data class RagConfig(
    val enabled: Boolean,
    val storage: RagStorage,
    val databaseUrl: String?,
    val databaseUser: String?,
    val databasePassword: String?,
    val embeddingBaseUrl: String?,
    val embeddingApiKey: String?,
    val embeddingModel: String?,
    val embeddingDimension: Int?,
    val importEnabled: Boolean,
    val maxFileSizeBytes: Long,
    val chunkSize: Int,
    val chunkOverlap: Int,
    val topK: Int,
    val minScore: Double,
) {
    fun requirePgVectorConfiguration() {
        require(storage == RagStorage.PGVECTOR) { "RAG storage is not pgvector" }
        require(!databaseUrl.isNullOrBlank()) { "DATABASE_URL is required when RAG_STORAGE=pgvector" }
        require(!databaseUser.isNullOrBlank()) { "DATABASE_USER is required when RAG_STORAGE=pgvector" }
        require(!databasePassword.isNullOrBlank()) { "DATABASE_PASSWORD is required when RAG_STORAGE=pgvector" }
        require(!embeddingBaseUrl.isNullOrBlank()) { "EMBEDDING_BASE_URL is required when RAG_STORAGE=pgvector" }
        require(!embeddingApiKey.isNullOrBlank()) { "EMBEDDING_API_KEY is required when RAG_STORAGE=pgvector" }
        require(!embeddingModel.isNullOrBlank()) { "EMBEDDING_MODEL is required when RAG_STORAGE=pgvector" }
        require(embeddingDimension != null) { "EMBEDDING_DIMENSION is required when RAG_STORAGE=pgvector" }
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): RagConfig {
            val storage = when (environment["RAG_STORAGE"]?.trim()?.lowercase() ?: "memory") {
                "memory" -> RagStorage.MEMORY
                "pgvector" -> RagStorage.PGVECTOR
                else -> error("RAG_STORAGE must be memory or pgvector")
            }
            return RagConfig(
                enabled = environment.bool("RAG_ENABLED", false), storage = storage,
                databaseUrl = environment.optional("DATABASE_URL"), databaseUser = environment.optional("DATABASE_USER"), databasePassword = environment.optional("DATABASE_PASSWORD"),
                embeddingBaseUrl = environment.optional("EMBEDDING_BASE_URL")?.trimEnd('/')?.removeSuffix("/v1"),
                embeddingApiKey = environment.optional("EMBEDDING_API_KEY"), embeddingModel = environment.optional("EMBEDDING_MODEL"),
                embeddingDimension = environment.optional("EMBEDDING_DIMENSION")?.toIntOrNull()?.also { require(it in 1..8192) { "EMBEDDING_DIMENSION must be 1..8192" } },
                importEnabled = environment.bool("KNOWLEDGE_IMPORT_ENABLED", false),
                maxFileSizeBytes = environment.int("KNOWLEDGE_MAX_FILE_SIZE_MB", 20, 1..100).toLong() * 1024 * 1024,
                chunkSize = environment.int("KNOWLEDGE_CHUNK_SIZE", 800, 100..4000),
                chunkOverlap = environment.int("KNOWLEDGE_CHUNK_OVERLAP", 120, 0..1000),
                topK = environment.int("KNOWLEDGE_TOP_K", 5, 1..20),
                minScore = environment.optional("KNOWLEDGE_MIN_SCORE")?.toDoubleOrNull()?.also { require(it in 0.0..1.0) { "KNOWLEDGE_MIN_SCORE must be 0..1" } } ?: 0.5,
            ).also { config ->
                require(config.chunkOverlap < config.chunkSize) { "KNOWLEDGE_CHUNK_OVERLAP must be smaller than KNOWLEDGE_CHUNK_SIZE" }
                if (config.storage == RagStorage.PGVECTOR) config.requirePgVectorConfiguration()
            }
        }
    }
}

private fun Map<String, String>.optional(name: String): String? = get(name)?.trim()?.takeIf(String::isNotEmpty)
private fun Map<String, String>.bool(name: String, default: Boolean): Boolean = optional(name)?.lowercase()?.let {
    when (it) { "true" -> true; "false" -> false; else -> error("$name must be true or false") }
} ?: default
private fun Map<String, String>.int(name: String, default: Int, range: IntRange): Int = optional(name)?.toIntOrNull()?.also {
    require(it in range) { "$name must be in ${range.first}..${range.last}" }
} ?: default
