package com.carvoice.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class ContractSerializationTest {
    @Test
    fun `chat request uses the documented field names`() {
        val encoded = Json.encodeToString(
            ChatRequest(
                requestId = "req-001",
                sessionId = "session-001",
                message = "打开空调",
            ),
        )

        val decoded = Json.decodeFromString<ChatRequest>(encoded)

        assertEquals("req-001", decoded.requestId)
        assertEquals("session-001", decoded.sessionId)
        assertEquals("打开空调", decoded.message)
    }

    @Test
    fun `socket event round trips shared payload`() {
        val event = SocketEvent(
            type = SocketEventType.CHAT_REQUEST,
            requestId = "request-1",
            conversationId = "conversation-1",
            payload = Json.encodeToJsonElement(ChatRequestPayload("你好")),
        )

        val decoded = Json.decodeFromString<SocketEvent>(Json.encodeToString(event))

        assertEquals(SocketEventType.CHAT_REQUEST, decoded.type)
        assertEquals("request-1", decoded.requestId)
        assertEquals("conversation-1", decoded.conversationId)
    }
}
