package com.carvoice.cockpit.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.QuickAction
import com.carvoice.cockpit.model.dashboardQuickActions
import com.carvoice.cockpit.ui.components.ToolExecutionCard
import com.carvoice.cockpit.ui.components.VehicleActionConfirm
import com.carvoice.cockpit.ui.components.SimulatedCockpitStateCard
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import kotlinx.coroutines.delay

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onOpenSidebar: (() -> Unit)?,
    onSuggestedPrompt: (QuickAction) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelVehicleAction: () -> Unit,
    onConfirmVehicleAction: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val hasConversationContent = state.messages.isNotEmpty() ||
        state.toolExecution != null ||
    state.simulatedCockpitState != null ||
        state.pendingVehicleAction != null ||
        state.isAssistantResponding ||
        state.error != null

    LaunchedEffect(
        state.messages.size,
        state.toolExecution,
        state.simulatedCockpitState,
        state.pendingVehicleAction,
        state.isAssistantResponding,
        state.error,
    ) {
        if (hasConversationContent) {
            delay(50)
            val lastItem = listState.layoutInfo.totalItemsCount - 1
            if (lastItem >= 0) listState.animateScrollToItem(lastItem)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ConversationTopBar(
            title = state.activeSession?.title ?: "新对话",
            connectionState = state.connectionState,
            onOpenSidebar = onOpenSidebar,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasConversationContent) {
                EmptyConversation()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .widthIn(max = 1120.dp)
                        .testTag("conversation-list"),
                    contentPadding = PaddingValues(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.messages, key = { "message-${it.id}" }) { message ->
                        ChatMessageItem(message)
                    }
                    state.toolExecution?.let { toolState ->
                        item(key = "tool-status") {
                            ToolExecutionCard(
                                state = toolState,
                                modifier = Modifier.padding(start = 52.dp),
                            )
                        }
                        state.simulatedCockpitState?.let { cockpitState ->
                            item(key = "simulated-cockpit-state") {
                                SimulatedCockpitStateCard(
                                    state = cockpitState,
                                    modifier = Modifier.padding(start = 52.dp),
                                )
                            }
                        }
                    }
                    if (state.isAssistantResponding) {
                        item(key = "thinking") {
                            ThinkingIndicator()
                        }
                    }
                    state.error?.let { error ->
                        item(key = "conversation-error") {
                            ConversationErrorMessage(
                                error = error,
                                onRetry = onRetry,
                                modifier = Modifier.padding(start = 52.dp),
                            )
                        }
                    }
                    state.pendingVehicleAction?.let { action ->
                        item(key = "vehicle-confirmation") {
                            VehicleActionConfirm(
                                action = action,
                                onCancel = onCancelVehicleAction,
                                onConfirm = onConfirmVehicleAction,
                                modifier = Modifier.padding(start = 52.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.84f)
                .widthIn(max = 1120.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasConversationContent) {
                SuggestedPrompts(
                    actions = dashboardQuickActions,
                    onPromptSelected = onSuggestedPrompt,
                )
            }
            ChatComposer(
                text = state.inputText,
                onTextChange = onInputChange,
                onSend = onSend,
                isReplying = state.isAssistantResponding,
            )
            Text(
                text = "模拟座舱 · 仅运行期内存状态，不连接真实车辆",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "AI",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "今天想在车里做点什么？",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "可以查询车况、调节座舱、规划导航或控制媒体",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(name = "空会话", widthDp = 1280, heightDp = 720)
@Composable
private fun EmptyConversationScreenPreview() {
    CarVoiceCockpitTheme {
        ConversationScreen(
            state = ConversationUiState(
                sessions = listOf(
                    com.carvoice.cockpit.model.ConversationSession(
                        id = "preview-empty",
                        title = "新对话",
                        updatedAtLabel = "刚刚",
                        messages = emptyList(),
                    ),
                ),
                activeSessionId = "preview-empty",
            ),
            onOpenSidebar = null,
            onSuggestedPrompt = {},
            onInputChange = {},
            onSend = {},
            onCancelVehicleAction = {},
            onConfirmVehicleAction = {},
            onRetry = {},
        )
    }
}

@Preview(name = "连续对话", widthDp = 1280, heightDp = 720)
@Composable
private fun ConversationScreenPreview() {
    CarVoiceCockpitTheme {
        ConversationScreen(
            state = ConversationUiState(
                sessions = listOf(
                    com.carvoice.cockpit.model.ConversationSession(
                        id = "preview-chat",
                        title = "车辆状态",
                        updatedAtLabel = "刚刚",
                        messages = listOf(
                            ChatMessage(1, MessageAuthor.USER, "当前车辆状态怎么样？"),
                            ChatMessage(
                                2,
                                MessageAuthor.AGENT,
                                "当前为模拟车辆数据：\n剩余续航 428 km，车内温度 24 ℃，胎压正常。",
                            ),
                        ),
                    ),
                ),
                activeSessionId = "preview-chat",
                respondingSessionId = "preview-chat",
            ),
            onOpenSidebar = null,
            onSuggestedPrompt = {},
            onInputChange = {},
            onSend = {},
            onCancelVehicleAction = {},
            onConfirmVehicleAction = {},
            onRetry = {},
        )
    }
}
