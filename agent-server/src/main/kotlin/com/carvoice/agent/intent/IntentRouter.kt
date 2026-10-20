package com.carvoice.agent.intent

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.contracts.IntentResult
import com.carvoice.contracts.IntentType
import kotlinx.serialization.json.Json

class IntentRouter(
    private val model: CarVoiceAgent,
    private val confidenceThreshold: Double = 0.6,
) {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    suspend fun classify(message: String): IntentResult {
        val modelResult = runCatching {
            model.classifyIntent(message)?.let(::parse)
        }.getOrNull()
        if (modelResult != null && modelResult.confidence >= confidenceThreshold) {
            val explicitSlots = extractExplicitSlots(modelResult.intent, message)
            return modelResult.copy(slots = explicitSlots + modelResult.slots)
        }
        return keywordFallback(message) ?: IntentResult(IntentType.UNKNOWN, 0.0)
    }

    internal fun parse(raw: String): IntentResult {
        val decoded = strictJson.decodeFromString<IntentResult>(raw.trim())
        val allowedKeys = allowedSlots.getValue(decoded.intent)
        require(decoded.slots.keys.all { it in allowedKeys }) { "Unsupported intent slot" }
        require(decoded.slots.values.all { it.isNotBlank() }) { "Blank intent slot" }
        return decoded.copy(slots = decoded.slots.mapValues { it.value.trim() })
    }

    private fun keywordFallback(message: String): IntentResult? {
        val text = message.trim()
        val intent = when {
            vehicleKeywords.any(text::contains) -> IntentType.VEHICLE_CONTROL
            navigationKeywords.any(text::contains) -> IntentType.NAVIGATION
            mediaKeywords.any(text::contains) -> IntentType.MEDIA
            knowledgeKeywords.any(text::contains) -> IntentType.KNOWLEDGE_QUERY
            else -> return null
        }
        return IntentResult(intent, 0.7, extractExplicitSlots(intent, text))
    }

    private fun extractExplicitSlots(intent: IntentType, text: String): Map<String, String> =
        when (intent) {
            IntentType.NAVIGATION -> {
                Regex("""(?:导航|带我|前往|去)(?:到|去)?\s*([^，。！？]+)""")
                    .find(text)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() && it !in listOf("哪里", "哪儿") }
                    ?.let { mapOf("destination" to it) }.orEmpty()
            }
            IntentType.MEDIA -> buildMap {
                Regex("""播放(?:一下)?(?:([\p{L}]{2,8})的)?《?([^，。！？》]+)""").find(text)
                    ?.let { match ->
                        match.groupValues[1].trim().takeIf(String::isNotEmpty)?.let {
                            put("artist", it)
                        }
                        match.groupValues[2].trim().takeIf { it != "音乐" }?.let {
                            put("song", it)
                        }
                    }
            }
            IntentType.KNOWLEDGE_QUERY -> mapOf(
                "topic" to text.replace(Regex("""(这个|是什么意思|是什么|请问|帮我查一下)"""), "")
                    .trim('，', '。', '？', '?', ' '),
            ).filterValues(String::isNotBlank)
            IntentType.VEHICLE_CONTROL -> buildMap {
                when {
                    "空调" in text && "关闭" in text -> put("action", "disableAc")
                    "空调" in text && ("打开" in text || "开启" in text) ->
                        put("action", "enableAc")
                    "风量" in text -> put("action", "setFanLevel")
                    "车窗" in text -> put("action", "setWindow")
                    "除雾" in text -> put("action", "setFrontDefrost")
                }
                Regex("""(\d+(?:\.\d+)?)\s*(?:度|℃)""").find(text)
                    ?.groupValues?.get(1)?.let { put("targetTemperature", it) }
                Regex("""风量(?:调到|设置为|到)?\s*(\d)""").find(text)
                    ?.groupValues?.get(1)?.let { put("fanLevel", it) }
                val percent = when {
                    "一半" in text -> "50"
                    "全开" in text -> "100"
                    "关闭" in text && "车窗" in text -> "0"
                    else -> Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)
                }
                percent?.let { put("windowPercent", it) }
                when {
                    "主驾驶" in text || "驾驶位" in text -> put("windowPosition", "driver")
                    "副驾驶" in text -> put("windowPosition", "passenger")
                }
                if ("除雾" in text) {
                    put("defrostEnabled", (!listOf("关闭", "取消").any(text::contains)).toString())
                }
            }
            else -> emptyMap()
        }

    private companion object {
        val vehicleKeywords = listOf("空调", "车窗", "除雾", "风量")
        val navigationKeywords = listOf("导航", "带我去", "前往")
        val mediaKeywords = listOf("播放", "音乐", "歌曲")
        val knowledgeKeywords = listOf("故障灯", "车辆手册", "是什么意思")
        val allowedSlots = mapOf(
            IntentType.GENERAL_CHAT to emptySet(),
            IntentType.NAVIGATION to setOf("destination"),
            IntentType.MEDIA to setOf("song", "artist", "album"),
            IntentType.KNOWLEDGE_QUERY to setOf("topic"),
            IntentType.VEHICLE_CONTROL to setOf(
                "action",
                "targetTemperature",
                "fanLevel",
                "windowPercent",
                "windowPosition",
                "defrostEnabled",
            ),
            IntentType.UNKNOWN to emptySet(),
        )
    }
}
