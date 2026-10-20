package com.carvoice.agent.search

import com.carvoice.agent.config.TavilyConfig
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class TavilyMcpWebSearchAdapter(
    private val config: TavilyConfig,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.timeoutSeconds))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WebSearchPort {
    private val requestId = AtomicLong(1)
    private val endpoint = config.authenticatedEndpoint()

    override suspend fun search(query: String): WebSearchOutcome {
        if (!config.isAvailable) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.DISABLED)
        }
        return try {
            withTimeout(config.timeoutSeconds * 1_000) {
                withContext(Dispatchers.IO) {
                    executeSearch(query)
                }
            }
        } catch (error: TimeoutCancellationException) {
            WebSearchOutcome.Failure(WebSearchFailureReason.TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpTimeoutException) {
            WebSearchOutcome.Failure(WebSearchFailureReason.TIMEOUT)
        } catch (error: IOException) {
            WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        } catch (error: SerializationException) {
            WebSearchOutcome.Failure(WebSearchFailureReason.INVALID_RESPONSE)
        } catch (error: IllegalArgumentException) {
            WebSearchOutcome.Failure(WebSearchFailureReason.INVALID_RESPONSE)
        }
    }

    private fun executeSearch(query: String): WebSearchOutcome {
        val initialize = post(
            payload = rpcRequest(
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", PROTOCOL_VERSION)
                    put("capabilities", buildJsonObject {})
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "carvoice-cockpit-agent")
                            put("version", "1.0")
                        },
                    )
                },
            ),
        )
        if (!initialize.success || initialize.body == null) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        }
        val initializeResult = initialize.body.resultOrThrow()
        val protocolVersion = initializeResult["protocolVersion"]
            ?.jsonPrimitive?.contentOrNull
            ?: return WebSearchOutcome.Failure(WebSearchFailureReason.INVALID_RESPONSE)
        val sessionId = initialize.sessionId

        val initialized = post(
            payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            },
            sessionId = sessionId,
            protocolVersion = protocolVersion,
            expectBody = false,
        )
        if (!initialized.success) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        }

        val toolsResponse = post(
            payload = rpcRequest("tools/list", buildJsonObject {}),
            sessionId = sessionId,
            protocolVersion = protocolVersion,
        )
        if (!toolsResponse.success || toolsResponse.body == null) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        }
        val toolName = toolsResponse.body.resultOrThrow()["tools"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.firstOrNull { it in SEARCH_TOOL_NAMES }
            ?: return WebSearchOutcome.Failure(WebSearchFailureReason.INVALID_RESPONSE)

        val callResponse = post(
            payload = rpcRequest(
                "tools/call",
                buildJsonObject {
                    put("name", toolName)
                    put(
                        "arguments",
                        buildJsonObject {
                            put("query", query)
                            put("search_depth", config.searchDepth)
                            put("max_results", config.maxResults)
                            put("include_raw_content", false)
                            put("include_images", false)
                        },
                    )
                },
            ),
            sessionId = sessionId,
            protocolVersion = protocolVersion,
        )
        if (!callResponse.success || callResponse.body == null) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        }
        val result = callResponse.body.resultOrThrow()
        if (result["isError"]?.jsonPrimitive?.booleanOrNull == true) {
            return WebSearchOutcome.Failure(WebSearchFailureReason.REMOTE_ERROR)
        }
        val sources = parseSources(result)
        return if (sources.isEmpty()) {
            WebSearchOutcome.Failure(WebSearchFailureReason.NO_RESULTS)
        } else {
            WebSearchOutcome.Success(WebSearchResult(query, sources.take(config.maxResults)))
        }
    }

    private fun parseSources(result: JsonObject): List<WebSearchSource> {
        val structured = result["structuredContent"]?.jsonObject
            ?.get("results") as? JsonArray
        val candidates = if (structured != null) {
            structured.mapNotNull(::parseStructuredSource)
        } else {
            result["content"]?.jsonArray
                ?.mapNotNull { item ->
                    item.jsonObject.takeIf {
                        it["type"]?.jsonPrimitive?.contentOrNull == "text"
                    }?.get("text")?.jsonPrimitive?.contentOrNull
                }
                ?.flatMap(::parseTextSources)
                .orEmpty()
        }
        return candidates
            .mapNotNull(::normalizeSource)
            .distinctBy(WebSearchSource::url)
            .take(config.maxResults)
    }

    private fun parseStructuredSource(element: JsonElement): WebSearchSource? {
        val item = element.jsonObject
        val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return null
        return WebSearchSource(
            title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            url = url,
            snippet = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            domain = "",
            publishedAt = item["published_date"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun parseTextSources(text: String): List<WebSearchSource> =
        text.split(Regex("""(?m)(?=^Title:\s*)"""))
            .mapNotNull { block ->
                val title = block.field("Title") ?: return@mapNotNull null
                val url = block.field("URL") ?: return@mapNotNull null
                WebSearchSource(
                    title = title,
                    url = url,
                    snippet = block.field("Content").orEmpty(),
                    domain = "",
                    publishedAt = block.field("Published Date"),
                )
            }

    private fun normalizeSource(source: WebSearchSource): WebSearchSource? {
        val uri = runCatching { URI(source.url.trim()) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val title = source.title.trim().take(MAX_TITLE_CHARS)
        val snippet = source.snippet.trim().take(config.maxSnippetChars)
        if (title.isBlank() || snippet.isBlank()) return null
        return source.copy(
            title = title,
            url = uri.toASCIIString(),
            snippet = snippet,
            domain = uri.host.lowercase().removePrefix("www."),
            publishedAt = source.publishedAt?.trim()?.take(MAX_PUBLISHED_AT_CHARS),
        )
    }

    private fun post(
        payload: JsonObject,
        sessionId: String? = null,
        protocolVersion: String? = null,
        expectBody: Boolean = true,
    ): McpResponse {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(config.timeoutSeconds))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                sessionId?.let { header("MCP-Session-Id", it) }
                protocolVersion?.let { header("MCP-Protocol-Version", it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(payload)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            return McpResponse(success = false)
        }
        val session = response.headers().firstValue("MCP-Session-Id").orElse(sessionId)
        if (!expectBody || response.body().isBlank()) {
            return McpResponse(success = true, sessionId = session)
        }
        val expectedId = payload["id"]?.jsonPrimitive?.contentOrNull
        val body = response.body().toJsonRpcBody(expectedId)
        return McpResponse(success = true, body = json.parseToJsonElement(body).jsonObject, sessionId = session)
    }

    private fun rpcRequest(method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", requestId.getAndIncrement())
        put("method", method)
        put("params", params)
    }

    private data class McpResponse(
        val success: Boolean,
        val body: JsonObject? = null,
        val sessionId: String? = null,
    )

    private companion object {
        const val PROTOCOL_VERSION = "2025-11-25"
        const val MAX_TITLE_CHARS = 300
        const val MAX_PUBLISHED_AT_CHARS = 100
        val SEARCH_TOOL_NAMES = setOf("tavily-search", "tavily_search")
    }
}

fun createWebSearchPort(config: TavilyConfig): WebSearchPort =
    if (config.isAvailable) TavilyMcpWebSearchAdapter(config) else DisabledWebSearchPort()

private fun TavilyConfig.authenticatedEndpoint(): URI {
    val separator = if ("?" in mcpUrl) "&" else "?"
    val encodedKey = URLEncoder.encode(requireNotNull(apiKey), StandardCharsets.UTF_8)
    return URI.create("$mcpUrl${separator}tavilyApiKey=$encodedKey")
}

private fun JsonObject.resultOrThrow(): JsonObject {
    if (this["error"] != null) {
        throw IllegalArgumentException("MCP returned a JSON-RPC error")
    }
    return this["result"]?.jsonObject
        ?: throw IllegalArgumentException("MCP response is missing result")
}

private fun String.toJsonRpcBody(expectedId: String?): String {
    val trimmed = trim()
    if (trimmed.startsWith("{")) return trimmed
    val candidates = trimmed
        .split(Regex("""\r?\n\r?\n"""))
        .mapNotNull { frame ->
            frame.lineSequence()
                .filter { it.startsWith("data:") }
                .joinToString("\n") { it.removePrefix("data:").trimStart() }
                .trim()
                .takeIf(String::isNotEmpty)
        }
    return candidates.firstOrNull { candidate ->
        if (expectedId == null) return@firstOrNull true
        runCatching {
            Json.parseToJsonElement(candidate).jsonObject["id"]
                ?.jsonPrimitive?.contentOrNull == expectedId
        }.getOrDefault(false)
    } ?: throw IllegalArgumentException("SSE response is missing the matching JSON-RPC result")
}

private fun String.field(name: String): String? =
    Regex("""(?ms)^${Regex.escape(name)}:\s*(.*?)(?=^[A-Za-z][A-Za-z ]*:\s|\z)""")
        .find(this)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
