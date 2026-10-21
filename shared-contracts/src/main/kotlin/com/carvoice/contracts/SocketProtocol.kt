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
    const val CONTROL_CONFIRMATION_REQUIRED = "control_confirmation_required"
    const val CONTROL_CONFIRMATION_RESPONSE = "control_confirmation_response"
    const val SIMULATED_COCKPIT_STATE = "simulated_cockpit_state"
    const val WEB_SEARCH_SOURCES = "web_search_sources"
    const val KNOWLEDGE_SOURCES = "knowledge_sources"
    const val CONVERSATION_SYNC = "conversation_sync"
}

object ChatProtocol {
    const val MAX_MESSAGE_LENGTH = 4_000
    const val MAX_SYNC_MESSAGES = 20
}

/** A deliberately small, client-owned history projection used after reconnecting. */
@Serializable
data class ConversationSyncPayload(
    val messages: List<ConversationSyncMessage>,
)

@Serializable
data class ConversationSyncMessage(
    val role: ConversationSyncRole,
    val content: String,
)

@Serializable
enum class ConversationSyncRole {
    USER,
    ASSISTANT,
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
    val knowledgeBaseId: String? = null,
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

@Serializable
data class ControlConfirmationRequiredPayload(
    val confirmationId: String,
    val description: String,
    val expiresAtEpochMillis: Long,
)

@Serializable
data class ControlConfirmationResponsePayload(
    val confirmationId: String,
    val confirmed: Boolean,
)

@Serializable
data class ToolProgressPayload(
    val callId: String,
    val toolName: String,
    val status: ToolCallStatus,
    val message: String,
)

@Serializable
data class SimulatedCockpitStatePayload(
    val acEnabled: Boolean = false,
    val targetTemperature: Float = 24f,
    val fanLevel: Int = 1,
    val frontDefrostEnabled: Boolean = false,
    val driverWindowPercent: Int = 0,
    val passengerWindowPercent: Int = 0,
)

@Serializable
data class WebSearchSourcesPayload(
    val conversationId: String,
    val messageId: String,
    val query: String,
    val sources: List<WebSearchSourcePayload>,
)

@Serializable
data class WebSearchSourcePayload(
    val index: Int,
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String,
    val publishedAt: String? = null,
)

@Serializable data class KnowledgeSourcesPayload(val conversationId:String,val messageId:String,val knowledgeBaseId:String,val query:String,val sources:List<KnowledgeSourcePayload>)
@Serializable data class KnowledgeSourcePayload(val index:Int,val documentId:String,val documentName:String,val pageNumber:Int?=null,val sectionTitle:String?=null,val snippet:String)
