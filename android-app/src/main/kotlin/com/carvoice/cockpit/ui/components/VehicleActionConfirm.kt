package com.carvoice.cockpit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import com.carvoice.cockpit.ui.theme.CockpitWarning

@Composable
fun VehicleActionConfirm(
    action: VehicleActionUi,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 760.dp),
        color = CockpitWarning.copy(alpha = 0.1f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(action.title, style = MaterialTheme.typography.titleMedium)
            Text(action.description, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = action.riskMessage,
                color = CockpitWarning,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "仅演示确认流程，不会连接或修改真实车辆。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(end = 10.dp),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("确认执行")
                }
            }
        }
    }
}

@Preview(widthDp = 700, heightDp = 420)
@Composable
private fun VehicleActionConfirmPreview() {
    CarVoiceCockpitTheme {
        VehicleActionConfirm(
            action = VehicleActionUi(
                title = "确认执行车辆操作",
                description = "将空调设置为 24 ℃",
                riskMessage = "该操作会修改车辆状态，请确认后执行。",
            ),
            onCancel = {},
            onConfirm = {},
        )
    }
}
