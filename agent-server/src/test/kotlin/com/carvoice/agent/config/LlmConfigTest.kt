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
    fun `openai-compatible mode is the default and still requires configuration`() {
        val error = assertFailsWith<IllegalStateException> {
            LlmConfig.fromEnvironment(emptyMap())
        }

        assertEquals(
            "OPENAI_BASE_URL is required for OpenAI-compatible LLM configuration",
            error.message,
        )
    }

    @Test
    fun `mock provider is rejected`() {
        val error = assertFailsWith<IllegalStateException> {
            LlmConfig.fromEnvironment(mapOf("LLM_PROVIDER" to "mock"))
        }

        assertEquals(
            "LLM_PROVIDER must be openai_compatible; Mock LLM is not supported",
            error.message,
        )
    }

    @Test
    fun `openai-compatible mode rejects every missing or blank model setting`() {
        val complete = mapOf(
            "LLM_PROVIDER" to "openai_compatible",
            "OPENAI_BASE_URL" to "https://provider.example",
            "OPENAI_API_KEY" to "test-key",
            "OPENAI_MODEL" to "test-model",
        )
        listOf("OPENAI_BASE_URL", "OPENAI_API_KEY", "OPENAI_MODEL").forEach { name ->
            val missing = assertFailsWith<IllegalStateException> {
                LlmConfig.fromEnvironment(complete - name)
            }
            assertEquals(
                "$name is required for OpenAI-compatible LLM configuration",
                missing.message,
            )

            val blank = assertFailsWith<IllegalStateException> {
                LlmConfig.fromEnvironment(complete + (name to "  "))
            }
            assertEquals(
                "$name is required for OpenAI-compatible LLM configuration",
                blank.message,
            )
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
