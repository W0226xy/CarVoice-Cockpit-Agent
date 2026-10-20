package com.carvoice.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class IntentType {
    GENERAL_CHAT,
    NAVIGATION,
    MEDIA,
    KNOWLEDGE_QUERY,
    VEHICLE_CONTROL,
    UNKNOWN,
}

@Serializable
data class IntentResult(
    val intent: IntentType,
    val confidence: Double,
    val slots: Map<String, String> = emptyMap(),
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be between 0.0 and 1.0" }
    }
}
