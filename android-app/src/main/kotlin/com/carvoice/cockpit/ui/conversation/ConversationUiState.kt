package com.carvoice.cockpit.ui.conversation

import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ChatHistoryItem
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.KnowledgeBaseItem
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.mockConversationSessions
import com.carvoice.cockpit.model.mockKnowledgeBaseItems
import com.carvoice.cockpit.model.toHistoryItem

data class ConversationUiState(
    val sessions: List<ConversationSession> = mockConversationSessions,
    val activeSessionId: String = sessions.first().id,
    val destination: WorkspaceDestination = WorkspaceDestination.CONVERSATION,
    val knowledgeBases: List<KnowledgeBaseItem> = mockKnowledgeBaseItems,
    val inputText: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val toolExecution: ToolExecutionUiState? = null,
    val pendingVehicleAction: VehicleActionUi? = null,
    val respondingSessionId: String? = null,
) {
    val activeSession: ConversationSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val messages: List<ChatMessage>
        get() = activeSession?.messages.orEmpty()

    val historyItems: List<ChatHistoryItem>
        get() = sessions.map(ConversationSession::toHistoryItem)

    val isAssistantResponding: Boolean
        get() = respondingSessionId == activeSessionId
}
