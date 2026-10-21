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
        deterministicClassification(message)?.let { return it }
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

    /**
     * Rules whose meaning must not be left to the model.  In particular, an
     * internet/current-information request is still general chat: the chat task
     * owns the later decision to call web search.  KNOWLEDGE_QUERY is reserved
     * for local or future RAG-backed material.
     */
    private fun deterministicClassification(message: String): IntentResult? {
        val text = message.trim()
        val intent = when {
            vehicleControlPatterns.any { it.containsMatchIn(text) } -> IntentType.VEHICLE_CONTROL
            navigationKeywords.any(text::contains) -> IntentType.NAVIGATION
            mediaKeywords.any(text::contains) -> IntentType.MEDIA
            realTimeOrWebPatterns.any { it.containsMatchIn(text) } -> IntentType.GENERAL_CHAT
            localKnowledgePatterns.any { it.containsMatchIn(text) } -> IntentType.KNOWLEDGE_QUERY
            else -> return null
        }
        return IntentResult(intent, 0.9, extractExplicitSlots(intent, text))
    }

    private fun keywordFallback(message: String): IntentResult? =
        deterministicClassification(message)

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
        val vehicleControlPatterns = listOf(
            Regex("""(打开|开启|关闭|调到|设置).*(空调|车窗|除雾|风量)"""),
            Regex("""(空调|车窗|除雾|风量).*(打开|开启|关闭|调到|设置)"""),
        )
        val navigationKeywords = listOf("导航", "带我去", "前往")
        val mediaKeywords = listOf("播放", "音乐", "歌曲")
        val realTimeOrWebPatterns = listOf(
            Regex("""(联网|网络)\s*(搜索|查询|查)"""),
            Regex("""(搜索一下|给我搜索|查一下).*(新闻|天气|油价|价格|汇率|赛事|比赛|政策|职务)"""),
            Regex("""(新闻|天气|下雨|油价|价格|汇率|实时路况|赛事|比赛结果|政策|法规|当前职务|现任).*(今天|今日|明天|现在|当前|实时|最近|最新)"""),
            Regex("""(今天|今日|明天|现在|当前|实时|最近|最新).*(新闻|天气|下雨|油价|价格|汇率|路况|赛事|比赛|政策|法规|职务)"""),
            Regex("""(给我|提供|附上)?来源"""),
        )
        val localKnowledgePatterns = listOf(
            Regex("""知识库"""),
            Regex("""(车辆)?用户手册"""),
            Regex("""车辆手册"""),
            Regex("""说明书"""),
            Regex("""故障灯"""),
            Regex("""胎压(报警|警示|灯)?"""),
            Regex("""前挡.*除雾.*(方法|怎么|如何|使用)"""),
            Regex("""空调.*(方法|怎么|如何|使用)"""),
            Regex("""座舱功能"""),
            Regex("""上传(的)?文档"""),
            Regex("""(项目|内置).*(资料|知识库)"""),
        )
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
