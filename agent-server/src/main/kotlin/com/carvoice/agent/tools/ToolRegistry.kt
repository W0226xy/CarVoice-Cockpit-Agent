package com.carvoice.agent.tools

import com.carvoice.contracts.ToolDefinition
import com.carvoice.contracts.ToolExecutionRequest
import com.carvoice.contracts.ToolExecutionResult

interface ToolRegistry {
    fun definitions(): List<ToolDefinition>

    suspend fun execute(request: ToolExecutionRequest): ToolExecutionResult
}

/**
 * The only vehicle capability boundary allowed during the MVP.
 * Implementations must simulate state and must not connect to Vehicle HAL, CAN, or physical vehicles.
 */
interface MockVehicleTool {
    suspend fun execute(request: ToolExecutionRequest): ToolExecutionResult
}
