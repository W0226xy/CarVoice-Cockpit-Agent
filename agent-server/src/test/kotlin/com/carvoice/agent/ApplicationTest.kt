package com.carvoice.agent

import com.carvoice.agent.agent.MockStreamingAgent
import com.carvoice.agent.config.LlmConfig
import com.carvoice.agent.config.LlmProviderMode
import com.carvoice.agent.routes.HealthResponse
import com.carvoice.agent.websocket.SocketEventCodec
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.ControlConfirmationResponsePayload
import com.carvoice.contracts.SimulatedCockpitStatePayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class ApplicationTest {
    private val config = LlmConfig(LlmProviderMode.MOCK)
    private val codec = SocketEventCodec()

    @Test
    fun `health endpoint reports provider without secrets`() = testApplication {
        application {
            module(config, MockStreamingAgent(chunkDelayMillis = 0))
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            HealthResponse(
                status = "ok",
                projectName = "CarVoice Cockpit Agent",
                providerMode = "mock",
            ),
            response.body(),
        )
    }

    @Test
    fun `websocket sends ready deltas and completion in order`() = testApplication {
        application {
            module(config, MockStreamingAgent(chunkDelayMillis = 0))
        }
        val client = createClient {
            install(WebSockets)
        }

        client.webSocket("/ws/chat") {
            assertEquals(SocketEventType.CONNECTION_READY, receiveEvent().type)
            send(
                Frame.Text(
                    codec.encode(
                        SocketEvent(
                            type = SocketEventType.CHAT_REQUEST,
                            requestId = "request-1",
                            conversationId = "conversation-1",
                            payload = codec.json.encodeToJsonElement(ChatRequestPayload("你好")),
                        ),
                    ),
                ),
            )

            val events = mutableListOf<SocketEvent>()
            withTimeout(3_000) {
                while (events.none { it.type == SocketEventType.ASSISTANT_COMPLETED }) {
                    events += receiveEvent()
                }
            }
            assertTrue(events.any { it.type == SocketEventType.ASSISTANT_DELTA })
            assertEquals(SocketEventType.ASSISTANT_COMPLETED, events.last().type)
        }
    }

    @Test
    fun `invalid unknown and empty events return safe errors`() = testApplication {
        application {
            module(config, MockStreamingAgent(chunkDelayMillis = 0))
        }

        @Test
        fun `websocket confirms simulated control before updating state`() = testApplication {
            application {
                module(config, MockStreamingAgent(chunkDelayMillis = 0))
            }
            val client = createClient { install(WebSockets) }

            client.webSocket("/ws/chat") {
                receiveEvent()
                send(
                    Frame.Text(
                        codec.encode(
                            SocketEvent(
                                type = SocketEventType.CHAT_REQUEST,
                                requestId = "control-request",
                                conversationId = "conversation-control",
                                payload = codec.json.encodeToJsonElement(
                                    ChatRequestPayload("打开空调并调到 24 度"),
                                ),
                            ),
                        ),
                    ),
                )
                val beforeConfirmation = mutableListOf<SocketEvent>()
                withTimeout(3_000) {
                    while (beforeConfirmation.none {
                            it.type == SocketEventType.ASSISTANT_COMPLETED
                        }
                    ) {
                        beforeConfirmation += receiveEvent()
                    }
                }
                assertTrue(beforeConfirmation.none {
                    it.type == SocketEventType.SIMULATED_COCKPIT_STATE
                })
                val required = beforeConfirmation.first {
                    it.type == SocketEventType.CONTROL_CONFIRMATION_REQUIRED
                }
                val confirmation = codec.json.decodeFromJsonElement<ControlConfirmationRequiredPayload>(
                    requireNotNull(required.payload),
                )

                send(
                    Frame.Text(
                        codec.encode(
                            SocketEvent(
                                type = SocketEventType.CONTROL_CONFIRMATION_RESPONSE,
                                requestId = "confirmation-response",
                                conversationId = "conversation-control",
                                payload = codec.json.encodeToJsonElement(
                                    ControlConfirmationResponsePayload(
                                        confirmation.confirmationId,
                                        true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val afterConfirmation = mutableListOf<SocketEvent>()
                withTimeout(3_000) {
                    while (afterConfirmation.none {
                            it.type == SocketEventType.ASSISTANT_COMPLETED
                        }
                    ) {
                        afterConfirmation += receiveEvent()
                    }
                }
                val stateEvent = afterConfirmation.first {
                    it.type == SocketEventType.SIMULATED_COCKPIT_STATE
                }
                val state = codec.json.decodeFromJsonElement<SimulatedCockpitStatePayload>(
                    requireNotNull(stateEvent.payload),
                )
                assertTrue(state.acEnabled)
                assertEquals(24f, state.targetTemperature)
            }
        }
        val client = createClient {
            install(WebSockets)
        }

        client.webSocket("/ws/chat") {
            receiveEvent()

            send(Frame.Text("{not-json"))
            assertErrorCode("INVALID_JSON")

            send(
                Frame.Text(
                    codec.encode(
                        SocketEvent(
                            type = "unknown",
                            requestId = "request-unknown",
                            conversationId = "conversation-1",
                        ),
                    ),
                ),
            )
            assertErrorCode("UNKNOWN_EVENT")

            send(
                Frame.Text(
                    codec.encode(
                        SocketEvent(
                            type = SocketEventType.CHAT_REQUEST,
                            requestId = "request-empty",
                            conversationId = "conversation-1",
                            payload = codec.json.encodeToJsonElement(ChatRequestPayload("  ")),
                        ),
                    ),
                ),
            )
            assertErrorCode("EMPTY_MESSAGE")
        }
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.receiveEvent():
        SocketEvent = withTimeout(3_000) {
        codec.decode((incoming.receive() as Frame.Text).readText())
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.assertErrorCode(
        expectedCode: String,
    ) {
        val event = receiveEvent()
        assertEquals(SocketEventType.ERROR, event.type)
        val payload = codec.json.decodeFromJsonElement<SocketErrorPayload>(
            requireNotNull(event.payload),
        )
        assertEquals(expectedCode, payload.code)
        assertTrue(payload.message.isNotBlank())
    }
}
