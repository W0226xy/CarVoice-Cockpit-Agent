package com.carvoice.agent.tasks

import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.IntentType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import com.carvoice.agent.knowledge.KnowledgeAnswerService
import com.carvoice.agent.knowledge.KnowledgePassage
import com.carvoice.agent.knowledge.KnowledgeRetriever
import com.carvoice.agent.knowledge.KnowledgeSearchResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

class TaskModulesTest {
    @Test
    fun `navigation and media ask for missing parameters`() = runBlocking {
        val navigation = NavigationTask().execute(context(IntentType.NAVIGATION))
        val media = MediaTask().execute(context(IntentType.MEDIA))

        assertEquals("你想导航到哪里？", assertIs<TaskResult.Reply>(navigation).text)
        assertTrue(assertIs<TaskResult.Reply>(media).text.contains("你想播放"))
    }

    @Test
    fun `knowledge task honestly reports when retrieval has no evidence`() = runBlocking {
        val result = KnowledgeTask(
            object : KnowledgeRetriever { override suspend fun search(topic: String) = KnowledgeSearchResult(topic, emptyList()) },
            object : KnowledgeAnswerService { override fun answer(question: String, passages: List<KnowledgePassage>) = flowOf("unused") },
        ).execute(context(IntentType.KNOWLEDGE_QUERY))

        val events = assertIs<TaskResult.Streaming>(result).events.toList()
        assertTrue(events.filterIsInstance<TaskStreamEvent.Delta>().single().text.contains("没有找到足够依据"))
    }

    @Test
    fun `control validates ranges and changes state only after confirmation`() = runBlocking {
        val task = SimulatedCockpitTask(fixedClock())
        val invalid = task.execute(
            context(
                IntentType.VEHICLE_CONTROL,
                mapOf("targetTemperature" to "31"),
            ),
        )
        assertTrue(assertIs<TaskResult.Reply>(invalid).text.contains("16–30"))

        val requested = assertIs<TaskResult.Confirmation>(
            task.execute(
                context(
                    IntentType.VEHICLE_CONTROL,
                    mapOf("action" to "enableAc", "targetTemperature" to "24"),
                ),
            ),
        )
        assertFalse(task.state("conversation-1").acEnabled)

        val completed = assertIs<TaskResult.ControlCompleted>(
            task.confirm("conversation-1", requested.payload.confirmationId, true),
        )
        assertTrue(completed.state?.acEnabled == true)
        assertEquals(24f, completed.state?.targetTemperature)
        assertIs<TaskResult.Failure>(
            task.confirm("conversation-1", requested.payload.confirmationId, true),
        )
    }

    @Test
    fun `cancel preserves state and conversations are isolated`() = runBlocking {
        val task = SimulatedCockpitTask(fixedClock())
        val cancelledRequest = assertIs<TaskResult.Confirmation>(
            task.execute(
                context(IntentType.VEHICLE_CONTROL, mapOf("action" to "enableAc")),
            ),
        )
        val cancelled = assertIs<TaskResult.ControlCompleted>(
            task.confirm("conversation-1", cancelledRequest.payload.confirmationId, false),
        )
        assertTrue(cancelled.cancelled)
        assertNull(cancelled.state)
        assertFalse(task.state("conversation-1").acEnabled)

        val confirmedRequest = assertIs<TaskResult.Confirmation>(
            task.execute(
                context(
                    IntentType.VEHICLE_CONTROL,
                    mapOf(
                        "action" to "setWindow",
                        "windowPosition" to "driver",
                        "windowPercent" to "50",
                    ),
                ).copy(conversationId = "conversation-2"),
            ),
        )
        task.confirm("conversation-2", confirmedRequest.payload.confirmationId, true)

        assertEquals(0, task.state("conversation-1").driverWindowPercent)
        assertEquals(50, task.state("conversation-2").driverWindowPercent)
    }

    @Test
    fun `wrong conversation does not consume confirmation and expired confirmation fails`() =
        runBlocking {
            val task = SimulatedCockpitTask(fixedClock())
            val request = assertIs<TaskResult.Confirmation>(
                task.execute(
                    context(IntentType.VEHICLE_CONTROL, mapOf("action" to "enableAc")),
                ),
            )

            assertIs<TaskResult.Failure>(
                task.confirm("conversation-other", request.payload.confirmationId, true),
            )
            assertIs<TaskResult.ControlCompleted>(
                task.confirm("conversation-1", request.payload.confirmationId, true),
            )

            val expiringTask = SimulatedCockpitTask(fixedClock(), confirmationTtlMillis = -1)
            val expired = assertIs<TaskResult.Confirmation>(
                expiringTask.execute(
                    context(IntentType.VEHICLE_CONTROL, mapOf("action" to "enableAc")),
                ),
            )
            val failure = assertIs<TaskResult.Failure>(
                expiringTask.confirm("conversation-1", expired.payload.confirmationId, true),
            )
            assertEquals("EXPIRED_CONFIRMATION", failure.code)
            assertFalse(expiringTask.state("conversation-1").acEnabled)
        }

    private fun context(
        intent: IntentType,
        slots: Map<String, String> = emptyMap(),
    ) = TaskContext(
        requestId = "request-1",
        conversationId = "conversation-1",
        message = "test",
        intent = IntentResult(intent, 0.9, slots),
        history = emptyList(),
    )

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
}
