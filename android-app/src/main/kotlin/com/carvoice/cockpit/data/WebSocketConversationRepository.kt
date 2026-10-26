package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.contracts.AssistantDeltaPayload
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.ControlConfirmationResponsePayload
import com.carvoice.contracts.SimulatedCockpitStatePayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import com.carvoice.contracts.ToolProgressPayload
import com.carvoice.contracts.WebSearchSourcesPayload
import com.carvoice.contracts.WebSearchSourcePayload
import com.carvoice.contracts.KnowledgeSourcesPayload
import com.carvoice.contracts.ConversationSyncPayload
import com.carvoice.contracts.ConversationSyncMessage as ProtocolSyncMessage
import com.carvoice.contracts.ConversationSyncRole
import java.net.URI
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
        connection.events.mapNotNull(::decodeEvent)

    internal fun decodeEvent(event: SocketEvent): ConversationStreamEvent? {
        return when (event.type) {
                SocketEventType.CONNECTION_READY -> {
                    connection.markReady()
                    null
                }

                SocketEventType.ASSISTANT_DELTA -> {
                    val requestId = event.requestId ?: return malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return malformedEvent(event)
                    val payload = event.payload?.let {
                        runCatching {
                            json.decodeFromJsonElement<AssistantDeltaPayload>(it)
                        }.getOrNull()
                    } ?: return malformedEvent(event)
                    ConversationStreamEvent.AssistantDelta(
                        requestId = requestId,
                        conversationId = conversationId,
                        text = payload.text,
                    )
                }

                SocketEventType.ASSISTANT_COMPLETED -> {
                    val requestId = event.requestId ?: return malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return malformedEvent(event)
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

                SocketEventType.CONTROL_CONFIRMATION_REQUIRED -> {
                    val requestId = event.requestId ?: return malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return malformedEvent(event)
                    val payload = event.decodePayload<ControlConfirmationRequiredPayload>()
                        ?: return malformedEvent(event)
                    ConversationStreamEvent.ControlConfirmationRequired(
                        requestId,
                        conversationId,
                        payload.confirmationId,
                        payload.description,
                        payload.expiresAtEpochMillis,
                    )
                }

                SocketEventType.TOOL_PROGRESS -> {
                    val requestId = event.requestId ?: return malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return malformedEvent(event)
                    val payload = event.decodePayload<ToolProgressPayload>()
                        ?: return malformedEvent(event)
                    ConversationStreamEvent.ToolProgress(
                        requestId,
                        conversationId,
                        payload.callId,
                        payload.toolName,
                        payload.status,
                        payload.message,
                    )
                }

                SocketEventType.SIMULATED_COCKPIT_STATE -> {
                    val requestId = event.requestId ?: return malformedEvent(event)
                    val conversationId =
                        event.conversationId ?: return malformedEvent(event)
                    val payload = event.decodePayload<SimulatedCockpitStatePayload>()
                        ?: return malformedEvent(event)
                    ConversationStreamEvent.SimulatedCockpitState(
                        requestId,
                        conversationId,
                        payload,
                    )
                }

                SocketEventType.WEB_SEARCH_SOURCES -> {
                    val requestId = event.requestId ?: return null
                    val conversationId = event.conversationId ?: return null
                    val payload = event.decodePayload<WebSearchSourcesPayload>()
                        ?: return null
                    if (payload.conversationId != conversationId ||
                        payload.messageId != requestId ||
                        payload.messageId.isBlank() ||
                        payload.query.isBlank()
                    ) {
                        return null
                    }
                    val sources = payload.sources.mapNotNull(WebSearchSourcePayload::toSafeSource)
                    if (sources.isEmpty()) return null
                    ConversationStreamEvent.WebSearchSources(
                        requestId,
                        conversationId,
                        payload.messageId,
                        payload.query.take(200),
                        sources,
                    )
                }
                SocketEventType.KNOWLEDGE_SOURCES -> {
                    val requestId=event.requestId ?: return null; val conversationId=event.conversationId ?: return null
                    val payload=event.decodePayload<KnowledgeSourcesPayload>() ?: return null
                    if(payload.conversationId!=conversationId||payload.messageId!=requestId||payload.knowledgeBaseId.isBlank()) return null
                    ConversationStreamEvent.KnowledgeSources(requestId,conversationId,payload.messageId,payload.knowledgeBaseId,payload.query.take(200),payload.sources.filter { it.index>0&&it.documentId.isNotBlank() }.distinctBy { it.index to it.documentId })
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
    override suspend fun sendMessage(conversationId:String,requestId:String,message:String,knowledgeBaseId:String?):Result<Unit> = connection.send(SocketEvent(type=SocketEventType.CHAT_REQUEST,requestId=requestId,conversationId=conversationId,payload=json.encodeToJsonElement(ChatRequestPayload(message,knowledgeBaseId))))

    override suspend fun sendControlConfirmation(
        conversationId: String,
        requestId: String,
        confirmationId: String,
        confirmed: Boolean,
    ): Result<Unit> = connection.send(
        SocketEvent(
            type = SocketEventType.CONTROL_CONFIRMATION_RESPONSE,
            requestId = requestId,
            conversationId = conversationId,
            payload = json.encodeToJsonElement(
                ControlConfirmationResponsePayload(confirmationId, confirmed),
            ),
        ),
    )

    override suspend fun syncConversation(
        conversationId: String,
        messages: List<ConversationSyncMessage>,
    ): Result<Unit> {
        if (messages.size > 20 || messages.any { it.content.isBlank() || it.content.length > 4_000 }) {
            return Result.failure(IllegalArgumentException("Invalid conversation sync payload"))
        }
        return connection.send(
            SocketEvent(
                type = SocketEventType.CONVERSATION_SYNC,
                conversationId = conversationId,
                payload = json.encodeToJsonElement(
                    ConversationSyncPayload(messages.map {
                        ProtocolSyncMessage(
                            role = if (it.author == com.carvoice.cockpit.model.MessageAuthor.USER) {
                                ConversationSyncRole.USER
                            } else {
                                ConversationSyncRole.ASSISTANT
                            },
                            content = it.content,
                        )
                    }),
                ),
            ),
        )
    }

    override fun close() = connection.close()

    private fun malformedEvent(event: SocketEvent) = ConversationStreamEvent.Error(
        requestId = event.requestId,
        conversationId = event.conversationId,
        code = "PROTOCOL_ERROR",
        message = "服务返回了无法识别的消息。",
        retryable = true,
    )

    private inline fun <reified T> SocketEvent.decodePayload(): T? =
        payload?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }
}

private fun WebSearchSourcePayload.toSafeSource(): WebSearchSourcePayload? {
    if (index <= 0 || title.isBlank()) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        return null
    }
    return copy(
        title = title.trim().take(300),
        url = uri.toASCIIString(),
        domain = uri.host.lowercase().removePrefix("www."),
        snippet = snippet.trim().take(1_000),
        publishedAt = publishedAt?.trim()?.take(100),
    )
}
