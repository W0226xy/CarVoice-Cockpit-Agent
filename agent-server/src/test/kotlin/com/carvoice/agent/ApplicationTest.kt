package com.carvoice.agent

import com.carvoice.agent.config.LlmConfig
import com.carvoice.agent.config.LlmProviderMode
import com.carvoice.agent.config.TavilyConfig
import com.carvoice.agent.routes.HealthResponse
import com.carvoice.agent.search.WebSearchOutcome
import com.carvoice.agent.search.WebSearchPort
import com.carvoice.agent.search.WebSearchResult
import com.carvoice.agent.search.WebSearchSource
import com.carvoice.agent.testing.FakeCarVoiceAgent
import com.carvoice.agent.websocket.SocketEventCodec
import com.carvoice.contracts.ChatRequestPayload
import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.ControlConfirmationResponsePayload
import com.carvoice.contracts.SimulatedCockpitStatePayload
import com.carvoice.contracts.SocketErrorPayload
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import com.carvoice.contracts.ToolCallStatus
import com.carvoice.contracts.ToolProgressPayload
import com.carvoice.contracts.WebSearchSourcesPayload
import com.carvoice.contracts.ConversationSyncPayload
import com.carvoice.contracts.ConversationSyncMessage
import com.carvoice.contracts.ConversationSyncRole
import com.carvoice.contracts.NavigationActionPayload
import com.carvoice.contracts.NavigationExecutionResultPayload
import com.carvoice.contracts.NavigationExecutionStatus
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
    private val config = LlmConfig(
        providerMode = LlmProviderMode.OPENAI_COMPATIBLE,
        baseUrl = "https://test.invalid",
        apiKey = "test-only-key",
        model = "test-model",
    )
    private val agent = FakeCarVoiceAgent(listOf("测试", "回复"))
    private val tavilyConfig = TavilyConfig.fromEnvironment(emptyMap())
    private val codec = SocketEventCodec()

    @Test
    fun `health endpoint reports provider without secrets`() = testApplication {
        application {
            module(config, agent, tavilyConfig)
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
                providerMode = "openai_compatible",
            ),
            response.body(),
        )
    }

    @Test
    fun `websocket sends ready deltas and completion in order`() = testApplication {
        application {
            module(config, agent, tavilyConfig)
        }
        val client = createClient {
            install(WebSockets)
        }

        client.webSocket("/ws/chat") {
            val ready = receiveEvent()
            assertEquals(SocketEventType.CONNECTION_READY, ready.type)
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
    fun `navigation waits for matching client result before final reply`() = testApplication {
        application { module(config, agent, tavilyConfig) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/chat") {
            receiveEvent()
            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.CHAT_REQUEST,
                "navigation-request",
                "navigation-conversation",
                codec.json.encodeToJsonElement(ChatRequestPayload("导航到扬州东站")),
            ))))
            var actionEvent: SocketEvent? = null
            withTimeout(3_000) {
                while (actionEvent == null) {
                    val candidate = receiveEvent()
                    if (candidate.type == SocketEventType.NAVIGATION_ACTION) actionEvent = candidate
                }
            }
            val action = codec.json.decodeFromJsonElement<NavigationActionPayload>(
                requireNotNull(actionEvent?.payload),
            )
            assertEquals("扬州东站", action.destination)
            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.NAVIGATION_EXECUTION_RESULT,
                "navigation-request",
                "navigation-conversation",
                codec.json.encodeToJsonElement(NavigationExecutionResultPayload(
                    action.actionId,
                    NavigationExecutionStatus.MAP_APP_LAUNCHED,
                )),
            ))))
            val events = mutableListOf<SocketEvent>()
            withTimeout(3_000) {
                while (events.none { it.type == SocketEventType.ASSISTANT_COMPLETED }) {
                    events += receiveEvent()
                }
            }
            val text = events.filter { it.type == SocketEventType.ASSISTANT_DELTA }.joinToString("") {
                codec.json.decodeFromJsonElement<com.carvoice.contracts.AssistantDeltaPayload>(
                    requireNotNull(it.payload),
                ).text
            }
            assertEquals("已为你打开地图，请在地图中确认目的地。", text)
        }
    }

    @Test
    fun `navigation reports unavailable map app from client result`() = testApplication {
        application { module(config, agent, tavilyConfig) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/chat") {
            receiveEvent()
            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.CHAT_REQUEST, "no-map-request", "no-map-conversation",
                codec.json.encodeToJsonElement(ChatRequestPayload("导航到扬州东站")),
            ))))
            var action: NavigationActionPayload? = null
            withTimeout(3_000) {
                while (action == null) {
                    val event = receiveEvent()
                    if (event.type == SocketEventType.NAVIGATION_ACTION) {
                        action = codec.json.decodeFromJsonElement(requireNotNull(event.payload))
                    }
                }
            }
            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.NAVIGATION_EXECUTION_RESULT, "no-map-request", "no-map-conversation",
                codec.json.encodeToJsonElement(NavigationExecutionResultPayload(
                    requireNotNull(action).actionId, NavigationExecutionStatus.NO_MAP_APP,
                )),
            ))))
            var response = ""
            withTimeout(3_000) {
                while (true) {
                    val event = receiveEvent()
                    if (event.type == SocketEventType.ASSISTANT_DELTA) {
                        response += codec.json.decodeFromJsonElement<com.carvoice.contracts.AssistantDeltaPayload>(
                            requireNotNull(event.payload),
                        ).text
                    }
                    if (event.type == SocketEventType.ASSISTANT_COMPLETED) break
                }
            }
            assertEquals("未找到可处理地点搜索的地图应用。", response)
        }
    }

    @Test
    fun `conversation sync is idempotent isolated and validates limits`() = testApplication {
        val historyAgent = FakeCarVoiceAgent(listOf("ok"))
        application { module(config, historyAgent, tavilyConfig) }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws/chat") {
            receiveEvent()
            val snapshot = ConversationSyncPayload(
                listOf(
                    ConversationSyncMessage(ConversationSyncRole.USER, "old user"),
                    ConversationSyncMessage(ConversationSyncRole.ASSISTANT, "old assistant"),
                ),
            )
            repeat(2) {
                send(Frame.Text(codec.encode(SocketEvent(
                    SocketEventType.CONVERSATION_SYNC,
                    conversationId = "one",
                    payload = codec.json.encodeToJsonElement(snapshot),
                ))))
            }
            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.CHAT_REQUEST, "r1", "one",
                codec.json.encodeToJsonElement(ChatRequestPayload("new")),
            ))))
            while (receiveEvent().type != SocketEventType.ASSISTANT_COMPLETED) Unit
            assertEquals(listOf("old user", "old assistant"), historyAgent.receivedHistories.single().map { it.content })

            send(Frame.Text(codec.encode(SocketEvent(
                SocketEventType.CONVERSATION_SYNC,
                conversationId = "two",
                payload = codec.json.encodeToJsonElement(ConversationSyncPayload(List(21) {
                    ConversationSyncMessage(ConversationSyncRole.USER, "x")
                })),
            ))))
            assertErrorCode("INVALID_SYNC")
        }
    }

    @Test
    fun `invalid unknown and empty events return safe errors`() = testApplication {
        application {
            module(config, agent, tavilyConfig)
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

    @Test
    fun `websocket confirms simulated control before updating state`() = testApplication {
        application {
            module(config, agent, tavilyConfig)
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

    @Test
    fun `websocket streams search progress sources and grounded answer`() = testApplication {
        var receivedQuery: String? = null
        val searchPort = object : WebSearchPort {
            override suspend fun search(query: String): WebSearchOutcome {
                receivedQuery = query
                return WebSearchOutcome.Success(
                    WebSearchResult(
                        query,
                        listOf(
                            WebSearchSource(
                                "南京新闻来源",
                                "https://example.com/nanjing-news",
                                "新闻摘要",
                                "example.com",
                            ),
                        ),
                    ),
                )
            }
        }
        application {
            module(config, agent, tavilyConfig, searchPort)
        }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/chat") {
            receiveEvent()
            send(
                Frame.Text(
                    codec.encode(
                        SocketEvent(
                            type = SocketEventType.CHAT_REQUEST,
                            requestId = "search-request",
                            conversationId = "search-conversation",
                            payload = codec.json.encodeToJsonElement(
                                ChatRequestPayload("今天南京有哪些新闻"),
                            ),
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

            val progress = events.filter { it.type == SocketEventType.TOOL_PROGRESS }.map {
                codec.json.decodeFromJsonElement<ToolProgressPayload>(requireNotNull(it.payload))
            }
            assertEquals(
                listOf(ToolCallStatus.RUNNING, ToolCallStatus.COMPLETED),
                progress.map { it.status },
            )
            val sourceIndex = events.indexOfFirst {
                it.type == SocketEventType.WEB_SEARCH_SOURCES
            }
            val deltaIndex = events.indexOfFirst { it.type == SocketEventType.ASSISTANT_DELTA }
            assertTrue(sourceIndex in 0..<deltaIndex)
            val sources = codec.json.decodeFromJsonElement<WebSearchSourcesPayload>(
                requireNotNull(events[sourceIndex].payload),
            )
            assertEquals("search-request", sources.messageId)
            assertEquals(1, sources.sources.size)
            assertTrue(receivedQuery.orEmpty().contains("南京"))
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
