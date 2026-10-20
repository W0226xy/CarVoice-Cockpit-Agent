package com.carvoice.agent.config

enum class LlmProviderMode(val value: String) {
    OPENAI_COMPATIBLE("openai_compatible"),
}

data class LlmConfig(
    val providerMode: LlmProviderMode,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): LlmConfig {
            val modeValue = environment["LLM_PROVIDER"]?.trim()?.lowercase()
                ?: LlmProviderMode.OPENAI_COMPATIBLE.value
            val mode = LlmProviderMode.entries.firstOrNull { it.value == modeValue }
                ?: error("LLM_PROVIDER must be openai_compatible; Mock LLM is not supported")

            return LlmConfig(
                providerMode = mode,
                baseUrl = environment.required("OPENAI_BASE_URL").toOpenAiServiceBaseUrl(),
                apiKey = environment.required("OPENAI_API_KEY"),
                model = environment.required("OPENAI_MODEL"),
            )
        }
    }
}

private fun Map<String, String>.required(name: String): String =
    get(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("$name is required for OpenAI-compatible LLM configuration")

/**
 * Koog appends `v1/chat/completions` itself. Providers commonly document their
 * endpoint with a trailing `/v1`, which would otherwise result in `/v1/v1/...`.
 */
private fun String.toOpenAiServiceBaseUrl(): String =
    trimEnd('/').removeSuffix("/v1").ifBlank {
        error("OPENAI_BASE_URL must contain a service base URL")
    }
