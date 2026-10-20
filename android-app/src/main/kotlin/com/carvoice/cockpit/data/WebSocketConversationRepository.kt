package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.contracts.AssistantDeltaPayload
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class WebSocketConversationRepository(
    private val connection: AgentConnection,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : ConversationRepository {
    override val connectionState: StateFlow<ConnectionState> = connection.state

    override val events: Flow<ConversationStreamEvent> =
        connection.events.mapNotNull { event ->
            when (event.type) {
                SocketEventType.CONNECTION_READY -> {
                    connection.markReady()
                    null
                }

                SocketEventType.ASSISTANT_DELTA -> {
                    val requestId = event.requestId ?: return@mapNotNull malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return@mapNotNull malformedEvent(event)
                    val payload = event.payload?.let {
                        runCatching {
                            json.decodeFromJsonElement<AssistantDeltaPayload>(it)
                        }.getOrNull()
                    } ?: return@mapNotNull malformedEvent(event)
                    ConversationStreamEvent.AssistantDelta(
                        requestId = requestId,
                        conversationId = conversationId,
                        text = payload.text,
                    )
                }

                SocketEventType.ASSISTANT_COMPLETED -> {
                    val requestId = event.requestId ?: return@mapNotNull malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return@mapNotNull malformedEvent(event)
                    ConversationStreamEvent.AssistantCompleted(requestId, conversationId)
                }

                SocketEventType.ERROR -> {
                    val payload = event.payload?.let {
                        runCatching {
                            json.decodeFromJsonElement<SocketErrorPayload>(it)
                        }.getOrNull()
                    } ?: SocketErrorPayload("PROTOCOL_ERROR", "服务返回了无效错误。")
                    ConversationStreamEvent.Error(
                        requestId = event.requestId,
                        conversationId = event.conversationId,
                        code = payload.code,
                        message = payload.message,
                        retryable = payload.retryable,
                    )
                }

                else -> null
            }
        }

    override fun connect() = connection.connect()

    override suspend fun sendMessage(
        conversationId: String,
        requestId: String,
        message: String,
    ): Result<Unit> = connection.send(
        SocketEvent(
            type = SocketEventType.CHAT_REQUEST,
            requestId = requestId,
            conversationId = conversationId,
            payload = json.encodeToJsonElement(ChatRequestPayload(message)),
        ),
    )

    override fun close() = connection.close()

    private fun malformedEvent(event: SocketEvent) = ConversationStreamEvent.Error(
        requestId = event.requestId,
        conversationId = event.conversationId,
        code = "PROTOCOL_ERROR",
        message = "服务返回了无法识别的消息。",
        retryable = true,
    )
}
