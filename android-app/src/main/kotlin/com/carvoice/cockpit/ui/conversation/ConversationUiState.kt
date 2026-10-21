package com.carvoice.cockpit.ui.conversation

import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ChatHistoryItem
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.ConversationErrorUi
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.model.SimulatedCockpitStateUi
import com.carvoice.cockpit.model.WebSearchSourcesUi
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.toHistoryItem
import java.util.UUID

data class ConversationUiState(
    val sessions: List<ConversationSession> = listOf(createEmptyConversationSession()),
    val activeSessionId: String = sessions.first().id,//当前激活的会话ID
    val destination: WorkspaceDestination = WorkspaceDestination.CONVERSATION,//目标工作区
    val inputText: String = "",//输入框文本
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,//连接状态
    val toolExecution: ToolExecutionUiState? = null,//工具执行状态
    val pendingVehicleAction: VehicleActionUi? = null,
    val respondingSessionId: String? = null,//正在响应的会话ID
    val activeRequestId: String? = null,//活跃请求ID
    val activeAssistantMessageId: String? = null,//活跃助手消息ID
    val error: ConversationErrorUi? = null,//错误信息
    val retryMessage: String? = null,//重试消息
    val deleteTargetConversationId: String? = null,//待删除的会话ID
    val cockpitStates: Map<String, SimulatedCockpitStateUi> = emptyMap(),//模拟座舱状态
    val pendingSearchSources: Map<String, WebSearchSourcesUi> = emptyMap(),//待处理的搜索来源
    val pendingKnowledgeSources: Map<String, com.carvoice.cockpit.model.KnowledgeSourcesUi> = emptyMap(),
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
