package com.carvoice.agent.search

import com.carvoice.agent.agent.CarVoiceAgent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.carvoice.agent.agent.ConversationTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class SearchDecisionServiceTest {
    @Test
    fun `rules search only explicit and time sensitive requests`() = runBlocking {
        val service = SearchDecisionService(DecisionAgent(null))

        listOf(
            "扬州今天会下雨吗？",
            "帮我查一下今天南京的油价",
            "最近有什么 AI 新闻？给我来源。",
            "你好，帮我查一下今天的新闻",
        ).forEach {
            assertTrue(service.decide(it).shouldSearch, it)
        }
        listOf(
            "你好，你能做什么？",
            "解释一下 Kotlin 的 StateFlow",
            "总结我刚才说的内容",
        ).forEach {
            assertFalse(service.decide(it).shouldSearch, it)
        }
    }

    @Test
    fun `current information signals always search and today query uses Shanghai date`() = runBlocking {
        val service = SearchDecisionService(
            DecisionAgent(null),
            clock = Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneId.of("Asia/Shanghai")),
        )

        val news = service.decide("今天南京有哪些新闻")
        assertTrue(news.shouldSearch)
        assertEquals("2026-10-20 南京 新闻", news.query)
        listOf(
            "南京今天会下雨吗",
            "最近有什么 AI 新闻，给我来源",
            "查一下今天南京的油价",
            "现在美元兑人民币汇率是多少",
        ).forEach {
            assertTrue(service.decide(it).shouldSearch, it)
        }
    }

    @Test
    fun `strict LLM decision is accepted for ambiguous request`() = runBlocking {
        val service = SearchDecisionService(
            DecisionAgent(
                """{"shouldSearch":true,"reason":"需要当前资料","query":"Kotlin 当前版本","confidence":0.9}""",
            ),
        )

        val result = service.decide("Kotlin 现在发展得怎么样")

        assertTrue(result.shouldSearch)
        assertEquals("Kotlin 当前版本", result.query)
    }

    @Test
    fun `invalid low confidence and model failure default to no search`() = runBlocking {
        val invalid = SearchDecisionService(DecisionAgent("""{"unexpected":true}"""))
        val low = SearchDecisionService(
            DecisionAgent(
                """{"shouldSearch":true,"reason":"不确定","query":"test","confidence":0.2}""",
            ),
        )
        val failed = SearchDecisionService(DecisionAgent(failure = true))

        assertFalse(invalid.decide("一个模糊问题").shouldSearch)
        assertFalse(low.decide("另一个模糊问题").shouldSearch)
        assertFalse(failed.decide("还是一个模糊问题").shouldSearch)
    }

    @Test
    fun `query sanitizer removes credential shaped content and limits length`() {
        val service = SearchDecisionService(DecisionAgent(null))

        val query = service.sanitizeQuery(
            "帮我查一下 Kotlin 新闻 API_KEY=secret Authorization: Bearer token " +
                "x".repeat(300),
        )

        assertFalse(query.contains("secret"))
        assertFalse(query.contains("token"))
        assertTrue(query.length <= 200)
    }

    @Test
    fun `explicit search without a concrete query safely stays offline`() = runBlocking {
        val service = SearchDecisionService(DecisionAgent(null))

        assertFalse(service.decide("帮我查一下").shouldSearch)
    }
}

private class DecisionAgent(
    private val decision: String? = null,
    private val failure: Boolean = false,
) : CarVoiceAgent {
    override fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String> = emptyFlow()

    override suspend fun decideWebSearch(message: String): String? {
        if (failure) error("decision model unavailable")
        return decision
    }
}
