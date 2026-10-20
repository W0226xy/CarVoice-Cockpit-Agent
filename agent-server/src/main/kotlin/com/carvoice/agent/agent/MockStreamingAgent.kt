package com.carvoice.agent.agent

import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.IntentType
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
        val chunks = listOf(
            "你好，我是车语智舱助手。",
            "当前正在使用 Mock 模型进行流式联调。",
            "你可以继续询问车辆常识或座舱使用问题。",
        )
        chunks.forEach { chunk ->
            if (chunkDelayMillis > 0) delay(chunkDelayMillis)
            emit(chunk)
        }
    }

    override suspend fun classifyIntent(message: String): String {
        val intent = when {
            listOf("空调", "车窗", "除雾", "风量").any(message::contains) ->
                IntentType.VEHICLE_CONTROL
            listOf("导航", "带我去").any(message::contains) -> IntentType.NAVIGATION
            listOf("播放", "音乐", "歌曲").any(message::contains) -> IntentType.MEDIA
            listOf("故障灯", "手册", "是什么意思").any(message::contains) ->
                IntentType.KNOWLEDGE_QUERY
            listOf("你好", "介绍一下", "聊聊").any(message::contains) -> IntentType.GENERAL_CHAT
            else -> IntentType.UNKNOWN
        }
        return kotlinx.serialization.json.Json.encodeToString(
            IntentResult(intent, if (intent == IntentType.UNKNOWN) 0.0 else 0.9),
        )
    }
}
