package com.carvoice.cockpit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.carvoice.cockpit.data.ConversationRepository
import com.carvoice.cockpit.data.ConversationStreamEvent
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.ui.CarVoiceApp
import com.carvoice.cockpit.ui.conversation.ConversationViewModel
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CarVoiceAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repository: FakeConversationRepository

    @Before
    fun setUp() {
        repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository)
        composeRule.setContent {
            CarVoiceCockpitTheme {
                CarVoiceApp(conversationViewModel = viewModel)
            }
        }
    }

    @Test
    fun localConversationStreamsAndStartsNewSession() {
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithTag("chat-input").performTextInput("你好，座舱")
        composeRule.onNodeWithText("发送").performClick()
        composeRule.waitUntil { repository.sent.isNotEmpty() }
        val sent = repository.sent.single()

        runBlocking {
            repository.emit(
                ConversationStreamEvent.AssistantDelta(
                    sent.requestId,
                    sent.conversationId,
                    "你好，",
                ),
            )
            repository.emit(
                ConversationStreamEvent.AssistantDelta(
                    sent.requestId,
                    sent.conversationId,
                    "欢迎使用车语智舱。",
                ),
            )
            repository.emit(
                ConversationStreamEvent.AssistantCompleted(
                    sent.requestId,
                    sent.conversationId,
                ),
            )
        }

        assertTrue(
            composeRule
                .onAllNodesWithText("你好，欢迎使用车语智舱。")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("今天想在车里做点什么？").assertIsDisplayed()
    }

    @Test
    fun airConditionerConfirmationStaysLocal() {
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("打开空调").performClick()
        composeRule.onNodeWithText("确认执行车辆操作").assertIsDisplayed()
        composeRule.onNodeWithText("确认执行").performClick()

        assertTrue(
            composeRule
                .onAllNodesWithText("车辆工具将在后续阶段接入，目前无法执行该操作。")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun initialConversationHasNoSeededHistory() {
        composeRule.onNodeWithText("暂无历史对话").assertIsDisplayed()
        composeRule.onAllNodesWithText("今日出行助手").assertCountEquals(0)
        composeRule.onNodeWithText("今天想在车里做点什么？").assertIsDisplayed()
    }

    @Test
    fun historyAndKnowledgeNavigationRemainAvailable() {
        composeRule.onNodeWithTag("chat-input").performTextInput("历史恢复文本")
        composeRule.onNodeWithText("发送").performClick()
        composeRule.waitUntil { repository.sent.isNotEmpty() }
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("历史恢复文本").performClick()
        assertTrue(composeRule.onAllNodesWithText("历史恢复文本").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithText("知识库").performClick()
        composeRule.onNodeWithText("车机使用手册").assertIsDisplayed()
        composeRule.onNodeWithText("返回对话").performClick()
        assertTrue(composeRule.onAllNodesWithText("历史恢复文本").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun deleteConversationRequiresConfirmation() {
        composeRule.onNodeWithTag("chat-input").performTextInput("待删除会话")
        composeRule.onNodeWithText("发送").performClick()
        composeRule.waitUntil { repository.sent.isNotEmpty() }

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("删除会话").performClick()
        composeRule.onNodeWithText("删除此会话？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(composeRule.onAllNodesWithText("待删除会话").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("删除会话").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("暂无历史对话").assertIsDisplayed()
    }
}

private class FakeConversationRepository : ConversationRepository {
    data class SentMessage(
        val conversationId: String,
        val requestId: String,
        val message: String,
    )

    override val connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    private val eventFlow = MutableSharedFlow<ConversationStreamEvent>(extraBufferCapacity = 16)
    override val events: Flow<ConversationStreamEvent> = eventFlow
    val sent = mutableListOf<SentMessage>()

    override fun connect() = Unit

    override suspend fun sendMessage(
        conversationId: String,
        requestId: String,
        message: String,
    ): Result<Unit> {
        sent += SentMessage(conversationId, requestId, message)
        return Result.success(Unit)
    }

    suspend fun emit(event: ConversationStreamEvent) {
        eventFlow.emit(event)
    }

    override fun close() = Unit
}
