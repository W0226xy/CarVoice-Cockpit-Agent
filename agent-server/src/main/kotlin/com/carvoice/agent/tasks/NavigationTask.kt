package com.carvoice.agent.tasks

class NavigationTask : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult {
        val destination = context.intent.slots["destination"]?.trim()
        return TaskResult.Reply(
            if (destination.isNullOrEmpty()) {
                "你想导航到哪里？"
            } else {
                "已识别导航目的地：$destination。地图导航能力将在后续阶段接入。"
            },
        )
    }
}
