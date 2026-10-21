package com.carvoice.agent.tasks

import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.SimulatedCockpitStatePayload
import kotlinx.coroutines.flow.Flow
import com.carvoice.contracts.ToolCallStatus
import com.carvoice.contracts.WebSearchSourcesPayload
import com.carvoice.contracts.KnowledgeSourcesPayload

data class TaskContext(
    val requestId: String,
    val conversationId: String,
    val message: String,
    val intent: IntentResult,
    val history: List<ConversationTurn>,
    val knowledgeBaseId: String? = null,
)

sealed interface TaskResult {
    data class Streaming(val events: Flow<TaskStreamEvent>) : TaskResult
    data class Reply(val text: String) : TaskResult
    data class Confirmation(
        val text: String,
        val payload: ControlConfirmationRequiredPayload,
    ) : TaskResult
    data class ControlCompleted(
        val text: String,
        val confirmationId: String,
        val state: SimulatedCockpitStatePayload?,
        val cancelled: Boolean = false,
    ) : TaskResult
    data class Failure(val code: String, val message: String) : TaskResult
}

sealed interface TaskStreamEvent {
    data class Delta(val text: String) : TaskStreamEvent
    data class ToolProgress(
        val callId: String,
        val toolName: String,
        val status: ToolCallStatus,
        val message: String,
    ) : TaskStreamEvent
    data class SearchSources(val payload: WebSearchSourcesPayload) : TaskStreamEvent
    data class KnowledgeSources(val payload: KnowledgeSourcesPayload) : TaskStreamEvent
}

interface TaskModule {
    suspend fun execute(context: TaskContext): TaskResult
}
