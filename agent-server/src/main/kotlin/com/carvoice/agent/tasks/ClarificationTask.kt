package com.carvoice.agent.tasks

class ClarificationTask : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult =
        TaskResult.Reply("我可以帮你聊天、导航、播放音乐或操作模拟座舱。你希望我做什么？")
}
