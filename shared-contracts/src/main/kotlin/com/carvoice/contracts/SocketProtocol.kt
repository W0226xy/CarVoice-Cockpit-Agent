package com.carvoice.contracts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object SocketEventType {
    const val CHAT_REQUEST = "chat_request"
    const val CONNECTION_READY = "connection_ready"
    const val ASSISTANT_DELTA = "assistant_delta"
    const val ASSISTANT_COMPLETED = "assistant_completed"
    const val ERROR = "error"
    const val TOOL_PROGRESS = "tool_progress"
}

object ChatProtocol {
    const val MAX_MESSAGE_LENGTH = 4_000
}

@Serializable
data class SocketEvent(
    val type: String,
    val requestId: String? = null,
    val conversationId: String? = null,
    val payload: JsonElement? = null,
)

@Serializable
data class ChatRequestPayload(
    val message: String,
)

@Serializable
data class ConnectionReadyPayload(
    val providerMode: String,
)

@Serializable
data class AssistantDeltaPayload(
    val text: String,
)

@Serializable
data class AssistantCompletedPayload(
    val finishReason: String = "completed",
)

@Serializable
data class SocketErrorPayload(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)
