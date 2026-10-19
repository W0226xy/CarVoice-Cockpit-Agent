package com.carvoice.cockpit.model

enum class ConnectionState(val label: String) {
    CONNECTED("服务已连接"),
    CONNECTING("正在连接"),
    DISCONNECTED("服务未连接"),
    FAILED("连接失败"),
}

enum class MessageAuthor {
    USER,
    AGENT,
}

data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val content: String,
)

enum class ToolExecutionState {
    RUNNING,
    COMPLETED,
}

data class ToolExecutionUiState(
    val title: String,
    val detail: String,
    val state: ToolExecutionState,
)

data class VehicleActionUi(
    val title: String,
    val description: String,
    val riskMessage: String,
)

data class ConversationErrorUi(
    val message: String,
    val retryable: Boolean,
)

enum class WorkspaceDestination {
    CONVERSATION,
    KNOWLEDGE_BASE,
}

data class ConversationSession(
    val id: String,
    val title: String,
    val updatedAtLabel: String,
    val messages: List<ChatMessage>,
){
    val hasUserMessage: Boolean
        get() = messages.any { it.author == MessageAuthor.USER }
}

data class ChatHistoryItem(
    val sessionId: String,
    val title: String,
    val summary: String,
    val updatedAtLabel: String,
)

fun ConversationSession.toHistoryItem() = ChatHistoryItem(
    sessionId = id,
    title = title,
    summary = messages.lastOrNull()?.content.orEmpty(),
    updatedAtLabel = updatedAtLabel,
)

data class KnowledgeBaseItem(
    val id: String,
    val title: String,
    val description: String,
    val documentCount: Int,
    val updatedAtLabel: String,
)

enum class QuickActionType {
    VEHICLE_STATUS,
    AIR_CONDITIONER,
    NAVIGATION,
    MEDIA,
}

data class QuickAction(
    val type: QuickActionType,
    val title: String,
    val description: String,
    val marker: String,
    val demoPrompt: String,
)

val dashboardQuickActions = listOf(
    QuickAction(
        type = QuickActionType.VEHICLE_STATUS,
        title = "查询车辆状态",
        description = "续航、温度与胎压",
        marker = "车况",
        demoPrompt = "当前车辆状态怎么样？",
    ),
    QuickAction(
        type = QuickActionType.AIR_CONDITIONER,
        title = "打开空调",
        description = "设置舒适座舱温度",
        marker = "温控",
        demoPrompt = "请将空调设置为 24 ℃",
    ),
    QuickAction(
        type = QuickActionType.NAVIGATION,
        title = "导航回家",
        description = "准备常用回家路线",
        marker = "导航",
        demoPrompt = "导航回家",
    ),
    QuickAction(
        type = QuickActionType.MEDIA,
        title = "播放音乐",
        description = "继续播放车载音乐",
        marker = "媒体",
        demoPrompt = "播放音乐",
    ),
)

val mockKnowledgeBaseItems = listOf(
    KnowledgeBaseItem(
        id = "vehicle-manual",
        title = "车机使用手册",
        description = "车机系统、账号、显示与常用功能说明",
        documentCount = 12,
        updatedAtLabel = "今天",
    ),
    KnowledgeBaseItem(
        id = "vehicle-control",
        title = "车辆控制说明",
        description = "空调、车窗及车辆状态字段的安全操作约定",
        documentCount = 8,
        updatedAtLabel = "昨天",
    ),
    KnowledgeBaseItem(
        id = "navigation-travel",
        title = "导航与出行",
        description = "目的地、路线偏好和出行服务使用指南",
        documentCount = 6,
        updatedAtLabel = "10 月 16 日",
    ),
)
