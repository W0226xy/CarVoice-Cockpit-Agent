package com.carvoice.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

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
}
