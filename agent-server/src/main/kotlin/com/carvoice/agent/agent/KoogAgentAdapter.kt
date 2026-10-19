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
import kotlinx.coroutines.flow.flowOf

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
        if (requiresVehicleTool(message)) {
            return flowOf(VEHICLE_TOOL_UNAVAILABLE_MESSAGE)
        }

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

    private companion object {
        const val SYSTEM_PROMPT =
            "你是车语智舱助手，只提供普通聊天和车辆常识问答。" +
                "不得声称已读取真实车况或执行空调、车窗、导航、媒体等车辆操作。" +
                "遇到需要车辆工具的请求时，必须回复：$VEHICLE_TOOL_UNAVAILABLE_MESSAGE"
    }
}
