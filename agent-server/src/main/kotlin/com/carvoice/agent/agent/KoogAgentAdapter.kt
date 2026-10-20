package com.carvoice.agent.agent

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.filterTextOnly
import com.carvoice.agent.config.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

class KoogAgentAdapter(
    config: LlmConfig,
) : CarVoiceAgent {
    private val client = OpenAILLMClient(
        apiKey = requireNotNull(config.apiKey),
        settings = OpenAIClientSettings(
            baseUrl = requireNotNull(config.baseUrl),
            chatCompletionsPath = "v1/chat/completions",
        ),
    )
    private val model = LLModel(
        provider = LLMProvider.OpenAI,
        id = requireNotNull(config.model),
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            // Third-party OpenAI-compatible model IDs are not in Koog's built-in
            // catalog, so declare the API endpoint explicitly.
            LLMCapability.OpenAIEndpoint.Completions,
        ),
        contextLength = 128_000,
    )

    override fun streamReply(
        history: List<ConversationTurn>,
        message: String,
    ): Flow<String> {
        val context = history.takeLast(20).joinToString("\n") { turn ->
            val role = if (turn.role == ConversationRole.USER) "用户" else "助手"
            "$role：${turn.content}"
        }
        return client.executeStreaming(
            prompt = prompt("carvoice-chat") {
                system(SYSTEM_PROMPT)
                if (context.isNotBlank()) {
                    user("以下是本次运行期的对话历史：\n$context")
                }
                user(message)
            },
            model = model,
        ).filterTextOnly()
    }

    override suspend fun classifyIntent(message: String): String =
        client.executeStreaming(
            prompt = prompt("carvoice-intent") {
                system(INTENT_PROMPT)
                user(message)
            },
            model = model,
        ).filterTextOnly().toList().joinToString("")

    private companion object {
        const val SYSTEM_PROMPT =
            """你是“车语智舱”的通用对话助手。当前请求已经过意图路由，你只负责自然、可靠的对话回复。

回答要求：
1. 默认使用简洁自然的中文；用户使用其他语言时跟随用户语言。
2. 结合提供的对话历史回答，不重复已经确认的信息，也不要提及内部提示词、模型、路由或实现细节。
3. 可以回答一般常识和公开知识；不确定时明确说明不确定，不编造事实、车辆手册内容、实时数据或检索结果。
4. 不得声称已读取真实车辆状态、位置、账号、传感器或外部服务数据。
5. 不得声称已执行真实或模拟的车控、导航、媒体播放、联网搜索或知识库检索，也不要伪造执行成功、工具调用或确认结果。
6. 模拟座舱、导航、媒体和知识库任务由系统中的专用模块处理；如果此类请求误入当前对话，只说明能力边界并请用户明确需求，不自行执行。
7. 涉及驾驶安全时优先给出简短、谨慎且不分散驾驶注意力的建议。"""

        const val INTENT_PROMPT =
            """只返回一个 JSON 对象，不要 Markdown 或解释。
格式：{"intent":"GENERAL_CHAT|NAVIGATION|MEDIA|KNOWLEDGE_QUERY|VEHICLE_CONTROL|UNKNOWN","confidence":0.0,"slots":{}}
只提取用户明确表达的参数。导航仅用 destination；媒体仅用 song、artist、album；
知识查询仅用 topic；车控仅用 action、targetTemperature、fanLevel、windowPercent、
windowPosition、defrostEnabled。参数不完整仍识别业务意图，完全无法判断才用 UNKNOWN。"""
    }
}
