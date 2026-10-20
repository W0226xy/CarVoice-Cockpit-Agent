package com.carvoice.cockpit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.ToolExecutionState
import com.carvoice.cockpit.model.ToolExecutionUiState
import com.carvoice.cockpit.model.WebSearchSourceUi
import com.carvoice.cockpit.ui.components.ToolExecutionCard
import com.carvoice.cockpit.ui.conversation.ChatMessageItem
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WebSearchComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchStatusAndSourceCardRenderAndOpenInjectedLink() {
        var openedUrl: String? = null
        composeRule.setContent {
            CarVoiceCockpitTheme {
                androidx.compose.foundation.layout.Column {
                    ToolExecutionCard(
                        ToolExecutionUiState(
                            "联网搜索",
                            "已找到 1 个来源",
                            ToolExecutionState.COMPLETED,
                        ),
                    )
                    ChatMessageItem(
                        ChatMessage(
                            id = 1,
                            author = MessageAuthor.AGENT,
                            content = "这是带来源的回答[1]",
                            serverMessageId = "request-1",
                            searchQuery = "AI 新闻",
                            sources = listOf(
                                WebSearchSourceUi(
                                    1,
                                    "来源标题",
                                    "https://example.com/news",
                                    "example.com",
                                    "来源摘要",
                                ),
                            ),
                        ),
                        onOpenSource = { openedUrl = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("已找到 1 个来源").assertIsDisplayed()
        composeRule.onNodeWithText("来源标题").assertIsDisplayed()
        composeRule.onNodeWithText("example.com").assertIsDisplayed()
        composeRule.onNodeWithTag("web-search-source-1").performClick()
        assertEquals("https://example.com/news", openedUrl)
    }
}
