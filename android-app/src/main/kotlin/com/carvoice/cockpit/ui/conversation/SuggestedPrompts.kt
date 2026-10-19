package com.carvoice.cockpit.ui.conversation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.QuickAction
import com.carvoice.cockpit.model.dashboardQuickActions
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun SuggestedPrompts(
    actions: List<QuickAction>,
    onPromptSelected: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        actions.forEach { action ->
            AssistChip(
                onClick = { onPromptSelected(action) },
                label = { Text(action.title) },
                modifier = Modifier.padding(horizontal = 5.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Preview(widthDp = 900)
@Composable
private fun SuggestedPromptsPreview() {
    CarVoiceCockpitTheme {
        SuggestedPrompts(
            actions = dashboardQuickActions,
            onPromptSelected = {},
        )
    }
}
