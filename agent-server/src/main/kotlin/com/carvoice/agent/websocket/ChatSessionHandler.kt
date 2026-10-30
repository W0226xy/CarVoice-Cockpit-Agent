package com.carvoice.agent.websocket

import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.agent.service.ConversationService
import com.carvoice.contracts.ConnectionReadyPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

class ChatSessionHandler(
    private val conversationService: ConversationService,
    private val providerMode: String,
    private val codec: SocketEventCodec,
) {
    suspend fun handle(session: DefaultWebSocketServerSession) = coroutineScope {
        val sendMutex = Mutex()
        val history = mutableMapOf<String, MutableList<ConversationTurn>>()
        var activeJob: Job? = null
        val connectionId = UUID.randomUUID().toString()

        suspend fun emit(event: SocketEvent) {
            sendMutex.withLock {
                session.send(Frame.Text(codec.encode(event)))
            }
        }

        emit(
            SocketEvent(
                type = SocketEventType.CONNECTION_READY,
                payload = codec.json.encodeToJsonElement(ConnectionReadyPayload(providerMode)),
            ),
        )

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) {
                    emit(
                        conversationService.errorEvent(
                            null,
                            null,
                            "UNSUPPORTED_FRAME",
                            "仅支持文本消息。",
                        ),
                    )
                    continue
                }
                val event = runCatching { codec.decode(frame.readText()) }.getOrElse {
                    emit(
                        conversationService.errorEvent(
                            null,
                            null,
                            "INVALID_JSON",
                            "消息格式无效。",
                        ),
                    )
                    continue
                }
                if (event.type !in setOf(
                        SocketEventType.CHAT_REQUEST,
                        SocketEventType.CONTROL_CONFIRMATION_RESPONSE,
                        SocketEventType.CONVERSATION_SYNC,
                        SocketEventType.NAVIGATION_EXECUTION_RESULT,
                    )
                ) {
                    emit(
                        conversationService.errorEvent(
                            event.requestId,
                            event.conversationId,
                            "UNKNOWN_EVENT",
                            "不支持的事件类型。",
                        ),
                    )
                    continue
                }
                // A navigation task intentionally waits for this event. It must bypass the
                // single active task guard, but the coordinator still verifies all IDs and the
                // connection token before it can complete anything.
                if (event.type == SocketEventType.NAVIGATION_EXECUTION_RESULT) {
                    conversationService.handleNavigationExecutionResult(event, connectionId)
                    continue
                }
                if (activeJob?.isActive == true) {
                    emit(
                        conversationService.errorEvent(
                            event.requestId,
                            event.conversationId,
                            "REQUEST_IN_PROGRESS",
                            "上一条回复仍在生成中。",
                            retryable = true,
                        ),
                    )
                    continue
                }
                if (event.type == SocketEventType.CONVERSATION_SYNC) {
                    conversationService.handleConversationSync(event, history, ::emit)
                    continue
                }
                activeJob = launch {
                    when (event.type) {
                        SocketEventType.CHAT_REQUEST ->
                            conversationService.handleChat(event, history, connectionId, ::emit)
                        SocketEventType.CONTROL_CONFIRMATION_RESPONSE ->
                            conversationService.handleControlConfirmation(event, history, ::emit)
                        else -> Unit
                    }
                }
            }
        } finally {
            conversationService.cancelNavigationForConnection(connectionId)
            activeJob?.cancelAndJoin()
        }
    }
}
