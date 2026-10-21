package com.carvoice.agent.knowledge

import kotlin.math.sqrt

data class KnowledgeDocument(val id: String, val title: String, val content: String)

/** A small, replaceable phase-one repository. It has no persistence or network access. */
class InMemoryKnowledgeRepository(
    private val documents: List<KnowledgeDocument>,
    private val embeddingService: EmbeddingService,
    private val minimumScore: Double = 0.16,
) : KnowledgeRepository {
    override suspend fun search(query: KnowledgeSearchQuery, limit: Int): List<KnowledgePassage> {
        require(limit > 0) { "limit must be positive" }
        return documents.map { document ->
            val documentEmbedding = embeddingService.embed("${document.title}\n${document.content}")
            KnowledgePassage(document.id, document.title, document.content, cosine(query.embedding, documentEmbedding))
        }.filter { it.score >= minimumScore }.sortedByDescending { it.score }.take(limit)
    }

    private fun cosine(left: List<Float>, right: List<Float>): Double {
        if (left.isEmpty() || left.size != right.size) return 0.0
        var dot = 0.0; var leftMagnitude = 0.0; var rightMagnitude = 0.0
        left.indices.forEach { index ->
            val a = left[index].toDouble(); val b = right[index].toDouble()
            dot += a * b; leftMagnitude += a * a; rightMagnitude += b * b
        }
        return if (leftMagnitude == 0.0 || rightMagnitude == 0.0) 0.0 else dot / (sqrt(leftMagnitude) * sqrt(rightMagnitude))
    }
}

/** Deterministic local embedding for the seed corpus; replace with a model/pgvector pipeline later. */
class CharacterNgramEmbeddingService(private val dimensions: Int = 128) : EmbeddingService {
    override suspend fun embed(text: String): List<Float> {
        val vector = FloatArray(dimensions)
        val normalized = text.lowercase().filterNot(Char::isWhitespace)
        val tokens = normalized.map(Char::toString) + normalized.windowed(2, partialWindows = false)
        tokens.forEach { token -> vector[(token.hashCode() and Int.MAX_VALUE) % dimensions] += 1f }
        return vector.toList()
    }
}

fun phaseOneVehicleKnowledge(): List<KnowledgeDocument> = listOf(
    KnowledgeDocument("tire-pressure-warning", "胎压报警灯", "胎压报警灯点亮时，请在安全地点停车并检查轮胎外观。轮胎明显亏气、损伤或报警持续时，不要高速行驶，应联系专业服务人员检查胎压与轮胎。"),
    KnowledgeDocument("engine-malfunction-light", "发动机故障灯", "发动机故障灯常亮表示发动机或排放系统检测到异常。请避免激烈驾驶；若故障灯闪烁、车辆抖动或动力明显下降，应尽快安全停车并联系专业服务人员。"),
    KnowledgeDocument("front-windshield-defog", "前挡风玻璃除雾", "前挡风玻璃起雾时，开启前挡除雾功能，并将出风模式调至前挡风玻璃。可适当提高风量，必要时开启空调以帮助除湿；视野未清晰前请勿继续驾驶。"),
    KnowledgeDocument("air-conditioning-use", "空调使用方法", "按下空调开关可开启或关闭空调。使用温度调节设置目标温度，使用风量调节改变送风强度。长时间停车时应避免持续使用空调，以免消耗蓄电池电量。"),
)
