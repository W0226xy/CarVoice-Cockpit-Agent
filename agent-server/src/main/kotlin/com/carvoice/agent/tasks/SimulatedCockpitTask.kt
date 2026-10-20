package com.carvoice.agent.tasks

import com.carvoice.contracts.ControlConfirmationRequiredPayload
import com.carvoice.contracts.SimulatedCockpitStatePayload
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SimulatedCockpitTask(
    private val clock: Clock = Clock.systemUTC(),
    private val confirmationTtlMillis: Long = 120_000,
) : TaskModule {
    private val states = ConcurrentHashMap<String, SimulatedCockpitStatePayload>()
    private val pending = ConcurrentHashMap<String, PendingControl>()

    override suspend fun execute(context: TaskContext): TaskResult {
        val operation = parseOperation(context.intent.slots)
        if (operation is ParseResult.Error) return TaskResult.Reply(operation.message)
        operation as ParseResult.Valid

        val confirmationId = UUID.randomUUID().toString()
        val expiresAt = clock.millis() + confirmationTtlMillis
        pending[confirmationId] = PendingControl(
            conversationId = context.conversationId,
            operation = operation.operation,
            expiresAtEpochMillis = expiresAt,
        )
        val description = operation.operation.description()
        return TaskResult.Confirmation(
            text = "模拟座舱控制确认请求：$description。请确认是否执行。",
            payload = ControlConfirmationRequiredPayload(
                confirmationId = confirmationId,
                description = description,
                expiresAtEpochMillis = expiresAt,
            ),
        )
    }

    fun confirm(
        conversationId: String,
        confirmationId: String,
        confirmed: Boolean,
    ): TaskResult {
        val request = pending[confirmationId]
            ?: return TaskResult.Failure("INVALID_CONFIRMATION", "确认请求无效或已处理。")
        if (request.conversationId != conversationId) {
            return TaskResult.Failure("INVALID_CONFIRMATION", "确认请求不属于当前会话。")
        }
        if (!pending.remove(confirmationId, request)) {
            return TaskResult.Failure("INVALID_CONFIRMATION", "确认请求无效或已处理。")
        }
        if (clock.millis() > request.expiresAtEpochMillis) {
            return TaskResult.Failure("EXPIRED_CONFIRMATION", "模拟座舱确认请求已过期。")
        }
        if (!confirmed) {
            return TaskResult.ControlCompleted(
                text = "已取消模拟座舱控制，状态未改变。",
                confirmationId = confirmationId,
                state = null,
                cancelled = true,
            )
        }

        val updated = states.compute(conversationId) { _, current ->
            request.operation.applyTo(current ?: SimulatedCockpitStatePayload())
        } ?: error("Cockpit state update failed")
        return TaskResult.ControlCompleted(
            text = request.operation.successMessage(),
            confirmationId = confirmationId,
            state = updated,
        )
    }

    fun state(conversationId: String): SimulatedCockpitStatePayload =
        states[conversationId] ?: SimulatedCockpitStatePayload()

    fun clearConversation(conversationId: String) {
        states.remove(conversationId)
        pending.entries.removeIf { it.value.conversationId == conversationId }
    }

    private fun parseOperation(slots: Map<String, String>): ParseResult {
        val action = slots["action"]?.trim().orEmpty()
        val acEnabled = when (action.lowercase()) {
            "enableac", "openac", "ac_on", "打开空调", "开启空调" -> true
            "disableac", "closeac", "ac_off", "关闭空调" -> false
            else -> null
        }
        val temperature = slots["targetTemperature"]?.toFloatOrNull()
        if ("targetTemperature" in slots && temperature == null) {
            return ParseResult.Error("模拟座舱温度必须是 16–30°C 之间的数字。")
        }
        if (temperature != null && temperature !in 16f..30f) {
            return ParseResult.Error("模拟座舱温度可设置范围为 16–30°C。")
        }
        val fanLevel = slots["fanLevel"]?.toIntOrNull()
        if ("fanLevel" in slots && fanLevel == null) {
            return ParseResult.Error("模拟座舱风量必须是 1–7 之间的整数。")
        }
        if (fanLevel != null && fanLevel !in 1..7) {
            return ParseResult.Error("模拟座舱风量可设置范围为 1–7。")
        }
        val defrost = slots["defrostEnabled"]?.let {
            when (it.lowercase()) {
                "true", "on", "1", "打开", "开启" -> true
                "false", "off", "0", "关闭" -> false
                else -> return ParseResult.Error("请明确要打开还是关闭模拟座舱前挡除雾。")
            }
        }
        val windowPercent = slots["windowPercent"]?.toIntOrNull()
        if ("windowPercent" in slots && windowPercent == null) {
            return ParseResult.Error("模拟座舱车窗开度必须是 0–100% 之间的整数。")
        }
        if (windowPercent != null && windowPercent !in 0..100) {
            return ParseResult.Error("模拟座舱车窗开度可设置范围为 0–100%。")
        }
        val windowPosition = slots["windowPosition"]?.lowercase()
        if (windowPosition != null && windowPosition !in setOf("driver", "passenger")) {
            return ParseResult.Error("模拟座舱仅支持主驾驶或副驾驶车窗。")
        }
        if (windowPercent != null && windowPosition == null) {
            return ParseResult.Error("请说明要调节主驾驶还是副驾驶车窗。")
        }
        if (windowPosition != null && windowPercent == null) {
            return ParseResult.Error("请说明模拟座舱车窗要打开到百分之多少。")
        }
        val operation = ControlOperation(
            acEnabled = acEnabled,
            targetTemperature = temperature,
            fanLevel = fanLevel,
            frontDefrostEnabled = defrost,
            windowPosition = windowPosition,
            windowPercent = windowPercent,
        )
        if (operation.isEmpty()) {
            return ParseResult.Error(
                "请说明要如何操作模拟座舱，例如打开空调、设置温度、风量、除雾或车窗开度。",
            )
        }
        return ParseResult.Valid(operation)
    }

    private data class PendingControl(
        val conversationId: String,
        val operation: ControlOperation,
        val expiresAtEpochMillis: Long,
    )

    private sealed interface ParseResult {
        data class Valid(val operation: ControlOperation) : ParseResult
        data class Error(val message: String) : ParseResult
    }
}

private data class ControlOperation(
    val acEnabled: Boolean?,
    val targetTemperature: Float?,
    val fanLevel: Int?,
    val frontDefrostEnabled: Boolean?,
    val windowPosition: String?,
    val windowPercent: Int?,
) {
    fun isEmpty() = listOf(
        acEnabled,
        targetTemperature,
        fanLevel,
        frontDefrostEnabled,
        windowPercent,
    ).all { it == null }

    fun applyTo(state: SimulatedCockpitStatePayload) = state.copy(
        acEnabled = acEnabled ?: state.acEnabled,
        targetTemperature = targetTemperature ?: state.targetTemperature,
        fanLevel = fanLevel ?: state.fanLevel,
        frontDefrostEnabled = frontDefrostEnabled ?: state.frontDefrostEnabled,
        driverWindowPercent = if (windowPosition == "driver") {
            requireNotNull(windowPercent)
        } else {
            state.driverWindowPercent
        },
        passengerWindowPercent = if (windowPosition == "passenger") {
            requireNotNull(windowPercent)
        } else {
            state.passengerWindowPercent
        },
    )

    fun description(): String = parts().joinToString("，")

    fun successMessage(): String =
        "已在模拟座舱中${parts().joinToString("，")}。"

    private fun parts() = buildList {
        acEnabled?.let { add(if (it) "打开空调" else "关闭空调") }
        targetTemperature?.let { add("将温度设置为 ${it.toDisplay()}°C") }
        fanLevel?.let { add("将风量设置为 $it 档") }
        frontDefrostEnabled?.let { add(if (it) "打开前挡除雾" else "关闭前挡除雾") }
        windowPercent?.let {
            val position = if (windowPosition == "driver") "主驾驶" else "副驾驶"
            add("将${position}车窗设置为 $it%")
        }
    }
}

private fun Float.toDisplay(): String =
    if (this % 1f == 0f) toInt().toString() else toString()
