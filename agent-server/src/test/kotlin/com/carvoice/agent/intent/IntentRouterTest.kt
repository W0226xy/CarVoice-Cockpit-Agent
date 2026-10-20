package com.carvoice.agent.intent

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.contracts.IntentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class IntentRouterTest {
    @Test
    fun `strict parser accepts valid result and rejects invalid fields`() {
        val router = IntentRouter(FakeAgent())

        assertEquals(
            IntentType.NAVIGATION,
            router.parse(
                """{"intent":"NAVIGATION","confidence":0.9,"slots":{"destination":"扬州大学"}}""",
            ).intent,
        )
        assertFails {
            router.parse("""{"intent":"INVALID","confidence":0.9,"slots":{}}""")
        }
        assertFails {
            router.parse("""{"intent":"MEDIA","confidence":1.2,"slots":{}}""")
        }
        assertFails {
            router.parse("""{"intent":"MEDIA","confidence":0.9,"slots":{"destination":"x"}}""")
        }
    }

    @Test
    fun `model result classifies and explicit slots are enriched`() = runBlocking {
        val router = IntentRouter(
            FakeAgent("""{"intent":"VEHICLE_CONTROL","confidence":0.9,"slots":{}}"""),
        )

        val result = router.classify("打开主驾驶车窗到一半")

        assertEquals(IntentType.VEHICLE_CONTROL, result.intent)
        assertEquals("driver", result.slots["windowPosition"])
        assertEquals("50", result.slots["windowPercent"])
    }

    @Test
    fun `model failure and low confidence use safe keyword fallback`() = runBlocking {
        val failed = IntentRouter(FakeAgent(failure = true)).classify("导航去扬州大学")
        val low = IntentRouter(
            FakeAgent("""{"intent":"UNKNOWN","confidence":0.2,"slots":{}}"""),
        ).classify("播放周杰伦的晴天")
        val unknown = IntentRouter(FakeAgent(failure = true)).classify("帮我安排一下")

        assertEquals(IntentType.NAVIGATION, failed.intent)
        assertEquals("扬州大学", failed.slots["destination"])
        assertEquals(IntentType.MEDIA, low.intent)
        assertEquals("晴天", low.slots["song"])
        assertEquals("周杰伦", low.slots["artist"])
        assertEquals(IntentType.UNKNOWN, unknown.intent)
        assertEquals(0.0, unknown.confidence)
    }

    @Test
    fun `current information deterministically stays general chat even when model says knowledge`() = runBlocking {
        val router = IntentRouter(
            FakeAgent("""{"intent":"KNOWLEDGE_QUERY","confidence":0.99,"slots":{"topic":"南京新闻"}}"""),
        )

        assertEquals(IntentType.GENERAL_CHAT, router.classify("今天南京有哪些新闻").intent)
        assertEquals(IntentType.GENERAL_CHAT, router.classify("南京今天的天气怎么样").intent)
        assertEquals(IntentType.GENERAL_CHAT, router.classify("查一下今天油价").intent)
        assertEquals(IntentType.GENERAL_CHAT, router.classify("最近有什么 AI 新闻").intent)
        assertEquals(IntentType.GENERAL_CHAT, router.classify("当前美元汇率").intent)
        assertEquals(IntentType.GENERAL_CHAT, router.classify("给我搜索一个主题并附来源").intent)
        assertEquals(
            IntentType.GENERAL_CHAT,
            IntentRouter(FakeAgent(failure = true)).classify("今天南京有哪些新闻").intent,
        )
    }

    @Test
    fun `only explicit local materials use knowledge query fallback`() = runBlocking {
        val router = IntentRouter(FakeAgent(failure = true))

        assertEquals(IntentType.KNOWLEDGE_QUERY, router.classify("这个故障灯是什么意思").intent)
        assertEquals(IntentType.KNOWLEDGE_QUERY, router.classify("查知识库中的车辆用户手册").intent)
    }
}

private class FakeAgent(
    private val classification: String? = null,
    private val failure: Boolean = false,
) : CarVoiceAgent {
    override fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String> = emptyFlow()

    override suspend fun classifyIntent(message: String): String? {
        if (failure) error("model unavailable")
        return classification
    }
}
