package com.carvoice.cockpit.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carvoice.cockpit.data.ConversationRepository
import com.carvoice.cockpit.data.ConversationStreamEvent
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.ConversationErrorUi
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.QuickAction
import com.carvoice.cockpit.model.QuickActionType
import com.carvoice.cockpit.model.SimulatedCockpitStateUi
import com.carvoice.cockpit.model.ToolExecutionState
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.WebSearchSourceUi
import com.carvoice.cockpit.model.WebSearchSourcesUi
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.carvoice.contracts.ToolCallStatus

class ConversationViewModel(
    private val repository: ConversationRepository,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(ConversationUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<ConversationUiState> =
        _uiState.asStateFlow()

    private var nextMessageId = 100L

    init {
        repository.connect()
        viewModelScope.launch(workerDispatcher) {
            repository.connectionState.collect { connectionState ->
                _uiState.update {
                    it.copy(
                        connectionState = connectionState,
                        error = if (connectionState == ConnectionState.FAILED) {
                            ConversationErrorUi("无法连接 Agent 服务，请检查服务是否启动。", true)
                        } else {
                            it.error
                        },
                        pendingVehicleAction = if (connectionState == ConnectionState.CONNECTED) {
                            it.pendingVehicleAction
                        } else {
                            null
                        },
                        respondingSessionId = if (connectionState == ConnectionState.CONNECTED) {
                            it.respondingSessionId
                        } else {
                            null
                        },
                        activeRequestId = if (connectionState == ConnectionState.CONNECTED) {
                            it.activeRequestId
                        } else {
                            null
                        },
                        activeAssistantMessageId = if (connectionState == ConnectionState.CONNECTED) {
                            it.activeAssistantMessageId
                        } else {
                            null
                        },
                        toolExecution = if (connectionState == ConnectionState.CONNECTED) {
                            it.toolExecution
                        } else {
                            null
                        },
                        pendingSearchSources = if (connectionState == ConnectionState.CONNECTED) {
                            it.pendingSearchSources
                        } else {
                            emptyMap()
                        },
                    )
                }
            }
        }
        viewModelScope.launch(workerDispatcher) {
            repository.events.collect(::handleStreamEvent)
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun prepareQuickAction(action: QuickAction) {
        _uiState.update {
            it.copy(
                destination = WorkspaceDestination.CONVERSATION,
                inputText = action.demoPrompt,
                pendingVehicleAction = null,
            )
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val message = state.inputText.trim()
        if (message.isEmpty() || state.isAssistantResponding) return
        if (state.connectionState != ConnectionState.CONNECTED) {
            _uiState.update {
                it.copy(
                    error = ConversationErrorUi("服务尚未连接，请连接后重试。", true),
                    retryMessage = message,
                )
            }
            repository.connect()
            return
        }

        val sessionId = state.activeSessionId
        val requestId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(nextMessageId++, MessageAuthor.USER, message)
        _uiState.update {
            it.copy(
                sessions = it.sessions.updateSession(sessionId) { session ->
                    session.copy(
                        title = if (!session.hasUserMessage && session.title == "新对话") {
                            message.toConversationTitle()
                        } else {
                            session.title
                        },
                        updatedAtLabel = "刚刚",
                        messages = session.messages + userMessage,
                    )
                },
                inputText = "",
                pendingVehicleAction = null,
                toolExecution = null,
                pendingSearchSources = emptyMap(),
                respondingSessionId = sessionId,
                activeRequestId = requestId,
                activeAssistantMessageId = null,
                error = null,
                retryMessage = message,
            )
        }

        viewModelScope.launch(workerDispatcher) {
            repository.sendMessage(sessionId, requestId, message)
                .onFailure {
                    showError("消息发送失败，请检查连接后重试。", retryable = true)
                }
        }
    }

    fun retry() {
        val state = _uiState.value
        val message = state.retryMessage
        _uiState.update { it.copy(error = null) }
        if (state.connectionState != ConnectionState.CONNECTED) {
            repository.connect()
            if (message != null) _uiState.update { it.copy(inputText = message) }
            return
        }
        if (message != null) {
            _uiState.update { it.copy(inputText = message) }
            sendMessage()
        }
    }

    fun selectConversation(sessionId: String) {
        if (_uiState.value.sessions.none { it.id == sessionId }) return
        _uiState.update {
            it.copy(
                sessions = it.sessions.moveSessionToFront(sessionId),
                activeSessionId = sessionId,
                destination = WorkspaceDestination.CONVERSATION,
                inputText = "",
                toolExecution = null,
                pendingVehicleAction = null,
                respondingSessionId = null,
                activeRequestId = null,
                activeAssistantMessageId = null,
                error = null,
                retryMessage = null,
                pendingSearchSources = emptyMap(),
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
        _uiState.update { it.copy(destination = WorkspaceDestination.CONVERSATION) }
    }

    fun cancelVehicleAction() {
        respondToVehicleAction(confirmed = false)
    }

    fun confirmVehicleAction() {
        respondToVehicleAction(confirmed = true)
    }

    fun startNewConversation() {
        val newSession = createEmptyConversationSession()
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions.filter(ConversationSession::hasUserMessage),
                activeSessionId = newSession.id,
                destination = WorkspaceDestination.CONVERSATION,
                inputText = "",
                toolExecution = null,
                pendingVehicleAction = null,
                respondingSessionId = null,
                activeRequestId = null,
                activeAssistantMessageId = null,
                error = null,
                retryMessage = null,
                pendingSearchSources = emptyMap(),
            )
        }
    }

    fun requestDeleteConversation(sessionId: String) {
        if (_uiState.value.historyItems.none { it.sessionId == sessionId }) return
        _uiState.update { it.copy(deleteTargetConversationId = sessionId) }
    }

    fun cancelDeleteConversation() {
        _uiState.update { it.copy(deleteTargetConversationId = null) }
    }

    fun confirmDeleteConversation() {
        val state = _uiState.value
        val targetId = state.deleteTargetConversationId ?: return
        if (state.sessions.none { it.id == targetId }) {
            cancelDeleteConversation()
            return
        }

        val remainingSessions = state.sessions.filterNot { it.id == targetId }
        val nextSession = if (targetId == state.activeSessionId) {
            remainingSessions.firstOrNull(ConversationSession::hasUserMessage)
                ?: createEmptyConversationSession()
        } else {
            state.activeSession ?: createEmptyConversationSession()
        }
        val nextSessions = if (remainingSessions.any { it.id == nextSession.id }) {
            remainingSessions
        } else {
            listOf(nextSession) + remainingSessions.filter(ConversationSession::hasUserMessage)
        }

        _uiState.update {
            it.copy(
                sessions = nextSessions,
                activeSessionId = nextSession.id,
                destination = WorkspaceDestination.CONVERSATION,
                inputText = "",
                toolExecution = null,
                pendingVehicleAction = null,
                respondingSessionId = null,
                activeRequestId = null,
                activeAssistantMessageId = null,
                error = null,
                retryMessage = null,
                deleteTargetConversationId = null,
                cockpitStates = it.cockpitStates - targetId,
                pendingSearchSources = emptyMap(),
            )
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private fun handleStreamEvent(event: ConversationStreamEvent) {
        when (event) {
            is ConversationStreamEvent.AssistantDelta -> appendDelta(event)
            is ConversationStreamEvent.AssistantCompleted -> completeResponse(event)
            is ConversationStreamEvent.Error -> {
                if (event.requestId == null ||
                    event.requestId == _uiState.value.activeRequestId ||
                    event.code in setOf("INVALID_CONFIRMATION", "EXPIRED_CONFIRMATION")
                ) {
                    showError(event.message, event.retryable)
                }
            }
            is ConversationStreamEvent.ControlConfirmationRequired -> {
                if (event.conversationId == _uiState.value.activeSessionId) {
                    _uiState.update {
                        it.copy(
                            pendingVehicleAction = VehicleActionUi(
                                title = "模拟座舱确认",
                                description = event.description,
                                riskMessage = "确认后只会修改服务端内存中的模拟座舱状态。",
                                confirmationId = event.confirmationId,
                                conversationId = event.conversationId,
                            ),
                        )
                    }
                }
            }
            is ConversationStreamEvent.ToolProgress -> {
                if (event.conversationId == _uiState.value.activeSessionId) {
                    val state = when (event.status) {
                        ToolCallStatus.COMPLETED, ToolCallStatus.CANCELLED ->
                            ToolExecutionState.COMPLETED
                        ToolCallStatus.FAILED -> ToolExecutionState.FAILED
                        else -> ToolExecutionState.RUNNING
                    }
                    _uiState.update {
                        it.copy(
                            toolExecution = ToolExecutionUiState(
                                title = if (event.toolName == "tavily_web_search") {
                                    "联网搜索"
                                } else {
                                    "模拟座舱"
                                },
                                detail = event.message,
                                state = state,
                            ),
                        )
                    }
                }
            }
            is ConversationStreamEvent.SimulatedCockpitState -> {
                val cockpit = event.state
                _uiState.update {
                    it.copy(
                        cockpitStates = it.cockpitStates + (
                            event.conversationId to SimulatedCockpitStateUi(
                                cockpit.acEnabled,
                                cockpit.targetTemperature,
                                cockpit.fanLevel,
                                cockpit.frontDefrostEnabled,
                                cockpit.driverWindowPercent,
                                cockpit.passengerWindowPercent,
                            )
                        ),
                    )
                }
            }
            is ConversationStreamEvent.WebSearchSources -> attachSearchSources(event)
        }
    }

    private fun attachSearchSources(event: ConversationStreamEvent.WebSearchSources) {
        val sources = WebSearchSourcesUi(
            query = event.query,
            sources = event.sources.map { source ->
                WebSearchSourceUi(
                    source.index,
                    source.title,
                    source.url,
                    source.domain,
                    source.snippet,
                    source.publishedAt,
                )
            },
        )
        _uiState.update { state ->
            val session = state.sessions.firstOrNull { it.id == event.conversationId }
                ?: return@update state
            val hasAssistantMessage = session.messages.any {
                it.serverMessageId == event.messageId
            }
            val isExpectedPendingResponse =
                state.activeRequestId == event.messageId &&
                    state.respondingSessionId == event.conversationId
            if (!hasAssistantMessage && !isExpectedPendingResponse) {
                return@update state
            }
            state.copy(
                sessions = if (hasAssistantMessage) {
                    state.sessions.updateSession(event.conversationId) { session ->
                        session.copy(
                            messages = session.messages.map { message ->
                                if (message.serverMessageId == event.messageId) {
                                    message.copy(
                                        searchQuery = sources.query,
                                        sources = sources.sources,
                                    )
                                } else {
                                    message
                                }
                            },
                        )
                    }
                } else {
                    state.sessions
                },
                pendingSearchSources = if (hasAssistantMessage) {
                    state.pendingSearchSources - event.messageId
                } else {
                    state.pendingSearchSources + (event.messageId to sources)
                },
            )
        }
    }

    private fun respondToVehicleAction(confirmed: Boolean) {
        val action = _uiState.value.pendingVehicleAction ?: return
        val requestId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                pendingVehicleAction = null,
                respondingSessionId = action.conversationId,
                activeRequestId = requestId,
                activeAssistantMessageId = null,
                error = null,
            )
        }
        viewModelScope.launch(workerDispatcher) {
            repository.sendControlConfirmation(
                action.conversationId,
                requestId,
                action.confirmationId,
                confirmed,
            ).onFailure {
                showError("模拟座舱确认发送失败，请检查连接后重试。", retryable = true)
            }
        }
    }

    private fun appendDelta(event: ConversationStreamEvent.AssistantDelta) {
        val state = _uiState.value
        if (event.requestId != state.activeRequestId ||
            event.conversationId != state.respondingSessionId
        ) {
            return
        }

        val assistantId = state.activeAssistantMessageId ?: nextMessageId++
        val pendingSources = state.pendingSearchSources[event.requestId]
        _uiState.update {
            it.copy(
                sessions = it.sessions.updateSession(event.conversationId) { session ->
                    val existing = session.messages.indexOfFirst { message ->
                        message.id == assistantId
                    }
                    val messages = if (existing >= 0) {
                        session.messages.map { message ->
                            if (message.id == assistantId) {
                                message.copy(content = message.content + event.text)
                            } else {
                                message
                            }
                        }
                    } else {
                        session.messages + ChatMessage(
                            id = assistantId,
                            author = MessageAuthor.AGENT,
                            content = event.text,
                            serverMessageId = event.requestId,
                            searchQuery = pendingSources?.query,
                            sources = pendingSources?.sources.orEmpty(),
                        )
                    }
                    session.copy(messages = messages, updatedAtLabel = "刚刚")
                },
                activeAssistantMessageId = assistantId,
                pendingSearchSources = it.pendingSearchSources - event.requestId,
            )
        }
    }

    private fun completeResponse(event: ConversationStreamEvent.AssistantCompleted) {
        val state = _uiState.value
        if (event.requestId != state.activeRequestId ||
            event.conversationId != state.respondingSessionId
        ) {
            return
        }
        _uiState.update {
            it.copy(
                respondingSessionId = null,
                activeRequestId = null,
                activeAssistantMessageId = null,
                error = null,
                retryMessage = null,
                pendingSearchSources = it.pendingSearchSources - event.requestId,
            )
        }
    }

    private fun showError(message: String, retryable: Boolean) {
        _uiState.update {
            it.copy(
                respondingSessionId = null,
                activeRequestId = null,
                activeAssistantMessageId = null,
                pendingVehicleAction = null,
                pendingSearchSources = emptyMap(),
                error = ConversationErrorUi(message, retryable),
            )
        }
    }
}

private inline fun List<ConversationSession>.updateSession(
    sessionId: String,
    transform: (ConversationSession) -> ConversationSession,
): List<ConversationSession> {
    val session = firstOrNull { it.id == sessionId } ?: return this
    return listOf(transform(session)) + filterNot { it.id == sessionId }
}

private fun List<ConversationSession>.moveSessionToFront(sessionId: String): List<ConversationSession> {
    val session = firstOrNull { it.id == sessionId } ?: return this
    return listOf(session) + filterNot { it.id == sessionId }
}

private fun String.toConversationTitle(): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return if (normalized.length > 18) "${normalized.take(18)}…" else normalized
}
