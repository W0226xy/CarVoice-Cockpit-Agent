package com.carvoice.agent.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmConfigTest {
    @Test
    fun `openai-compatible mode normalizes a provider v1 endpoint`() {
        val config = LlmConfig.fromEnvironment(
            mapOf(
                "LLM_PROVIDER" to "openai_compatible",
                "OPENAI_BASE_URL" to "https://provider.example/v1/",
                "OPENAI_API_KEY" to "test-key",
                "OPENAI_MODEL" to "test-model",
            ),
        )

        assertEquals("https://provider.example", config.baseUrl)
    }

    @Test
    fun `openai-compatible mode requires all model settings`() {
        assertFailsWith<IllegalStateException> {
            LlmConfig.fromEnvironment(mapOf("LLM_PROVIDER" to "openai_compatible"))
        }
    }

    @Test
    fun `dotenv fills absent environment values but does not override them`() {
        val dotenv = Files.createTempFile("carvoice", ".env")
        try {
            Files.writeString(
                dotenv,
                """
                # Development configuration
                OPENAI_MODEL=from-dotenv
                OPENAI_API_KEY="quoted-key"
                """.trimIndent(),
            )

            val environment = RuntimeEnvironment.load(
                systemEnvironment = mapOf("OPENAI_MODEL" to "from-system"),
                dotenvPath = dotenv,
            )

            assertEquals("from-system", environment["OPENAI_MODEL"])
            assertEquals("quoted-key", environment["OPENAI_API_KEY"])
        } finally {
            Files.deleteIfExists(dotenv)
        }
    }

    @Test
    fun `dotenv discovery searches parent directories`() {
        val root = Files.createTempDirectory("carvoice-root")
        val moduleDirectory = Files.createDirectories(root.resolve("agent-server"))
        val dotenv = root.resolve(".env")
        try {
            Files.writeString(dotenv, "OPENAI_MODEL=from-parent-dotenv")

            val discoveredPath = RuntimeEnvironment.findDotenvPath(moduleDirectory)
            val environment = RuntimeEnvironment.load(emptyMap(), discoveredPath)

            assertEquals("from-parent-dotenv", environment["OPENAI_MODEL"])
        } finally {
            Files.deleteIfExists(dotenv)
            Files.deleteIfExists(moduleDirectory)
            Files.deleteIfExists(root)
        }
    }
}
