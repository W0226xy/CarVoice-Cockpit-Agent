package com.carvoice.agent.tasks

class KnowledgeTask : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult =
        TaskResult.Reply("知识库检索能力尚未接入，我目前不能提供车辆手册检索结果。")
}
