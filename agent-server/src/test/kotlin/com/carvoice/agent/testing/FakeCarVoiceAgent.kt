package com.carvoice.agent.testing

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.agent.ConversationTurn
import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.IntentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

class FakeCarVoiceAgent(
    private val replyChunks: List<String> = listOf("测试回复"),
) : CarVoiceAgent {
    override fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String> = flowOf(*replyChunks.toTypedArray())

    override suspend fun classifyIntent(message: String): String {
        val intent = when {
            listOf("空调", "车窗", "除雾", "风量").any(message::contains) ->
                IntentType.VEHICLE_CONTROL
            listOf("导航", "带我去").any(message::contains) -> IntentType.NAVIGATION
            listOf("播放", "音乐", "歌曲").any(message::contains) -> IntentType.MEDIA
            listOf("故障灯", "手册", "是什么意思").any(message::contains) ->
                IntentType.KNOWLEDGE_QUERY
            listOf("你好", "介绍一下", "聊聊").any(message::contains) ->
                IntentType.GENERAL_CHAT
            else -> IntentType.GENERAL_CHAT
        }
        return Json.encodeToString(
            IntentResult(intent, if (intent == IntentType.UNKNOWN) 0.0 else 0.9),
        )
    }
}
