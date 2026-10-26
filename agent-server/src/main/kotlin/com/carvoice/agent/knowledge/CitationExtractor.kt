package com.carvoice.agent.knowledge

/** Extracts only bracketed, one-based citation indexes emitted by the knowledge prompt. */
fun extractCitationIndexes(answer: String, maxSourceIndex: Int): List<Int> {
    if (maxSourceIndex < 1) return emptyList()
    val indexes = linkedSetOf<Int>()
    // A bracket can contain individual indexes, comma-separated indexes, or inclusive ranges.
    Regex("\\[([^]\\r\\n]+)]").findAll(answer).forEach { match ->
        match.groupValues[1].replace('，', ',').split(',').forEach { token ->
            val range = Regex("^\\s*(\\d+)\\s*[-–]\\s*(\\d+)\\s*$").matchEntire(token)
            if (range != null) {
                val start = range.groupValues[1].toIntOrNull() ?: return@forEach
                val end = range.groupValues[2].toIntOrNull() ?: return@forEach
                if (start <= end) (start..end).forEach { index ->
                    if (index in 1..maxSourceIndex) indexes += index
                }
            } else {
                val index = Regex("^\\s*(\\d+)\\s*$").matchEntire(token)?.groupValues?.get(1)?.toIntOrNull()
                if (index != null && index in 1..maxSourceIndex) indexes += index
            }
        }
    }
    return indexes.toList()
}
