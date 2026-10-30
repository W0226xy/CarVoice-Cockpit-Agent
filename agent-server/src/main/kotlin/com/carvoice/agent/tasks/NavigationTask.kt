package com.carvoice.agent.tasks

import java.util.UUID

class NavigationTask : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult {
        val destination = context.intent.slots["destination"]?.trim()
        return if (destination.isNullOrEmpty()) {
            TaskResult.Reply("你想导航到哪里？")
        } else {
            TaskResult.NavigationAction(UUID.randomUUID().toString(), destination)
        }
    }
}
