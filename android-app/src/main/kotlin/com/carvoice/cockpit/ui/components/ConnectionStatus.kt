package com.carvoice.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import com.carvoice.cockpit.ui.theme.CockpitDanger
import com.carvoice.cockpit.ui.theme.CockpitSuccess
import com.carvoice.cockpit.ui.theme.CockpitWarning

@Composable
fun ConnectionStatus(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val indicatorColor = when (state) {
        ConnectionState.CONNECTED -> CockpitSuccess
        ConnectionState.CONNECTING -> CockpitWarning
        ConnectionState.DISCONNECTED -> CockpitDanger
        ConnectionState.FAILED -> CockpitDanger
    }

    Surface(
        modifier = modifier,
        color = indicatorColor.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(indicatorColor, CircleShape),
            )
            Text(
                text = state.label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Preview
@Composable
private fun ConnectionStatusPreview() {
    CarVoiceCockpitTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectionState.entries.forEach { ConnectionStatus(it) }
        }
    }
}
