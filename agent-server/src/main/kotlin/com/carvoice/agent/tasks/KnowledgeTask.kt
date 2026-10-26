package com.carvoice.agent.tasks

import com.carvoice.agent.knowledge.KnowledgeAnswerService
import com.carvoice.agent.knowledge.KnowledgeRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import com.carvoice.contracts.KnowledgeSourcePayload
import com.carvoice.contracts.KnowledgeSourcesPayload
import com.carvoice.contracts.ToolCallStatus

class KnowledgeTask(
    private val retriever: KnowledgeRetriever,
    private val answerService: KnowledgeAnswerService,
) : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult {
        val topic = context.intent.slots["topic"]?.takeIf { it.isNotBlank() } ?: context.message
        return TaskResult.Streaming(flow {
            val callId = "knowledge-search-${context.requestId}"
            emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.RUNNING, "正在检索车辆知识库"))
            val result = try { retriever.search(topic, context.knowledgeBaseId) } catch (error: CancellationException) {
                emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.CANCELLED, "知识库检索已取消")); throw error
            } catch (_: Exception) {
                emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.FAILED, "知识库检索失败")); return@flow
            }
            if (result.passages.isEmpty()) { emit(TaskStreamEvent.Delta("当前知识库中没有找到足够依据，无法回答这个问题。")); emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.COMPLETED, "车辆知识库检索完成")); return@flow }
            // Retrieved passages are model-only candidates.  The source event is deliberately
            // delayed until the completed answer tells us which candidates it actually cited.
            val retrievedPassages = result.passages
            emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.RUNNING, "正在根据资料生成回答"))
            val answer = StringBuilder()
            answerService.answer(context.message, retrievedPassages).collect {
                answer.append(it)
                emit(TaskStreamEvent.Delta(it))
            }
            val citationIndexes = com.carvoice.agent.knowledge.extractCitationIndexes(answer.toString(), retrievedPassages.size)
            if (citationIndexes.isNotEmpty()) {
                val baseId = retrievedPassages.first().knowledgeBaseId
                val citedPassages = citationIndexes.map { index -> index to retrievedPassages[index - 1] }
                emit(TaskStreamEvent.KnowledgeSources(KnowledgeSourcesPayload(
                    context.conversationId, context.requestId, baseId, result.topic,
                    citedPassages.map { (index, passage) -> KnowledgeSourcePayload(index, passage.documentId, passage.title.take(200), passage.pageNumber, passage.sectionTitle?.take(200), passage.content.take(500)) },
                )))
            }
            emit(TaskStreamEvent.ToolProgress(callId, "vehicle_knowledge_search", ToolCallStatus.COMPLETED, "车辆知识库回答完成"))
        })
    }
}

class DisabledKnowledgeTask : TaskModule {
    override suspend fun execute(context: TaskContext) = TaskResult.Reply("车辆知识库功能当前未启用。")
}
