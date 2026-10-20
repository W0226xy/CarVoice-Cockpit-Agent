package com.carvoice.cockpit.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import com.carvoice.cockpit.ui.components.WebSearchSourcesCard
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onOpenSource: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (message.author) {
        MessageAuthor.USER -> UserMessage(message.content, modifier)
        MessageAuthor.AGENT -> AssistantMessage(message, onOpenSource, modifier)
    }
}

@Composable
fun UserMessage(
    content: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = 22.dp,
                bottomEnd = 7.dp,
            ),
        ) {
            Text(
                text = content,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun AssistantMessage(
    message: ChatMessage,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 920.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        Column(
            modifier = Modifier.padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "车载助手",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (message.sources.isNotEmpty()) {
                WebSearchSourcesCard(
                    query = message.searchQuery.orEmpty(),
                    sources = message.sources,
                    onOpenSource = onOpenSource,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "thinkingPhase",
    ).value

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantAvatar()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val alpha = (
                        0.35f + 0.65f *
                            ((sin(phase * 2 * PI - index * PI / 2) + 1) / 2).toFloat()
                        )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                CircleShape,
                            ),
                    )
                }
                Text(
                    text = "正在思考",
                    modifier = Modifier.padding(start = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(38.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "AI",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(widthDp = 900)
@Composable
private fun MessageBubblePreview() {
    CarVoiceCockpitTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            ChatMessageItem(
                message = ChatMessage(
                    id = 1,
                    author = MessageAuthor.AGENT,
                    content = "当前为模拟车辆数据：\n剩余续航 428 km，车内温度 24 ℃，胎压正常。",
                ),
            )
            ChatMessageItem(
                message = ChatMessage(
                    id = 2,
                    author = MessageAuthor.USER,
                    content = "帮我导航回家，并播放一些轻音乐。",
                ),
            )
            ThinkingIndicator()
        }
    }
}
