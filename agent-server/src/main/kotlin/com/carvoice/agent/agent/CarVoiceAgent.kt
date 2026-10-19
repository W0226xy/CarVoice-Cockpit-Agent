package com.carvoice.agent.agent

import kotlinx.coroutines.flow.Flow

data class ConversationTurn(
    val role: ConversationRole,
    val content: String,
)

enum class ConversationRole {
    USER,
    ASSISTANT,
}

interface CarVoiceAgent {
    fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String>
}

const val VEHICLE_TOOL_UNAVAILABLE_MESSAGE =
    "车辆工具将在后续阶段接入，目前无法执行该操作。"

internal fun requiresVehicleTool(message: String): Boolean {
    val normalized = message.lowercase()
    val directQueries = listOf("真实车况", "当前车况", "车辆状态", "剩余续航")
    val actionTargets = listOf("空调", "车窗", "导航", "音乐", "媒体")
    val actionVerbs = listOf("打开", "关闭", "设置", "调节", "导航", "播放", "暂停", "查询")
    return directQueries.any(normalized::contains) ||
        (actionTargets.any(normalized::contains) && actionVerbs.any(normalized::contains))
}
