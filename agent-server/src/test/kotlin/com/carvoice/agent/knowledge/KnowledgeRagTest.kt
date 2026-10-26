package com.carvoice.agent.knowledge

import com.carvoice.agent.intent.IntentRouter
import com.carvoice.agent.routing.TaskRouter
import com.carvoice.agent.tasks.ChatTask
import com.carvoice.agent.tasks.KnowledgeTask
import com.carvoice.agent.tasks.TaskContext
import com.carvoice.agent.tasks.TaskResult
import com.carvoice.agent.tasks.TaskStreamEvent
import com.carvoice.agent.testing.FakeCarVoiceAgent
import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.IntentType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KnowledgeRagTest {
    private val embeddings = CharacterNgramEmbeddingService()

    @Test
    fun `vehicle manual question routes to knowledge task and returns grounded answer`() = runBlocking {
        val retriever = DefaultKnowledgeRetriever(
            InMemoryKnowledgeRepository(phaseOneVehicleKnowledge(), embeddings), embeddings,
        )
        var answerPassages: List<KnowledgePassage> = emptyList()
        val task = KnowledgeTask(retriever, object : KnowledgeAnswerService {
            override fun answer(question: String, passages: List<KnowledgePassage>) = flowOf(
                "发动机故障灯常亮时应避免激烈驾驶。[engine-malfunction-light]",
            ).also { answerPassages = passages }
        })
        val router = TaskRouter(ChatTask(FakeCarVoiceAgent()), task)
        val intent = IntentRouter(FakeCarVoiceAgent()).classify("发动机故障灯亮了怎么办")

        assertEquals(IntentType.KNOWLEDGE_QUERY, intent.intent)
        assertSame(task, router.route(intent.intent))
        val events = assertIs<TaskResult.Streaming>(task.execute(context(intent, "发动机故障灯亮了怎么办"))).events.toList()
        assertTrue(answerPassages.any { it.id == "engine-malfunction-light" })
        assertEquals(
            "发动机故障灯常亮时应避免激烈驾驶。[engine-malfunction-light]",
            assertIs<TaskStreamEvent.Delta>(events.filterIsInstance<TaskStreamEvent.Delta>().single()).text,
        )
    }

    @Test
    fun `missing knowledge is answered honestly and retrieval failures are safe`() = runBlocking {
        val emptyTask = KnowledgeTask(
            object : KnowledgeRetriever { override suspend fun search(topic: String) = KnowledgeSearchResult(topic, emptyList()) },
            unusedAnswerService(),
        )
        val emptyEvents = assertIs<TaskResult.Streaming>(emptyTask.execute(context())).events.toList()
        assertTrue((emptyEvents.filterIsInstance<TaskStreamEvent.Delta>().single()).text.contains("没有找到足够依据"))
        assertTrue(emptyEvents.none { it is TaskStreamEvent.KnowledgeSources })

        val failedTask = KnowledgeTask(
            object : KnowledgeRetriever { override suspend fun search(topic: String): KnowledgeSearchResult = error("offline") },
            unusedAnswerService(),
        )
        val failedEvents = assertIs<TaskResult.Streaming>(failedTask.execute(context())).events.toList()
        assertTrue(failedEvents.any { it is TaskStreamEvent.ToolProgress && it.status == com.carvoice.contracts.ToolCallStatus.FAILED })
    }

    @Test
    fun `only passages cited by final streamed answer are sent to client`() = runBlocking {
        val passages = (1..3).map { index -> KnowledgePassage("$index", "资料$index", "内容$index", 1.0) }
        val task = KnowledgeTask(
            object : KnowledgeRetriever { override suspend fun search(topic: String) = KnowledgeSearchResult(topic, passages) },
            object : KnowledgeAnswerService { override fun answer(question: String, passages: List<KnowledgePassage>) = flowOf("答案[1]", "[3][99]") },
        )
        val events = assertIs<TaskResult.Streaming>(task.execute(context())).events.toList()
        val sources = assertIs<TaskStreamEvent.KnowledgeSources>(events.filterIsInstance<TaskStreamEvent.KnowledgeSources>().single())
        assertEquals(listOf(1, 3), sources.payload.sources.map { it.index })
    }

    @Test
    fun `answer without citation sends no knowledge source event`() = runBlocking {
        val task = KnowledgeTask(
            object : KnowledgeRetriever { override suspend fun search(topic: String) = KnowledgeSearchResult(topic, listOf(KnowledgePassage("1", "资料", "内容", 1.0))) },
            object : KnowledgeAnswerService { override fun answer(question: String, passages: List<KnowledgePassage>) = flowOf("没有足够依据") },
        )
        val events = assertIs<TaskResult.Streaming>(task.execute(context())).events.toList()
        assertTrue(events.none { it is TaskStreamEvent.KnowledgeSources })
    }

    @Test
    fun `weather remains general chat and knowledge task has no Tavily dependency`() = runBlocking {
        val intent = IntentRouter(FakeCarVoiceAgent()).classify("南京今天的天气怎么样")
        assertEquals(IntentType.GENERAL_CHAT, intent.intent)
        // KnowledgeTask accepts only retrieval and answer ports; therefore it cannot call Tavily.
        val result = DefaultKnowledgeRetriever(
            InMemoryKnowledgeRepository(phaseOneVehicleKnowledge(), embeddings), embeddings,
        ).search("胎压报警灯亮了怎么办")
        assertTrue(result.passages.isNotEmpty())
    }

    private fun unusedAnswerService() = object : KnowledgeAnswerService {
        override fun answer(question: String, passages: List<KnowledgePassage>) = flowOf("unused")
    }

    private fun context(
        intent: IntentResult = IntentResult(IntentType.KNOWLEDGE_QUERY, 0.9, mapOf("topic" to "未知资料")),
        message: String = "未知资料",
    ) = TaskContext("request", "conversation", message, intent, emptyList())
}
