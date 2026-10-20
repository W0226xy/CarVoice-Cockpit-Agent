package com.carvoice.cockpit.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.carvoice.cockpit.model.VehicleActionUi
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun VehicleActionConfirm(
    action: VehicleActionUi,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onCancel,
        title = { Text("模拟座舱确认") },
        text = {
            Text(
                "${action.description}\n\n${action.riskMessage}\n" +
                    "不会连接或修改真实车辆。",
            )
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认执行")
            }
        },
    )
}

@Preview
@Composable
private fun VehicleActionConfirmPreview() {
    CarVoiceCockpitTheme {
        VehicleActionConfirm(
            action = VehicleActionUi(
                title = "模拟座舱确认",
                description = "打开空调并将温度设置为 24°C",
                riskMessage = "确认后只修改模拟状态。",
            ),
            onCancel = {},
            onConfirm = {},
        )
    }
}
