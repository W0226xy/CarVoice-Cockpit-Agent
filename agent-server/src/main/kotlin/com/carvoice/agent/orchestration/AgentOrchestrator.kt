package com.carvoice.agent.orchestration

import com.carvoice.contracts.ChatRequest
import com.carvoice.contracts.ChatResponse

interface AgentOrchestrator {
    suspend fun respond(request: ChatRequest): ChatResponse
}
