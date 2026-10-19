package com.carvoice.cockpit.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ChatHistoryItem
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.mockConversationSessions
import com.carvoice.cockpit.model.toHistoryItem
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun ChatSidebar(
    historyItems: List<ChatHistoryItem>,
    activeSessionId: String,
    destination: WorkspaceDestination,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NewChatButton(onClick = onNewConversation)
            Text(
                text = "历史对话",
                modifier = Modifier.padding(horizontal = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            ConversationHistoryList(
                items = historyItems,
                activeSessionId = activeSessionId,
                onSelectConversation = onSelectConversation,
                modifier = Modifier.weight(1f),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            KnowledgeBaseEntry(
                selected = destination == WorkspaceDestination.KNOWLEDGE_BASE,
                onClick = onOpenKnowledgeBase,
            )
        }
    }
}

@Composable
fun NewChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text("+ 新建会话")
    }
}

@Composable
fun ConversationHistoryList(
    items: List<ChatHistoryItem>,
    activeSessionId: String,
    onSelectConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = ChatHistoryItem::sessionId) { item ->
            ConversationHistoryItem(
                item = item,
                selected = item.sessionId == activeSessionId,
                onClick = { onSelectConversation(item.sessionId) },
            )
        }
    }
}

@Composable
private fun ConversationHistoryItem(
    item: ChatHistoryItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.updatedAtLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = item.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun KnowledgeBaseEntry(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "▤",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("知识库", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun ChatSidebarPreview() {
    CarVoiceCockpitTheme {
        ChatSidebar(
            historyItems = mockConversationSessions.map { it.toHistoryItem() },
            activeSessionId = "session-today",
            destination = WorkspaceDestination.CONVERSATION,
            onNewConversation = {},
            onSelectConversation = {},
            onOpenKnowledgeBase = {},
        )
    }
}
