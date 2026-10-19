package com.carvoice.cockpit.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.QuickAction
import com.carvoice.cockpit.model.QuickActionType
import com.carvoice.cockpit.model.ToolExecutionState
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.model.WorkspaceDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val replyDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var nextMessageId = 100L
    private var nextSessionId = 1L
    private var localReplyJob: Job? = null

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun prepareQuickAction(action: QuickAction) {
        _uiState.update {
            it.copy(
                destination = WorkspaceDestination.CONVERSATION,
                inputText = action.demoPrompt,
                pendingVehicleAction = if (action.type == QuickActionType.AIR_CONDITIONER) {
                    VehicleActionUi(
                        title = "确认执行车辆操作",
                        description = "将空调设置为 24 ℃",
                        riskMessage = "该操作会修改车辆状态，请确认后执行。",
                    )
                } else {
                    null
                },
            )
        }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        val message = currentState.inputText.trim()
        val sessionId = currentState.activeSessionId
        if (message.isEmpty() || currentState.isAssistantResponding) return

        val userMessage = ChatMessage(
            id = nextMessageId++,
            author = MessageAuthor.USER,
            content = message,
        )
        val toolExecution = if (message.contains("车辆状态")) {
            ToolExecutionUiState(
                title = "查询车辆状态",
                detail = "正在查询",
                state = ToolExecutionState.RUNNING,
            )
        } else {
            null
        }
        _uiState.update {
            it.copy(
                sessions = it.sessions.updateSession(sessionId) { session ->
                    session.copy(
                        title = if (session.messages.isEmpty() && session.title == "新会话") {
                            message.take(14)
                        } else {
                            session.title
                        },
                        updatedAtLabel = "刚刚",
                        messages = session.messages + userMessage,
                    )
                },
                inputText = "",
                toolExecution = toolExecution,
                pendingVehicleAction = null,
                respondingSessionId = sessionId,
            )
        }

        localReplyJob = viewModelScope.launch(replyDispatcher) {
            delay(700)
            val demoReply = ChatMessage(
                id = nextMessageId++,
                author = MessageAuthor.AGENT,
                content = "当前为界面演示模式，Agent 服务将在下一阶段接入。",
            )
            _uiState.update {
                it.copy(
                    sessions = it.sessions.updateSession(sessionId) { session ->
                        session.copy(
                            updatedAtLabel = "刚刚",
                            messages = session.messages + demoReply,
                        )
                    },
                    toolExecution = if (it.activeSessionId == sessionId) {
                        it.toolExecution?.copy(
                            detail = "查询完成",
                            state = ToolExecutionState.COMPLETED,
                        )
                    } else {
                        it.toolExecution
                    },
                    respondingSessionId = if (it.respondingSessionId == sessionId) {
                        null
                    } else {
                        it.respondingSessionId
                    },
                )
            }
        }
    }

    fun selectConversation(sessionId: String) {
        if (_uiState.value.sessions.none { it.id == sessionId }) return
        localReplyJob?.cancel()
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                destination = WorkspaceDestination.CONVERSATION,
                inputText = "",
                toolExecution = null,
                pendingVehicleAction = null,
                respondingSessionId = null,
            )
        }
    }

    fun openKnowledgeBase() {
        _uiState.update {
            it.copy(
                destination = WorkspaceDestination.KNOWLEDGE_BASE,
                inputText = "",
                pendingVehicleAction = null,
            )
        }
    }

    fun returnToConversation() {
        _uiState.update {
            it.copy(destination = WorkspaceDestination.CONVERSATION)
        }
    }

    fun cancelVehicleAction() {
        _uiState.update { it.copy(pendingVehicleAction = null) }
    }

    fun confirmVehicleAction() {
        val state = _uiState.value
        val action = state.pendingVehicleAction ?: return
        val sessionId = state.activeSessionId
        val localNotice = ChatMessage(
            id = nextMessageId++,
            author = MessageAuthor.AGENT,
            content = "已确认：${action.description}。当前为界面演示模式，未执行车辆操作。",
        )
        _uiState.update {
            it.copy(
                sessions = it.sessions.updateSession(sessionId) { session ->
                    session.copy(
                        updatedAtLabel = "刚刚",
                        messages = session.messages + localNotice,
                    )
                },
                inputText = "",
                pendingVehicleAction = null,
            )
        }
    }

    fun startNewConversation() {
        localReplyJob?.cancel()
        val newSession = ConversationSession(
            id = "local-${nextSessionId++}",
            title = "新会话",
            updatedAtLabel = "刚刚",
            messages = emptyList(),
        )
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions,
                activeSessionId = newSession.id,
                destination = WorkspaceDestination.CONVERSATION,
                inputText = "",
                toolExecution = null,
                pendingVehicleAction = null,
                respondingSessionId = null,
            )
        }
    }
}

private inline fun List<ConversationSession>.updateSession(
    sessionId: String,
    transform: (ConversationSession) -> ConversationSession,
): List<ConversationSession> = map { session ->
    if (session.id == sessionId) transform(session) else session
}
