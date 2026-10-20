package com.carvoice.agent.tasks

class MediaTask : TaskModule {
    override suspend fun execute(context: TaskContext): TaskResult {
        val slots = context.intent.slots
        val description = listOfNotNull(
            slots["artist"]?.let { "歌手 $it" },
            slots["song"]?.let { "歌曲 $it" },
            slots["album"]?.let { "专辑 $it" },
        ).joinToString("，")
        return TaskResult.Reply(
            if (description.isBlank()) {
                "你想播放哪首歌、哪位歌手或哪个专辑？"
            } else {
                "已识别播放请求：$description。媒体 App 联动将在后续阶段接入。"
            },
        )
    }
}
