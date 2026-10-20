package com.carvoice.agent.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchDecision(
    val shouldSearch: Boolean,
    val reason: String,
    val query: String? = null,
    val confidence: Double = 1.0,
) {
    init {
        require(reason.isNotBlank()) { "Search decision reason must not be blank" }
        require(confidence in 0.0..1.0) { "Search decision confidence must be between 0 and 1" }
        require(!shouldSearch || !query.isNullOrBlank()) {
            "Search query is required when shouldSearch is true"
        }
    }
}

data class WebSearchResult(
    val query: String,
    val sources: List<WebSearchSource>,
)

data class WebSearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val domain: String,
    val publishedAt: String? = null,
)

sealed interface WebSearchOutcome {
    data class Success(val result: WebSearchResult) : WebSearchOutcome
    data class Failure(val reason: WebSearchFailureReason) : WebSearchOutcome
}

enum class WebSearchFailureReason {
    DISABLED,
    TIMEOUT,
    REMOTE_ERROR,
    INVALID_RESPONSE,
    NO_RESULTS,
}
