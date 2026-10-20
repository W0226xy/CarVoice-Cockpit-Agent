package com.carvoice.cockpit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ToolExecutionState
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import com.carvoice.cockpit.ui.theme.CockpitSuccess
import com.carvoice.cockpit.ui.theme.CockpitWarning

@Composable
fun ToolExecutionCard(
    state: ToolExecutionUiState,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (state.state) {
        ToolExecutionState.RUNNING -> CockpitWarning
        ToolExecutionState.COMPLETED -> CockpitSuccess
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = state.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "模拟座舱 · 服务端内存态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = state.detail,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(widthDp = 560)
@Composable
private fun ToolExecutionCardPreview() {
    CarVoiceCockpitTheme {
        ToolExecutionCard(
            state = ToolExecutionUiState(
                title = "车辆状态查询",
                detail = "正在查询车辆状态",
                state = ToolExecutionState.RUNNING,
            ),
        )
    }
}
