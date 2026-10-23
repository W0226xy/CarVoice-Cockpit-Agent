package com.carvoice.agent.routing

import com.carvoice.agent.tasks.ChatTask
import com.carvoice.agent.tasks.ClarificationTask
import com.carvoice.agent.tasks.KnowledgeTask
import com.carvoice.agent.tasks.MediaTask
import com.carvoice.agent.tasks.NavigationTask
import com.carvoice.agent.tasks.SimulatedCockpitTask
import com.carvoice.agent.tasks.TaskModule
import com.carvoice.contracts.IntentType

class TaskRouter(
    val chatTask: ChatTask,
    val knowledgeTask: TaskModule,
    val navigationTask: NavigationTask = NavigationTask(),
    val mediaTask: MediaTask = MediaTask(),
    val simulatedCockpitTask: SimulatedCockpitTask = SimulatedCockpitTask(),
    val clarificationTask: ClarificationTask = ClarificationTask(),
) {
    fun route(intent: IntentType): TaskModule = when (intent) {
        IntentType.GENERAL_CHAT -> chatTask
        IntentType.NAVIGATION -> navigationTask
        IntentType.MEDIA -> mediaTask
        IntentType.KNOWLEDGE_QUERY -> knowledgeTask
        IntentType.VEHICLE_CONTROL -> simulatedCockpitTask
        IntentType.UNKNOWN -> clarificationTask
    }
}
