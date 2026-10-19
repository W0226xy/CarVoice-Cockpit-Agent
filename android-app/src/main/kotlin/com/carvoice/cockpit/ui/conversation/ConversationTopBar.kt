package com.carvoice.cockpit.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.ui.components.ConnectionStatus
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun ConversationTopBar(
    title: String,
    connectionState: ConnectionState,
    onOpenSidebar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onOpenSidebar?.let { openSidebar ->
                TextButton(
                    onClick = openSidebar,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("会话")
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionStatus(connectionState)
            TextButton(
                enabled = false,
                onClick = {},
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("更多")
            }
        }
    }
}

@Preview(widthDp = 1000)
@Composable
private fun ConversationTopBarPreview() {
    CarVoiceCockpitTheme {
        ConversationTopBar(
            title = "新对话",
            connectionState = ConnectionState.DISCONNECTED,
            onOpenSidebar = null,
        )
    }
}
