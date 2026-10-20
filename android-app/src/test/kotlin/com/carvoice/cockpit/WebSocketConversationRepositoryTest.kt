package com.carvoice.cockpit

import com.carvoice.cockpit.data.AgentConnection
import com.carvoice.cockpit.data.AgentConnectionConfig
import com.carvoice.cockpit.data.ConversationStreamEvent
import com.carvoice.cockpit.data.WebSocketConversationRepository
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import com.carvoice.contracts.WebSearchSourcePayload
import com.carvoice.contracts.WebSearchSourcesPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketConversationRepositoryTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `unknown events and malformed source events are safely ignored`() {
        withRepository { repository ->
            assertNull(repository.decodeEvent(SocketEvent(type = "future_event")))
            assertNull(
                repository.decodeEvent(
                    sourceEvent(
                        listOf(
                            WebSearchSourcePayload(
                                1,
                                "unsafe",
                                "javascript:alert(1)",
                                "attacker.example",
                                "bad",
                            ),
                        ),
                    ),
                ),
            )
            assertNull(
                repository.decodeEvent(
                    sourceEvent(
                        listOf(
                            WebSearchSourcePayload(
                                1,
                                "source",
                                "https://example.com",
                                "example.com",
                                "snippet",
                            ),
                        ),
                    ).copy(requestId = "different-request"),
                ),
            )
        }
    }

    @Test
    fun `source event derives domain from safe URL and bounds text`() {
        withRepository { repository ->
            val decoded = repository.decodeEvent(
                sourceEvent(
                    listOf(
                        WebSearchSourcePayload(
                            1,
                            "t".repeat(400),
                            "https://www.example.com/news",
                            "forged.example",
                            "s".repeat(2_000),
                        ),
                    ),
                ),
            )

            assertTrue(decoded is ConversationStreamEvent.WebSearchSources)
            val source = (decoded as ConversationStreamEvent.WebSearchSources).sources.single()
            assertEquals("example.com", source.domain)
            assertEquals(300, source.title.length)
            assertEquals(1_000, source.snippet.length)
        }
    }

    private fun sourceEvent(sources: List<WebSearchSourcePayload>) = SocketEvent(
        type = SocketEventType.WEB_SEARCH_SOURCES,
        requestId = "request-1",
        conversationId = "conversation-1",
        payload = json.encodeToJsonElement(
            WebSearchSourcesPayload(
                "conversation-1",
                "request-1",
                "query",
                sources,
            ),
        ),
    )

    private fun withRepository(block: (WebSocketConversationRepository) -> Unit) {
        val connection = AgentConnection(AgentConnectionConfig("ws://127.0.0.1:1/ws/chat"))
        try {
            block(WebSocketConversationRepository(connection, json))
        } finally {
            connection.close()
        }
    }
}
