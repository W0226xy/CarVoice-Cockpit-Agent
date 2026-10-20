package com.carvoice.agent.tasks

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.search.DisabledWebSearchPort
import com.carvoice.agent.search.SearchAnswerContextBuilder
import com.carvoice.agent.search.SearchDecisionService
import com.carvoice.agent.search.WebSearchFailureReason
import com.carvoice.agent.search.WebSearchOutcome
import com.carvoice.agent.search.WebSearchPort
import com.carvoice.contracts.ToolCallStatus
import com.carvoice.contracts.WebSearchSourcePayload
import com.carvoice.contracts.WebSearchSourcesPayload
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class ChatTask(
    private val agent: CarVoiceAgent,
    private val searchDecisionService: SearchDecisionService = SearchDecisionService(agent),
    private val webSearchPort: WebSearchPort = DisabledWebSearchPort(),
    private val contextBuilder: SearchAnswerContextBuilder = SearchAnswerContextBuilder(6_000),
) : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult =
        TaskResult.Streaming(
            flow {
                val decision = searchDecisionService.decide(context.message)
                if (!decision.shouldSearch) {
                    agent.streamReply(context.history, context.message).collect {
                        emit(TaskStreamEvent.Delta(it))
                    }
                    return@flow
                }

                val callId = "web-search-${context.requestId}"
                emit(
                    TaskStreamEvent.ToolProgress(
                        callId,
                        WEB_SEARCH_TOOL,
                        ToolCallStatus.RUNNING,
                        "正在联网搜索",
                    ),
                )
                when (val outcome = webSearchPort.search(requireNotNull(decision.query))) {
                    is WebSearchOutcome.Success -> {
                        val result = outcome.result
                        val sources = result.sources.mapIndexed { index, source ->
                            WebSearchSourcePayload(
                                index = index + 1,
                                title = source.title,
                                url = source.url,
                                domain = source.domain,
                                snippet = source.snippet,
                                publishedAt = source.publishedAt,
                            )
                        }
                        emit(
                            TaskStreamEvent.SearchSources(
                                WebSearchSourcesPayload(
                                    conversationId = context.conversationId,
                                    messageId = context.requestId,
                                    query = result.query,
                                    sources = sources,
                                ),
                            ),
                        )
                        emit(
                            TaskStreamEvent.ToolProgress(
                                callId,
                                WEB_SEARCH_TOOL,
                                ToolCallStatus.COMPLETED,
                                "已找到 ${sources.size} 个来源",
                            ),
                        )
                        agent.streamReplyWithSearch(
                            context.history,
                            context.message,
                            contextBuilder.build(result),
                        ).collect {
                            emit(TaskStreamEvent.Delta(it))
                        }
                    }
                    is WebSearchOutcome.Failure -> {
                        emit(
                            TaskStreamEvent.ToolProgress(
                                callId,
                                WEB_SEARCH_TOOL,
                                ToolCallStatus.FAILED,
                                outcome.reason.toSafeMessage(),
                            ),
                        )
                        emit(TaskStreamEvent.Delta(SEARCH_UNAVAILABLE_NOTICE))
                        agent.streamReply(context.history, context.message).collect {
                            emit(TaskStreamEvent.Delta(it))
                        }
                    }
                }
            },
        )

    private fun WebSearchFailureReason.toSafeMessage(): String = when (this) {
        WebSearchFailureReason.DISABLED -> "联网搜索未启用，已切换为普通回答"
        WebSearchFailureReason.TIMEOUT -> "联网搜索超时，已切换为普通回答"
        WebSearchFailureReason.NO_RESULTS -> "未找到有效来源，已切换为普通回答"
        WebSearchFailureReason.REMOTE_ERROR,
        WebSearchFailureReason.INVALID_RESPONSE,
        -> "联网搜索暂时不可用，已切换为普通回答"
    }

    private companion object {
        const val WEB_SEARCH_TOOL = "tavily_web_search"
        const val SEARCH_UNAVAILABLE_NOTICE =
            "联网搜索暂时未能获得有效结果，以下回答不包含实时搜索来源。\n\n"
    }
}
