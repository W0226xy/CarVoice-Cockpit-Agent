package com.carvoice.agent.routing

import com.carvoice.agent.agent.MockStreamingAgent
import com.carvoice.agent.tasks.ChatTask
import com.carvoice.agent.tasks.ClarificationTask
import com.carvoice.agent.tasks.KnowledgeTask
import com.carvoice.agent.tasks.MediaTask
import com.carvoice.agent.tasks.NavigationTask
import com.carvoice.agent.tasks.SimulatedCockpitTask
import com.carvoice.contracts.IntentType
import kotlin.test.Test
import kotlin.test.assertIs

class TaskRouterTest {
    private val router = TaskRouter(ChatTask(MockStreamingAgent(0)))

    @Test
    fun `all six intents route deterministically`() {
        assertIs<ChatTask>(router.route(IntentType.GENERAL_CHAT))
        assertIs<NavigationTask>(router.route(IntentType.NAVIGATION))
        assertIs<MediaTask>(router.route(IntentType.MEDIA))
        assertIs<KnowledgeTask>(router.route(IntentType.KNOWLEDGE_QUERY))
        assertIs<SimulatedCockpitTask>(router.route(IntentType.VEHICLE_CONTROL))
        assertIs<ClarificationTask>(router.route(IntentType.UNKNOWN))
    }
}
