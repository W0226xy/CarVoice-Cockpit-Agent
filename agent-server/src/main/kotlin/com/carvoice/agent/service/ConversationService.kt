package com.carvoice.agent.service

import com.carvoice.agent.agent.ConversationRole
import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.agent.intent.IntentRouter
import com.carvoice.agent.routing.TaskRouter
import com.carvoice.agent.tasks.TaskContext
import com.carvoice.agent.tasks.TaskResult
import com.carvoice.agent.tasks.TaskStreamEvent
import com.carvoice.contracts.AssistantCompletedPayload
import com.carvoice.contracts.AssistantDeltaPayload
import com.carvoice.contracts.ChatProtocol
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.ControlConfirmationResponsePayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import com.carvoice.contracts.ToolCallStatus
import com.carvoice.contracts.ToolProgressPayload
import com.carvoice.contracts.WebSearchSourcesPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

class ConversationService(
    private val intentRouter: IntentRouter,
    private val taskRouter: TaskRouter,
    private val json: Json,
) {
    suspend fun handleChat(
        event: SocketEvent,
        sessionHistory: MutableMap<String, MutableList<ConversationTurn>>,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        val identifiers = validateIdentifiers(event, emit) ?: return
        val (requestId, conversationId) = identifiers
        val payload = runCatching {
            event.payload?.let { json.decodeFromJsonElement<ChatRequestPayload>(it) }
        }.getOrNull()
        val message = payload?.message?.trim().orEmpty()
        when {
            message.isEmpty() -> {
                emit(errorEvent(requestId, conversationId, "EMPTY_MESSAGE", "消息不能为空。"))
                return
            }
            message.length > ChatProtocol.MAX_MESSAGE_LENGTH -> {
                emit(
                    errorEvent(
                        requestId,
                        conversationId,
                        "MESSAGE_TOO_LONG",
                        "消息长度不能超过 ${ChatProtocol.MAX_MESSAGE_LENGTH} 个字符。",
                    ),
                )
                return
            }
        }

        val history = sessionHistory.getOrPut(conversationId) { mutableListOf() }
        val snapshot = history.toList()
        history += ConversationTurn(ConversationRole.USER, message)
        try {
            val intent = intentRouter.classify(message)
            val task = taskRouter.route(intent.intent)
            logger.info(
                "Intent routed conversationId={}, intent={}, confidence={}, slotFields={}, route={}",
                conversationId,
                intent.intent,
                intent.confidence,
                intent.slots.keys.sorted(),
                task::class.simpleName,
            )
            val result = task.execute(
                TaskContext(requestId, conversationId, message, intent, snapshot),
            )
            emitResult(result, requestId, conversationId, history, emit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn(
                "Chat request failed; exception chain={}, message={}",
                error.exceptionChainDescription(),
                error.safeLogMessage(),
            )
            emit(
                errorEvent(
                    requestId,
                    conversationId,
                    "MODEL_ERROR",
                    "模型服务暂时不可用，请稍后重试。",
                    retryable = true,
                ),
            )
        }
    }

    suspend fun handleControlConfirmation(
        event: SocketEvent,
        sessionHistory: MutableMap<String, MutableList<ConversationTurn>>,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        val identifiers = validateIdentifiers(event, emit) ?: return
        val (requestId, conversationId) = identifiers
        val payload = runCatching {
            event.payload?.let {
                json.decodeFromJsonElement<ControlConfirmationResponsePayload>(it)
            }
        }.getOrNull()
        if (payload == null || payload.confirmationId.isBlank()) {
            emit(
                errorEvent(
                    requestId,
                    conversationId,
                    "INVALID_CONFIRMATION",
                    "模拟座舱确认响应格式无效。",
                ),
            )
            return
        }

        val result = taskRouter.simulatedCockpitTask.confirm(
            conversationId,
            payload.confirmationId,
            payload.confirmed,
        )
        val history = sessionHistory.getOrPut(conversationId) { mutableListOf() }
        emitResult(result, requestId, conversationId, history, emit)
    }

    private suspend fun emitResult(
        result: TaskResult,
        requestId: String,
        conversationId: String,
        history: MutableList<ConversationTurn>,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        when (result) {
            is TaskResult.Streaming -> {
                val text = StringBuilder()
                result.events.collect { streamEvent ->
                    when (streamEvent) {
                        is TaskStreamEvent.Delta -> if (streamEvent.text.isNotEmpty()) {
                            text.append(streamEvent.text)
                            emitDelta(requestId, conversationId, streamEvent.text, emit)
                        }
                        is TaskStreamEvent.ToolProgress -> emit(
                            SocketEvent(
                                type = SocketEventType.TOOL_PROGRESS,
                                requestId = requestId,
                                conversationId = conversationId,
                                payload = json.encodeToJsonElement(
                                    ToolProgressPayload(
                                        streamEvent.callId,
                                        streamEvent.toolName,
                                        streamEvent.status,
                                        streamEvent.message,
                                    ),
                                ),
                            ),
                        )
                        is TaskStreamEvent.SearchSources -> emit(
                            SocketEvent(
                                type = SocketEventType.WEB_SEARCH_SOURCES,
                                requestId = requestId,
                                conversationId = conversationId,
                                payload = json.encodeToJsonElement<WebSearchSourcesPayload>(
                                    streamEvent.payload,
                                ),
                            ),
                        )
                    }
                }
                complete(requestId, conversationId, text.toString(), history, emit)
            }
            is TaskResult.Reply -> {
                emitDelta(requestId, conversationId, result.text, emit)
                complete(requestId, conversationId, result.text, history, emit)
            }
            is TaskResult.Confirmation -> {
                emitDelta(requestId, conversationId, result.text, emit)
                emit(
                    SocketEvent(
                        type = SocketEventType.TOOL_PROGRESS,
                        requestId = requestId,
                        conversationId = conversationId,
                        payload = json.encodeToJsonElement(
                            ToolProgressPayload(
                                callId = result.payload.confirmationId,
                                toolName = "simulated_cockpit_control",
                                status = ToolCallStatus.PENDING_CONFIRMATION,
                                message = "等待模拟座舱控制确认",
                            ),
                        ),
                    ),
                )
                emit(
                    SocketEvent(
                        type = SocketEventType.CONTROL_CONFIRMATION_REQUIRED,
                        requestId = requestId,
                        conversationId = conversationId,
                        payload = json.encodeToJsonElement(result.payload),
                    ),
                )
                complete(requestId, conversationId, result.text, history, emit)
            }
            is TaskResult.ControlCompleted -> {
                val status = if (result.cancelled) {
                    ToolCallStatus.CANCELLED
                } else {
                    ToolCallStatus.COMPLETED
                }
                emit(
                    SocketEvent(
                        type = SocketEventType.TOOL_PROGRESS,
                        requestId = requestId,
                        conversationId = conversationId,
                        payload = json.encodeToJsonElement(
                            ToolProgressPayload(
                                callId = result.confirmationId,
                                toolName = "simulated_cockpit_control",
                                status = status,
                                message = if (result.cancelled) {
                                    "已取消模拟座舱控制"
                                } else {
                                    "模拟座舱控制已完成"
                                },
                            ),
                        ),
                    ),
                )
                result.state?.let {
                    emit(
                        SocketEvent(
                            type = SocketEventType.SIMULATED_COCKPIT_STATE,
                            requestId = requestId,
                            conversationId = conversationId,
                            payload = json.encodeToJsonElement(it),
                        ),
                    )
                }
                emitDelta(requestId, conversationId, result.text, emit)
                complete(requestId, conversationId, result.text, history, emit)
            }
            is TaskResult.Failure -> {
                emit(errorEvent(requestId, conversationId, result.code, result.message))
            }
        }
    }

    private suspend fun emitDelta(
        requestId: String,
        conversationId: String,
        text: String,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        emit(
            SocketEvent(
                type = SocketEventType.ASSISTANT_DELTA,
                requestId = requestId,
                conversationId = conversationId,
                payload = json.encodeToJsonElement(AssistantDeltaPayload(text)),
            ),
        )
    }

    private suspend fun complete(
        requestId: String,
        conversationId: String,
        text: String,
        history: MutableList<ConversationTurn>,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        history += ConversationTurn(ConversationRole.ASSISTANT, text)
        while (history.size > 40) history.removeAt(0)
        emit(
            SocketEvent(
                type = SocketEventType.ASSISTANT_COMPLETED,
                requestId = requestId,
                conversationId = conversationId,
                payload = json.encodeToJsonElement(AssistantCompletedPayload()),
            ),
        )
    }

    private suspend fun validateIdentifiers(
        event: SocketEvent,
        emit: suspend (SocketEvent) -> Unit,
    ): Pair<String, String>? {
        val requestId = event.requestId
        val conversationId = event.conversationId
        if (requestId.isNullOrBlank() || conversationId.isNullOrBlank()) {
            emit(errorEvent(requestId, conversationId, "INVALID_REQUEST", "缺少请求标识。"))
            return null
        }
        return requestId to conversationId
    }

    fun errorEvent(
        requestId: String?,
        conversationId: String?,
        code: String,
        message: String,
        retryable: Boolean = false,
    ) = SocketEvent(
        type = SocketEventType.ERROR,
        requestId = requestId,
        conversationId = conversationId,
        payload = json.encodeToJsonElement(SocketErrorPayload(code, message, retryable)),
    )

    private fun Throwable.exceptionChainDescription(): String =
        generateSequence(this) { it.cause }
            .map { it::class.qualifiedName ?: it::class.simpleName ?: "UnknownException" }
            .joinToString(" -> ")

    private fun Throwable.safeLogMessage(): String =
        message
            ?.replace(Regex("(?i)(bearer\\s+)[^\\s,;]+"), "$1[REDACTED]")
            ?.replace(Regex("(?i)(api[_-]?key[=:]\\s*)[^\\s,;]+"), "$1[REDACTED]")
            ?.take(1_000)
            ?: "<no message>"

    private companion object {
        val logger = LoggerFactory.getLogger(ConversationService::class.java)
    }
}
