package com.carvoice.agent.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockStreamingAgent(
    private val chunkDelayMillis: Long = 120,
) : CarVoiceAgent {
    override fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String> = flow {
        val chunks = if (requiresVehicleTool(message)) {
            listOf("车辆工具将在后续阶段接入，", "目前无法执行该操作。")
        } else {
            listOf(
                "你好，我是车语智舱助手。",
                "当前正在使用 Mock 模型进行流式联调。",
                "你可以继续询问车辆常识或座舱使用问题。",
            )
        }
        chunks.forEach { chunk ->
            if (chunkDelayMillis > 0) delay(chunkDelayMillis)
            emit(chunk)
        }
    }
}
