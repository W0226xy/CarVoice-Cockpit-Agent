package com.carvoice.agent.agent

import kotlinx.coroutines.flow.Flow

data class ConversationTurn(
    val role: ConversationRole,
    val content: String,
)

enum class ConversationRole {
    USER,
    ASSISTANT,
}

interface CarVoiceAgent {
    fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String>

    suspend fun classifyIntent(message: String): String? = null
}
