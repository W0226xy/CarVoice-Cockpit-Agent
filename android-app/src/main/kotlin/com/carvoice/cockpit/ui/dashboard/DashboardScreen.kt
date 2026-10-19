package com.carvoice.cockpit.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.QuickAction
import com.carvoice.cockpit.model.dashboardQuickActions
import com.carvoice.cockpit.ui.components.ConnectionStatus
import com.carvoice.cockpit.ui.components.QuickActionCard
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    onQuickAction: (QuickAction) -> Unit,
    onStartConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "车语智舱 Agent",
                style = MaterialTheme.typography.headlineSmall,
            )
            ConnectionStatus(connectionState)
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .padding(vertical = 18.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "你好，我是车语智舱助手",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "我可以帮你查询车况、控制车内功能、导航和播放媒体",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = onStartConversation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                ) {
                    Text("开始对话")
                }
            }

            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(
                    space = 16.dp,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                dashboardQuickActions.chunked(2).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowActions.forEach { action ->
                            QuickActionCard(
                                action = action,
                                onClick = { onQuickAction(action) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "车机主页", widthDp = 1280, heightDp = 720)
@Composable
private fun DashboardScreenPreview() {
    CarVoiceCockpitTheme {
        DashboardScreen(
            connectionState = ConnectionState.DISCONNECTED,
            onQuickAction = {},
            onStartConversation = {},
        )
    }
}
