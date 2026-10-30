package com.carvoice.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `intent result validates confidence and round trips slots`() {
        val result = IntentResult(
            IntentType.NAVIGATION,
            0.92,
            mapOf("destination" to "扬州大学"),
        )

        assertEquals(result, Json.decodeFromString<IntentResult>(Json.encodeToString(result)))
        assertFailsWith<IllegalArgumentException> {
            IntentResult(IntentType.UNKNOWN, 1.1)
        }
    }

    @Test
    fun `cockpit protocol payloads round trip`() {
        val confirmation = ControlConfirmationRequiredPayload("confirm-1", "打开空调", 1234)
        val response = ControlConfirmationResponsePayload("confirm-1", true)
        val progress = ToolProgressPayload(
            "confirm-1",
            "simulated_cockpit_control",
            ToolCallStatus.COMPLETED,
            "模拟座舱控制已完成",
        )
        val state = SimulatedCockpitStatePayload(acEnabled = true, targetTemperature = 24f)

        assertEquals(
            confirmation,
            Json.decodeFromString<ControlConfirmationRequiredPayload>(
                Json.encodeToString(confirmation),
            ),
        )
        assertEquals(
            response,
            Json.decodeFromString<ControlConfirmationResponsePayload>(
                Json.encodeToString(response),
            ),
        )
        assertEquals(
            progress,
            Json.decodeFromString<ToolProgressPayload>(Json.encodeToString(progress)),
        )
        assertEquals(
            state,
            Json.decodeFromString<SimulatedCockpitStatePayload>(Json.encodeToString(state)),
        )
    }

    @Test
    fun `navigation action and result payloads round trip`() {
        val action = NavigationActionPayload("action-1", NavigationActionType.OPEN_MAP_SEARCH, "扬州东站")
        val result = NavigationExecutionResultPayload(
            "action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED,
        )
        assertEquals(action, Json.decodeFromString<NavigationActionPayload>(Json.encodeToString(action)))
        assertEquals(
            result,
            Json.decodeFromString<NavigationExecutionResultPayload>(Json.encodeToString(result)),
        )
    }

    @Test
    fun `web search sources event round trips and unknown event stays compatible`() {
        val payload = WebSearchSourcesPayload(
            conversationId = "conversation-1",
            messageId = "request-1",
            query = "AI 新闻",
            sources = listOf(
                WebSearchSourcePayload(
                    1,
                    "来源标题",
                    "https://example.com/news",
                    "example.com",
                    "摘要",
                    "2026-10-20",
                ),
            ),
        )
        assertEquals(
            payload,
            Json.decodeFromString<WebSearchSourcesPayload>(Json.encodeToString(payload)),
        )

        val unknown = SocketEvent(type = "future_event")
        assertEquals(
            "future_event",
            Json.decodeFromString<SocketEvent>(Json.encodeToString(unknown)).type,
        )
    }
}
