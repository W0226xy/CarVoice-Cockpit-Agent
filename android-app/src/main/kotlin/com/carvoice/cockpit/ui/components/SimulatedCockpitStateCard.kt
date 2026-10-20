package com.carvoice.cockpit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.SimulatedCockpitStateUi

@Composable
fun SimulatedCockpitStateCard(
    state: SimulatedCockpitStateUi,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("simulated-cockpit-state-card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("模拟座舱状态", style = MaterialTheme.typography.titleMedium)
            StateRow("空调", if (state.acEnabled) "已开启" else "已关闭")
            StateRow("温度 / 风量", "${state.targetTemperature.toDisplay()}°C / ${state.fanLevel} 档")
            StateRow("前挡除雾", if (state.frontDefrostEnabled) "已开启" else "已关闭")
            StateRow(
                "车窗",
                "主驾驶 ${state.driverWindowPercent}% · 副驾驶 ${state.passengerWindowPercent}%",
            )
        }
    }
}

@Composable
private fun StateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun Float.toDisplay() = if (this % 1f == 0f) toInt().toString() else toString()
