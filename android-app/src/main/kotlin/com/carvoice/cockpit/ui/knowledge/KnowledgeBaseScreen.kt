package com.carvoice.cockpit.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.KnowledgeBaseItem
import com.carvoice.cockpit.model.mockKnowledgeBaseItems
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun KnowledgeBaseScreen(
    items: List<KnowledgeBaseItem>,
    onBackToConversation: () -> Unit,
    onOpenSidebar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Text("知识库", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(
                onClick = onBackToConversation,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("返回对话")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .widthIn(max = 1060.dp)
                .padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "管理车载助手可引用的资料",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "当前展示本地占位数据。后续可在这里选择知识库、导入文档并配置 RAG 检索范围。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("导入资料")
                }
                OutlinedButton(
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("管理知识库")
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = KnowledgeBaseItem::id) { item ->
                    KnowledgeBaseCard(item)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeBaseCard(
    item: KnowledgeBaseItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "▤",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "${item.documentCount} 份资料 · ${item.updatedAtLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(widthDp = 1000, heightDp = 720)
@Composable
private fun KnowledgeBaseScreenPreview() {
    CarVoiceCockpitTheme {
        KnowledgeBaseScreen(
            items = mockKnowledgeBaseItems,
            onBackToConversation = {},
            onOpenSidebar = null,
        )
    }
}
