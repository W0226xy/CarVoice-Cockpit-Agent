package com.carvoice.contracts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatRequest(
    val requestId: String,
    val sessionId: String,
    val message: String,
    val locale: String = "zh-CN",
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class ChatResponse(
    val requestId: String,
    val sessionId: String,
    val message: String,
    val finishReason: FinishReason,
    val toolCalls: List<ToolCall> = emptyList(),
)

@Serializable
data class ChatChunk(
    val requestId: String,
    val sessionId: String,
    val sequence: Int,
    val content: String,
    val done: Boolean = false,
)

@Serializable
data class ToolCall(
    val callId: String,
    val toolName: String,
    val arguments: JsonObject,
)

@Serializable
enum class FinishReason {
    COMPLETED,
    TOOL_CALL,
    CANCELLED,
    ERROR,
}
