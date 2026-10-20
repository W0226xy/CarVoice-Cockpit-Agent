package com.carvoice.agent.tasks

import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.SimulatedCockpitStatePayload
import kotlinx.coroutines.flow.Flow

data class TaskContext(
    val requestId: String,
    val conversationId: String,
    val message: String,
    val intent: IntentResult,
    val history: List<ConversationTurn>,
)

sealed interface TaskResult {
    data class Streaming(val chunks: Flow<String>) : TaskResult
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

interface TaskModule {
    suspend fun execute(context: TaskContext): TaskResult
}
