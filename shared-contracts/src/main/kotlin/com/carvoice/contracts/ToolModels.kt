package com.carvoice.contracts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean,
    val inputSchema: JsonObject,
)

@Serializable
data class ToolExecutionRequest(
    val requestId: String,
    val callId: String,
    val toolName: String,
    val arguments: JsonObject,
    val userConfirmed: Boolean = false,
)

@Serializable
data class ToolCallProgress(
    val requestId: String,
    val callId: String,
    val toolName: String,
    val status: ToolCallStatus,
    val message: String? = null,
)

@Serializable
data class ToolExecutionResult(
    val requestId: String,
    val callId: String,
    val toolName: String,
    val success: Boolean,
    val output: JsonObject? = null,
    val error: ErrorDetail? = null,
)

@Serializable
enum class RiskLevel {
    READ_ONLY,
    LOW_RISK_EXECUTION,
    HIGH_RISK_EXECUTION,
}

@Serializable
enum class ToolCallStatus {
    PENDING_CONFIRMATION,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
