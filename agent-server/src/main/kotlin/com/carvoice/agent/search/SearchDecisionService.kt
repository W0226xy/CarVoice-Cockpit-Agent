package com.carvoice.agent.search

import com.carvoice.agent.agent.CarVoiceAgent
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json

class SearchDecisionService(
    private val agent: CarVoiceAgent,
    private val confidenceThreshold: Double = 0.65,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    suspend fun decide(message: String): SearchDecision {
        deterministicDecision(message)?.let { return it }
        val decision = runCatching {
            agent.decideWebSearch(message)?.let(::parse)
        }.getOrNull()
        return decision
            ?.takeIf { it.confidence >= confidenceThreshold }
            ?.let { it.copy(query = it.query?.let(::sanitizeQuery)) }
            ?.takeIf { !it.shouldSearch || !it.query.isNullOrBlank() }
            ?: noSearch("无法可靠判断是否需要联网")
    }

    internal fun parse(raw: String): SearchDecision =
        strictJson.decodeFromString<SearchDecision>(raw.trim())

    private fun deterministicDecision(message: String): SearchDecision? {
        val text = message.trim()
        if (explicitSearchPatterns.any { it.containsMatchIn(text) }) {
            return search("用户明确要求联网或来源", text)
        }
        if (timeSensitivePatterns.any { it.containsMatchIn(text) }) {
            return search("请求涉及时效性信息", text)
        }
        if (noSearchPatterns.any { it.matches(text) }) {
            return noSearch("稳定知识、闲聊或基于现有上下文的请求")
        }
        return null
    }

    private fun search(reason: String, message: String): SearchDecision {
        val query = buildQuery(message)
        return if (query.isBlank()) {
            noSearch("缺少可用于联网检索的具体问题")
        } else {
            SearchDecision(
                shouldSearch = true,
                reason = reason,
                query = query,
                confidence = 1.0,
            )
        }
    }

    private fun noSearch(reason: String) = SearchDecision(
        shouldSearch = false,
        reason = reason,
        confidence = 1.0,
    )

    internal fun sanitizeQuery(message: String): String =
        message
            .replace(
                Regex("(?i)authorization\\s*[:=]\\s*(?:bearer\\s+)?\\S+"),
                "",
            )
            .replace(Regex("(?i)api[_-]?key\\s*[:=]\\s*\\S+"), "")
            .replace(Regex("(?i)bearer\\s+\\S+"), "")
            .replace(Regex("""(请|帮我|麻烦)?(联网)?(搜索|查一下|查询一下|查查)"""), "")
            .replace(Regex("""(给我|提供|附上)?来源"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_QUERY_LENGTH)

    private fun buildQuery(message: String): String {
        var query = sanitizeQuery(message)
        if (relativeDatePatterns.any { it.containsMatchIn(query) }) {
            val date = LocalDate.now(clock).toString()
            query = query.replace(relativeDatePattern, date)
        }
        return query
            .replace("会下雨", "天气")
            .replace("下雨", "天气")
            .replace(Regex("""(有哪些|有什么|怎么样|是多少|吗|呢)"""), "")
            .replace(Regex("""(\d{4}-\d{2}-\d{2})([\p{IsHan}])"""), "$1 $2")
            .replace(Regex("""([\p{IsHan}])(新闻|天气|油价|价格|汇率)"""), "$1 $2")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_QUERY_LENGTH)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
        val noSearchPatterns = listOf(
            Regex("""^(你好|您好|嗨|hello|hi)[！!，,。\s]*(你能做什么)?[？?！!。\s]*$""", RegexOption.IGNORE_CASE),
            Regex("""^.*(解释|介绍).*(概念|原理|StateFlow|Kotlin).*$""", RegexOption.IGNORE_CASE),
            Regex("""^.*(写|修改|解释).*(代码|函数|程序).*$"""),
            Regex("""^.*总结(我|刚才|上面|以下|这段).*$"""),
        )
        val explicitSearchPatterns = listOf(
            Regex("""(联网查|联网搜索|搜索一下|帮我查|查一下|给我来源|提供来源|附上来源)"""),
            Regex("""(最新消息|最近.*新闻|最新.*版本|当前.*版本)"""),
        )
        val timeSensitivePatterns = listOf(
            Regex("""(今天|明天|现在|当前|实时|最近|最新).*(天气|下雨|新闻|路况|油价|价格|汇率|比赛|政策|法规|版本|职务)"""),
            Regex("""(天气|新闻|实时路况|油价|汇率|比赛结果|政策法规|现任|当前职务)"""),
        )
        val relativeDatePattern = Regex("""今天|今日|现在|当前""")
        val relativeDatePatterns = listOf(relativeDatePattern)
    }
}
