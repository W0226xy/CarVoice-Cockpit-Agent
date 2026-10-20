package com.carvoice.agent.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TavilyConfigTest {
    @Test
    fun `Tavily is disabled by default and missing key keeps application available`() {
        assertFalse(TavilyConfig.fromEnvironment(emptyMap()).isAvailable)
        assertFalse(
            TavilyConfig.fromEnvironment(mapOf("TAVILY_ENABLED" to "true")).isAvailable,
        )
    }

    @Test
    fun `valid Tavily environment enables bounded search`() {
        val config = TavilyConfig.fromEnvironment(
            mapOf(
                "TAVILY_ENABLED" to "true",
                "TAVILY_API_KEY" to "test-key",
                "TAVILY_MCP_URL" to "https://mcp.example/mcp",
                "TAVILY_SEARCH_TIMEOUT_SECONDS" to "10",
                "TAVILY_MAX_RESULTS" to "3",
                "TAVILY_SEARCH_DEPTH" to "basic",
            ),
        )

        assertTrue(config.isAvailable)
        assertEquals(3, config.maxResults)
    }

    @Test
    fun `invalid Tavily limits fail clearly`() {
        assertFailsWith<IllegalArgumentException> {
            TavilyConfig.fromEnvironment(mapOf("TAVILY_MAX_RESULTS" to "99"))
        }
        assertFailsWith<IllegalArgumentException> {
            TavilyConfig.fromEnvironment(mapOf("TAVILY_SEARCH_DEPTH" to "crawl"))
        }

        @Test
        fun `plaintext remote MCP URL never enables an API key`() {
            val remote = TavilyConfig.fromEnvironment(
                mapOf(
                    "TAVILY_ENABLED" to "true",
                    "TAVILY_API_KEY" to "test-key",
                    "TAVILY_MCP_URL" to "http://mcp.example/mcp",
                ),
            )
            val loopback = TavilyConfig.fromEnvironment(
                mapOf(
                    "TAVILY_ENABLED" to "true",
                    "TAVILY_API_KEY" to "test-key",
                    "TAVILY_MCP_URL" to "http://127.0.0.1:8080/mcp",
                ),
            )

            assertFalse(remote.isAvailable)
            assertTrue(loopback.isAvailable)
        }

        @Test
        fun `configuration string redacts key and URL query`() {
            val config = TavilyConfig.fromEnvironment(
                mapOf(
                    "TAVILY_ENABLED" to "true",
                    "TAVILY_API_KEY" to "secret-key",
                    "TAVILY_MCP_URL" to "https://mcp.example/mcp?private=value",
                ),
            )

            assertFalse(config.toString().contains("secret-key"))
            assertFalse(config.toString().contains("private=value"))
        }
    }
}
