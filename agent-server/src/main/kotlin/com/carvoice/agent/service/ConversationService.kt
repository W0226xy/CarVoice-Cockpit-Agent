package com.carvoice.agent.service

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.agent.ConversationRole
import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.contracts.AssistantCompletedPayload
import com.carvoice.contracts.AssistantDeltaPayload
import com.carvoice.contracts.ChatProtocol
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

class ConversationService(
    private val agent: CarVoiceAgent,
    private val json: Json,
) {
    suspend fun handleChat(
        event: SocketEvent,
        sessionHistory: MutableMap<String, MutableList<ConversationTurn>>,
        emit: suspend (SocketEvent) -> Unit,
    ) {
        val requestId = event.requestId
        val conversationId = event.conversationId
        if (requestId.isNullOrBlank() || conversationId.isNullOrBlank()) {
            emit(errorEvent(requestId, conversationId, "INVALID_REQUEST", "缺少请求标识。"))
            return
        }

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
        val historySnapshot = history.toList()
        history += ConversationTurn(ConversationRole.USER, message)
        val assistantText = StringBuilder()

        try {
            agent.streamReply(historySnapshot, message).collect { chunk ->
                if (chunk.isNotEmpty()) {
                    assistantText.append(chunk)
                    emit(
                        SocketEvent(
                            type = SocketEventType.ASSISTANT_DELTA,
                            requestId = requestId,
                            conversationId = conversationId,
                            payload = json.encodeToJsonElement(AssistantDeltaPayload(chunk)),
                        ),
                    )
                }
            }
            history += ConversationTurn(ConversationRole.ASSISTANT, assistantText.toString())
            while (history.size > 40) history.removeAt(0)
            emit(
                SocketEvent(
                    type = SocketEventType.ASSISTANT_COMPLETED,
                    requestId = requestId,
                    conversationId = conversationId,
                    payload = json.encodeToJsonElement(AssistantCompletedPayload()),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn(
                "LLM streaming request failed; exception chain={}, message={}",
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
