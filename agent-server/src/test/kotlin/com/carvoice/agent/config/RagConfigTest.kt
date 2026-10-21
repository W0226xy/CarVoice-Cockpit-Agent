package com.carvoice.agent.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RagConfigTest {
    @Test fun `memory is the safe default`() {
        val config = RagConfig.fromEnvironment(emptyMap())
        assertEquals(RagStorage.MEMORY, config.storage)
        assertEquals(5, config.topK)
    }
    @Test fun `pgvector requires database and embedding configuration`() {
        assertFailsWith<IllegalArgumentException> { RagConfig.fromEnvironment(mapOf("RAG_STORAGE" to "pgvector")) }
    }
    @Test fun `pgvector accepts complete configuration`() {
        val config = RagConfig.fromEnvironment(pgEnvironment())
        assertEquals(RagStorage.PGVECTOR, config.storage)
        assertEquals(3, config.embeddingDimension)
    }
    private fun pgEnvironment() = mapOf("RAG_STORAGE" to "pgvector", "DATABASE_URL" to "jdbc:postgresql://localhost/test", "DATABASE_USER" to "u", "DATABASE_PASSWORD" to "p", "EMBEDDING_BASE_URL" to "https://embedding.example", "EMBEDDING_API_KEY" to "key", "EMBEDDING_MODEL" to "model", "EMBEDDING_DIMENSION" to "3")
}
