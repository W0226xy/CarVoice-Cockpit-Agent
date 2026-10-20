package com.carvoice.cockpit.ui.conversation

import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ChatHistoryItem
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.ConversationErrorUi
import com.carvoice.cockpit.model.KnowledgeBaseItem
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.model.SimulatedCockpitStateUi
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.mockKnowledgeBaseItems
import com.carvoice.cockpit.model.toHistoryItem
import java.util.UUID

data class ConversationUiState(
    val sessions: List<ConversationSession> = listOf(createEmptyConversationSession()),
    val activeSessionId: String = sessions.first().id,
    val destination: WorkspaceDestination = WorkspaceDestination.CONVERSATION,
    val knowledgeBases: List<KnowledgeBaseItem> = mockKnowledgeBaseItems,
    val inputText: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val toolExecution: ToolExecutionUiState? = null,
    val pendingVehicleAction: VehicleActionUi? = null,
    val respondingSessionId: String? = null,
    val activeRequestId: String? = null,
    val activeAssistantMessageId: Long? = null,
    val error: ConversationErrorUi? = null,
    val retryMessage: String? = null,
    val deleteTargetConversationId: String? = null,
    val cockpitStates: Map<String, SimulatedCockpitStateUi> = emptyMap(),
) {
    val activeSession: ConversationSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val messages: List<ChatMessage>
        get() = activeSession?.messages.orEmpty()

    val historyItems: List<ChatHistoryItem>
        get() = sessions
            .filter(ConversationSession::hasUserMessage)
            .map(ConversationSession::toHistoryItem)

    val isAssistantResponding: Boolean
        get() = respondingSessionId == activeSessionId

    val simulatedCockpitState: SimulatedCockpitStateUi?
        get() = cockpitStates[activeSessionId]
}

fun createEmptyConversationSession() = ConversationSession(
    id = UUID.randomUUID().toString(),
    title = "新对话",
    updatedAtLabel = "刚刚",
    messages = emptyList(),
)
