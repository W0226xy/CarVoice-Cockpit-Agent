package com.carvoice.agent.config

data class TavilyConfig(
    val enabled: Boolean,
    val apiKey: String?,
    val mcpUrl: String,
    val timeoutSeconds: Long,
    val maxResults: Int,
    val searchDepth: String,
    val maxSnippetChars: Int,
    val maxContextChars: Int,
) {
    val isAvailable: Boolean
        get() = enabled && !apiKey.isNullOrBlank() && mcpUrl.isAllowedMcpUrl()

    override fun toString(): String =
        "TavilyConfig(enabled=$enabled, apiKey=[REDACTED], " +
            "mcpUrl=${mcpUrl.substringBefore('?')}, " +
            "timeoutSeconds=$timeoutSeconds, maxResults=$maxResults, searchDepth=$searchDepth)"

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()) = TavilyConfig(
            enabled = environment.boolean("TAVILY_ENABLED", false),
            apiKey = environment["TAVILY_API_KEY"]?.trim()?.takeIf(String::isNotEmpty),
            mcpUrl = environment["TAVILY_MCP_URL"]?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: "https://mcp.tavily.com/mcp/",
            timeoutSeconds = environment.longInRange(
                "TAVILY_SEARCH_TIMEOUT_SECONDS",
                default = 15,
                range = 1L..60L,
            ),
            maxResults = environment.intInRange(
                "TAVILY_MAX_RESULTS",
                default = 5,
                range = 1..10,
            ),
            searchDepth = environment["TAVILY_SEARCH_DEPTH"]?.trim()?.lowercase()
                ?.takeIf(String::isNotEmpty)
                ?.also {
                    require(it in allowedDepths) {
                        "TAVILY_SEARCH_DEPTH must be one of ${allowedDepths.sorted()}"
                    }
                }
                ?: "basic",
            maxSnippetChars = environment.intInRange(
                "TAVILY_MAX_SNIPPET_CHARS",
                default = 800,
                range = 100..2_000,
            ),
            maxContextChars = environment.intInRange(
                "TAVILY_MAX_CONTEXT_CHARS",
                default = 6_000,
                range = 500..20_000,
            ),
        )

        private val allowedDepths = setOf("basic", "advanced", "fast", "ultra-fast")
    }
}

private fun Map<String, String>.boolean(name: String, default: Boolean): Boolean =
    get(name)?.trim()?.lowercase()?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> error("$name must be true or false")
        }
    } ?: default

private fun Map<String, String>.longInRange(
    name: String,
    default: Long,
    range: LongRange,
): Long = get(name)?.trim()?.takeIf(String::isNotEmpty)?.toLongOrNull()?.let {
    require(it in range) { "$name must be in ${range.first}..${range.last}" }
    it
} ?: if (containsKey(name) && get(name)?.isNotBlank() == true) {
    error("$name must be a number")
} else {
    default
}

private fun Map<String, String>.intInRange(
    name: String,
    default: Int,
    range: IntRange,
): Int = get(name)?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull()?.let {
    require(it in range) { "$name must be in ${range.first}..${range.last}" }
    it
} ?: if (containsKey(name) && get(name)?.isNotBlank() == true) {
    error("$name must be a number")
} else {
    default
}

private fun String.isAllowedMcpUrl(): Boolean =
    runCatching { java.net.URI(this) }.getOrNull()?.let {
        when (it.scheme?.lowercase()) {
            "https" -> !it.host.isNullOrBlank()
            "http" -> it.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
            else -> false
        }
    } == true
