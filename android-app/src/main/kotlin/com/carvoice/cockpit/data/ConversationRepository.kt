package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ConversationRepository {
    val connectionState: StateFlow<ConnectionState>
    val events: Flow<ConversationStreamEvent>

    fun connect()

    suspend fun sendMessage(
        conversationId: String,
        requestId: String,
        message: String,
    ): Result<Unit>

    fun close()
}

sealed interface ConversationStreamEvent {
    val requestId: String?
    val conversationId: String?

    data class AssistantDelta(
        override val requestId: String,
        override val conversationId: String,
        val text: String,
    ) : ConversationStreamEvent

    data class AssistantCompleted(
        override val requestId: String,
        override val conversationId: String,
    ) : ConversationStreamEvent

    data class Error(
        override val requestId: String?,
        override val conversationId: String?,
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : ConversationStreamEvent
}
