package com.carvoice.cockpit.model

enum class ConnectionState(val label: String) {
    CONNECTED("服务已连接"),
    CONNECTING("正在连接"),
    DISCONNECTED("服务未连接"),
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

enum class WorkspaceDestination {
    CONVERSATION,
    KNOWLEDGE_BASE,
}

data class ConversationSession(
    val id: String,
    val title: String,
    val updatedAtLabel: String,
    val messages: List<ChatMessage>,
)

data class ChatHistoryItem(
    val sessionId: String,
    val title: String,
    val summary: String,
    val updatedAtLabel: String,
)

fun ConversationSession.toHistoryItem() = ChatHistoryItem(
    sessionId = id,
    title = title,
    summary = messages.lastOrNull()?.content ?: "暂无消息",
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

val mockConversationSessions = listOf(
    ConversationSession(
        id = "session-today",
        title = "今日出行助手",
        updatedAtLabel = "14:18",
        messages = listOf(
            ChatMessage(
                id = 1,
                author = MessageAuthor.USER,
                content = "帮我看看今天出发前需要注意什么？",
            ),
            ChatMessage(
                id = 2,
                author = MessageAuthor.AGENT,
                content = "当前为本地演示：车辆续航充足，胎压正常。出发前可以确认空调和导航目的地。",
            ),
        ),
    ),
    ConversationSession(
        id = "session-climate",
        title = "座舱温度设置",
        updatedAtLabel = "昨天",
        messages = listOf(
            ChatMessage(
                id = 3,
                author = MessageAuthor.USER,
                content = "车内有点热，帮我设置到 24 ℃。",
            ),
            ChatMessage(
                id = 4,
                author = MessageAuthor.AGENT,
                content = "这是历史演示会话，没有执行真实车辆操作。",
            ),
        ),
    ),
    ConversationSession(
        id = "session-trip",
        title = "导航与音乐",
        updatedAtLabel = "周六",
        messages = listOf(
            ChatMessage(
                id = 5,
                author = MessageAuthor.USER,
                content = "导航回家，再播放一些轻音乐。",
            ),
            ChatMessage(
                id = 6,
                author = MessageAuthor.AGENT,
                content = "已记录演示指令，导航和媒体服务将在后续阶段接入。",
            ),
        ),
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
