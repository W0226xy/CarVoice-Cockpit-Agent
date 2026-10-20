package com.carvoice.agent.tasks

import com.carvoice.agent.agent.CarVoiceAgent

class ChatTask(
    private val agent: CarVoiceAgent,
) : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult =
        TaskResult.Streaming(agent.streamReply(context.history, context.message))
}
