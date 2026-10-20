package com.carvoice.agent.search

interface WebSearchPort {
    suspend fun search(query: String): WebSearchOutcome
}

class DisabledWebSearchPort : WebSearchPort {
    override suspend fun search(query: String): WebSearchOutcome =
        WebSearchOutcome.Failure(WebSearchFailureReason.DISABLED)
}
