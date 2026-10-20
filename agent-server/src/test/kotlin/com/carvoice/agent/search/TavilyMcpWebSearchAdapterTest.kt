package com.carvoice.agent.search

import com.carvoice.agent.config.TavilyConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TavilyMcpWebSearchAdapterTest {
    @Test
    fun `MCP adapter initializes lists tools and calls only search with filtered sources`() =
        withMcpServer { server ->
            val methods = mutableListOf<String>()
            server.createContext("/mcp/") { exchange ->
                val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val request = Json.parseToJsonElement(body).jsonObject
                val method = request["method"]?.jsonPrimitive?.content.orEmpty()
                methods += method
                val response = when (method) {
                    "initialize" ->
                        """event: message
data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}

event: message
data: {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{},"serverInfo":{"name":"test","version":"1"}}}

"""
                    "notifications/initialized" -> ""
                    "tools/list" ->
                        """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"tavily-search"},{"name":"tavily-extract"}]}}"""
                    "tools/call" ->
                        """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Title: Good source\nURL: https://news.example/item\nContent: ${"x".repeat(200)}\nPublished Date: 2026-10-20\n\nTitle: Bad source\nURL: javascript:alert(1)\nContent: bad"}],"isError":false}}"""
                    else -> error("Unexpected method $method")
                }
                exchange.responseHeaders.add(
                    "Content-Type",
                    if (method == "initialize") "text/event-stream" else "application/json",
                )
                if (method == "initialize") {
                    exchange.responseHeaders.add("MCP-Session-Id", "test-session")
                }
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(
                    if (method == "notifications/initialized") 202 else 200,
                    bytes.size.toLong(),
                )
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()

            val outcome = runBlocking {
                TavilyMcpWebSearchAdapter(config(server, maxSnippetChars = 100))
                    .search("AI news")
            }

            val result = assertIs<WebSearchOutcome.Success>(outcome).result
            assertEquals(
                listOf(
                    "initialize",
                    "notifications/initialized",
                    "tools/list",
                    "tools/call",
                ),
                methods,
            )
            assertEquals(1, result.sources.size)
            assertEquals("news.example", result.sources.single().domain)
            assertEquals(100, result.sources.single().snippet.length)
        }

    @Test
    fun `MCP timeout returns safe failure`() = withMcpServer { server ->
        server.createContext("/mcp/") { exchange ->
            Thread.sleep(1_500)
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()

        val outcome = runBlocking {
            TavilyMcpWebSearchAdapter(config(server, timeoutSeconds = 1))
                .search("weather")
        }

        assertEquals(
            WebSearchFailureReason.TIMEOUT,
            assertIs<WebSearchOutcome.Failure>(outcome).reason,
        )
    }

    @Test
    fun `disabled configuration does not create remote adapter`() {
        val port = createWebSearchPort(
            TavilyConfig.fromEnvironment(
                mapOf("TAVILY_ENABLED" to "false", "TAVILY_API_KEY" to "test-key"),
            ),
        )

        assertTrue(port is DisabledWebSearchPort)
    }

    private fun config(
        server: HttpServer,
        timeoutSeconds: Long = 5,
        maxSnippetChars: Int = 800,
    ) = TavilyConfig(
        enabled = true,
        apiKey = "test-key",
        mcpUrl = "http://127.0.0.1:${server.address.port}/mcp/",
        timeoutSeconds = timeoutSeconds,
        maxResults = 5,
        searchDepth = "basic",
        maxSnippetChars = maxSnippetChars,
        maxContextChars = 6_000,
    )

    private fun withMcpServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }
}
