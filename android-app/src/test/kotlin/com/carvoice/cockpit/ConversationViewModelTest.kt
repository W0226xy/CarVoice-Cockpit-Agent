package com.carvoice.cockpit

import com.carvoice.cockpit.data.ConversationRepository
import com.carvoice.cockpit.data.ConversationStreamEvent
import com.carvoice.cockpit.data.ConversationHistoryRepository
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.MessageStatus
import com.carvoice.cockpit.model.ToolExecutionState
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.contracts.SimulatedCockpitStatePayload
import com.carvoice.contracts.ToolCallStatus
import com.carvoice.contracts.WebSearchSourcePayload
import com.carvoice.contracts.KnowledgeSourcePayload
import com.carvoice.cockpit.ui.conversation.ConversationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationViewModelTest {
    @Test
    fun `old Room snapshots do not replace optimistic or streaming messages`() = runBlocking {
        val repository = FakeConversationRepository()
        val history = ControllableHistoryRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default, history)
        delay(50)

        viewModel.updateInput("第一条")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()
        history.emitSessions(emptyList())
        history.emitMessages(request.conversationId, emptyList())
        delay(50)
        assertEquals(listOf("第一条"), viewModel.uiState.value.messages.map { it.content })

        repository.emit(ConversationStreamEvent.AssistantDelta(request.requestId, request.conversationId, "流式回答"))
        delay(50)
        history.emitMessages(request.conversationId, emptyList())
        delay(50)
        assertEquals(listOf("第一条", "流式回答"), viewModel.uiState.value.messages.map { it.content })
        assertEquals(MessageStatus.STREAMING, viewModel.uiState.value.messages.last().status)
    }

    @Test
    fun `Room replay merges completed messages without duplicates and keeps chronological history`() = runBlocking {
        val repository = FakeConversationRepository()
        val history = ControllableHistoryRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default, history)
        delay(50)
        val conversationId = viewModel.uiState.value.activeSessionId

        repeat(3) { index ->
            viewModel.updateInput("问题${index + 1}")
            viewModel.sendMessage()
            delay(30)
            val request = repository.sent.last()
            repository.emit(ConversationStreamEvent.AssistantDelta(request.requestId, conversationId, "回答${index + 1}"))
            repository.emit(ConversationStreamEvent.AssistantCompleted(request.requestId, conversationId))
            delay(30)
        }
        val beforeReplay = viewModel.uiState.value.messages
        assertEquals(6, beforeReplay.size)
        history.emitMessages(conversationId, beforeReplay)
        delay(50)

        assertEquals(6, viewModel.uiState.value.messages.size)
        assertEquals(
            listOf("问题1", "回答1", "问题2", "回答2", "问题3", "回答3"),
            viewModel.uiState.value.messages.map { it.content },
        )
    }

    @Test
    fun `late delta from a background conversation cannot overwrite selected conversation`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("A 的问题")
        viewModel.sendMessage()
        delay(50)
        val requestA = repository.sent.single()
        repository.emit(ConversationStreamEvent.AssistantDelta(requestA.requestId, requestA.conversationId, "A 的开头"))
        delay(30)

        viewModel.startNewConversation()
        viewModel.updateInput("B 的问题")
        viewModel.sendMessage()
        delay(50)
        repository.emit(ConversationStreamEvent.AssistantDelta(requestA.requestId, requestA.conversationId, "不应显示在 B"))
        delay(50)

        assertEquals(listOf("B 的问题"), viewModel.uiState.value.messages.map { it.content })
        viewModel.selectConversation(requestA.conversationId)
        delay(30)
        assertEquals(listOf("A 的问题", "A 的开头不应显示在 B"), viewModel.uiState.value.messages.map { it.content })
    }

    @Test
    fun `reconnect and stale Room emissions retain local complete history`() = runBlocking {
        val repository = FakeConversationRepository()
        val history = ControllableHistoryRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default, history)
        delay(50)
        viewModel.updateInput("断线前的问题")
        viewModel.sendMessage()
        delay(30)
        val request = repository.sent.single()
        repository.emit(ConversationStreamEvent.AssistantDelta(request.requestId, request.conversationId, "断线前的回答"))
        repository.emit(ConversationStreamEvent.AssistantCompleted(request.requestId, request.conversationId))
        delay(30)

        repository.connectionState.value = ConnectionState.DISCONNECTED
        repository.connectionState.value = ConnectionState.CONNECTED
        history.emitSessions(emptyList())
        history.emitMessages(request.conversationId, emptyList())
        delay(50)

        assertEquals(listOf("断线前的问题", "断线前的回答"), viewModel.uiState.value.messages.map { it.content })
    }

    @Test
    fun `deltas append to one assistant message and completion unlocks input`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("你好")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()

        repository.emit(
            ConversationStreamEvent.AssistantDelta(
                request.requestId,
                request.conversationId,
                "你",
            ),
        )
        repository.emit(
            ConversationStreamEvent.AssistantDelta(
                request.requestId,
                request.conversationId,
                "好",
            ),
        )
        delay(50)

        assertEquals("你好", viewModel.uiState.value.messages.last().content)
        assertEquals(
            1,
            viewModel.uiState.value.messages.count { it.author == MessageAuthor.AGENT },
        )
        assertTrue(viewModel.uiState.value.isAssistantResponding)

        repository.emit(
            ConversationStreamEvent.AssistantCompleted(
                request.requestId,
                request.conversationId,
            ),
        )
        delay(50)
        assertFalse(viewModel.uiState.value.isAssistantResponding)
    }

    @Test
    fun `error stops generation and exposes retry`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("测试错误")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()

        repository.emit(
            ConversationStreamEvent.Error(
                requestId = request.requestId,
                conversationId = request.conversationId,
                code = "MODEL_ERROR",
                message = "模型暂时不可用",
                retryable = true,
            ),
        )
        delay(50)

        assertFalse(viewModel.uiState.value.isAssistantResponding)
        assertEquals("模型暂时不可用", viewModel.uiState.value.error?.message)
        assertTrue(viewModel.uiState.value.error?.retryable == true)
    }

    @Test
    fun `generation prevents duplicate sends`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("第一条")
        viewModel.sendMessage()
        viewModel.updateInput("第二条")
        viewModel.sendMessage()
        delay(50)

        assertEquals(1, repository.sent.size)
    }

    @Test
    fun `new conversation creates a new conversation id`() {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        val previousId = viewModel.uiState.value.activeSessionId

        viewModel.startNewConversation()

        assertNotEquals(previousId, viewModel.uiState.value.activeSessionId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.historyItems.isEmpty())
        assertEquals("新对话", viewModel.uiState.value.activeSession?.title)
    }

    @Test
    fun `first message promotes new conversation into history with local title`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        val input = "  帮我规划周末去上海迪士尼的路线和停车建议  "
        viewModel.updateInput(input)
        viewModel.sendMessage()
        delay(50)

        val history = viewModel.uiState.value.historyItems.single()
        assertEquals("帮我规划周末去上海迪士尼的路线和停车…", history.title)
        assertEquals(input.trim(), history.summary)
    }

    @Test
    fun `selecting a history conversation restores its messages`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("第一段对话")
        viewModel.sendMessage()
        delay(50)
        val firstId = repository.sent.single().conversationId
        repository.emit(ConversationStreamEvent.AssistantCompleted(repository.sent.single().requestId, firstId))
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("第二段对话")
        viewModel.sendMessage()
        delay(50)

        viewModel.selectConversation(firstId)

        assertEquals(firstId, viewModel.uiState.value.activeSessionId)
        assertEquals("第一段对话", viewModel.uiState.value.messages.single().content)
    }

    @Test
    fun `deleting a non active conversation keeps the current conversation`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("保留的对话")
        viewModel.sendMessage()
        delay(50)
        val retainedId = repository.sent.single().conversationId
        repository.emit(ConversationStreamEvent.AssistantCompleted(repository.sent.single().requestId, retainedId))
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("待删除的对话")
        viewModel.sendMessage()
        delay(50)
        val deletedId = repository.sent.last().conversationId
        repository.emit(ConversationStreamEvent.AssistantCompleted(repository.sent.last().requestId, deletedId))
        delay(50)
        viewModel.selectConversation(retainedId)

        viewModel.requestDeleteConversation(deletedId)
        viewModel.confirmDeleteConversation()

        assertEquals(retainedId, viewModel.uiState.value.activeSessionId)
        assertTrue(viewModel.uiState.value.sessions.none { it.id == deletedId })
    }

    @Test
    fun `deleting current conversation switches to most recent remaining conversation`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("较早对话")
        viewModel.sendMessage()
        delay(50)
        val firstId = repository.sent.single().conversationId
        repository.emit(ConversationStreamEvent.AssistantCompleted(repository.sent.single().requestId, firstId))
        delay(50)
        viewModel.startNewConversation()
        viewModel.updateInput("较新对话")
        viewModel.sendMessage()
        delay(50)
        val activeId = repository.sent.last().conversationId
        repository.emit(ConversationStreamEvent.AssistantCompleted(repository.sent.last().requestId, activeId))
        delay(50)

        viewModel.requestDeleteConversation(activeId)
        viewModel.confirmDeleteConversation()

        assertEquals(firstId, viewModel.uiState.value.activeSessionId)
        assertTrue(viewModel.uiState.value.sessions.none { it.id == activeId })
    }

    @Test
    fun `cancel deletion leaves a history conversation unchanged`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("保留记录")
        viewModel.sendMessage()
        delay(50)
        val conversationId = repository.sent.single().conversationId

        viewModel.requestDeleteConversation(conversationId)
        assertEquals(conversationId, viewModel.uiState.value.deleteTargetConversationId)
        viewModel.cancelDeleteConversation()

        assertNull(viewModel.uiState.value.deleteTargetConversationId)
        assertTrue(viewModel.uiState.value.sessions.any { it.id == conversationId })
    }

    @Test
    fun `late delta for a deleted conversation is ignored`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("生成中的会话")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()

        viewModel.requestDeleteConversation(request.conversationId)
        viewModel.confirmDeleteConversation()
        repository.emit(
            ConversationStreamEvent.AssistantDelta(
                request.requestId,
                request.conversationId,
                "不应显示的延迟分片",
            ),
        )
        delay(50)

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.sessions.none { it.id == request.conversationId })
    }

    @Test
    fun `knowledge navigation remains local`() {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)

        viewModel.openKnowledgeBase()
        assertEquals(WorkspaceDestination.KNOWLEDGE_BASE, viewModel.uiState.value.destination)
        viewModel.returnToConversation()
        assertEquals(WorkspaceDestination.CONVERSATION, viewModel.uiState.value.destination)
        assertNotNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun `confirmation event is sent only after user confirms`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        val conversationId = viewModel.uiState.value.activeSessionId
        repository.emit(
            ConversationStreamEvent.ControlConfirmationRequired(
                "request-1",
                conversationId,
                "confirmation-1",
                "打开空调",
                System.currentTimeMillis() + 60_000,
            ),
        )
        delay(50)

        assertEquals("模拟座舱确认", viewModel.uiState.value.pendingVehicleAction?.title)
        assertTrue(repository.confirmations.isEmpty())
        viewModel.confirmVehicleAction()
        delay(50)

        assertTrue(repository.confirmations.single().confirmed)
        assertEquals("confirmation-1", repository.confirmations.single().confirmationId)
        assertNull(viewModel.uiState.value.pendingVehicleAction)
    }

    @Test
    fun `cancelling confirmation sends negative response`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        val conversationId = viewModel.uiState.value.activeSessionId
        repository.emit(
            ConversationStreamEvent.ControlConfirmationRequired(
                "request-1",
                conversationId,
                "confirmation-1",
                "打开空调",
                System.currentTimeMillis() + 60_000,
            ),
        )
        delay(50)

        viewModel.cancelVehicleAction()
        delay(50)

        assertFalse(repository.confirmations.single().confirmed)
    }

    @Test
    fun `cockpit state update is isolated by conversation`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        val firstConversation = viewModel.uiState.value.activeSessionId
        repository.emit(
            ConversationStreamEvent.SimulatedCockpitState(
                "request-1",
                firstConversation,
                SimulatedCockpitStatePayload(acEnabled = true, targetTemperature = 24f),
            ),
        )
        delay(50)
        assertTrue(viewModel.uiState.value.simulatedCockpitState?.acEnabled == true)

        viewModel.startNewConversation()

        assertNull(viewModel.uiState.value.simulatedCockpitState)
    }

    @Test
    fun `confirmation error clears pending action`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        val conversationId = viewModel.uiState.value.activeSessionId
        repository.emit(
            ConversationStreamEvent.ControlConfirmationRequired(
                "request-1",
                conversationId,
                "confirmation-1",
                "打开空调",
                System.currentTimeMillis() + 60_000,
            ),
        )
        repository.emit(
            ConversationStreamEvent.Error(
                "request-1",
                conversationId,
                "INVALID_CONFIRMATION",
                "确认请求无效",
                false,
            ),
        )
        delay(50)

        assertNull(viewModel.uiState.value.pendingVehicleAction)
        assertEquals("确认请求无效", viewModel.uiState.value.error?.message)
    }

    @Test
    fun `search progress and sources bind to the streamed assistant message`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("最近有什么 AI 新闻")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()

        repository.emit(
            ConversationStreamEvent.ToolProgress(
                request.requestId,
                request.conversationId,
                "search-call",
                "tavily_web_search",
                ToolCallStatus.RUNNING,
                "正在联网搜索",
            ),
        )
        repository.emit(
            ConversationStreamEvent.WebSearchSources(
                request.requestId,
                request.conversationId,
                request.requestId,
                "AI 新闻",
                listOf(
                    WebSearchSourcePayload(
                        1,
                        "来源标题",
                        "https://example.com/news",
                        "example.com",
                        "来源摘要",
                    ),
                ),
            ),
        )
        repository.emit(
            ConversationStreamEvent.AssistantDelta(
                request.requestId,
                request.conversationId,
                "带来源回答[1]",
            ),
        )
        delay(50)

        assertEquals("联网搜索", viewModel.uiState.value.toolExecution?.title)
        assertEquals("正在联网搜索", viewModel.uiState.value.toolExecution?.detail)
        val assistant = viewModel.uiState.value.messages.last()
        assertEquals(request.requestId, assistant.serverMessageId)
        assertEquals("AI 新闻", assistant.searchQuery)
        assertEquals("来源标题", assistant.sources.single().title)
    }

    @Test
    fun `search failure remains one assistant message and exposes failed status`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("扬州今天会下雨吗")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()

        repository.emit(
            ConversationStreamEvent.ToolProgress(
                request.requestId,
                request.conversationId,
                "search-call",
                "tavily_web_search",
                ToolCallStatus.FAILED,
                "联网搜索超时，已切换为普通回答",
            ),
        )
        repository.emit(
            ConversationStreamEvent.AssistantDelta(
                request.requestId,
                request.conversationId,
                "联网搜索暂时不可用。普通回答",
            ),
        )
        delay(50)

        assertEquals(ToolExecutionState.FAILED, viewModel.uiState.value.toolExecution?.state)
        assertEquals(
            1,
            viewModel.uiState.value.messages.count { it.author == MessageAuthor.AGENT },
        )
        assertTrue(viewModel.uiState.value.messages.last().sources.isEmpty())
    }

    @Test
    fun `knowledge sources arriving before delta bind once to that request`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("胎压灯亮了怎么办")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()
        val event = ConversationStreamEvent.KnowledgeSources(
            request.requestId, request.conversationId, request.requestId, "manual", "胎压",
            listOf(KnowledgeSourcePayload(1, "doc-1", "手册", snippet = "胎压资料")),
        )

        repository.emit(event)
        repository.emit(event) // WebSocket retries must not duplicate the card's sources.
        repository.emit(ConversationStreamEvent.AssistantDelta(request.requestId, request.conversationId, "请检查胎压[1]"))
        delay(50)

        val assistant = viewModel.uiState.value.messages.last()
        assertEquals(listOf(1), assistant.knowledgeSources.map { it.index })
        assertEquals("manual", assistant.knowledgeBaseId)
        repository.emit(ConversationStreamEvent.AssistantCompleted(request.requestId, request.conversationId))
        delay(50)
        assertTrue(viewModel.uiState.value.pendingKnowledgeSources.isEmpty())
    }

    @Test
    fun `disconnect clears pending search and tool state`() = runBlocking {
        val repository = FakeConversationRepository()
        val viewModel = ConversationViewModel(repository, Dispatchers.Default)
        delay(50)
        viewModel.updateInput("搜索新闻")
        viewModel.sendMessage()
        delay(50)
        val request = repository.sent.single()
        repository.emit(
            ConversationStreamEvent.ToolProgress(
                request.requestId,
                request.conversationId,
                "search-call",
                "tavily_web_search",
                ToolCallStatus.RUNNING,
                "正在联网搜索",
            ),
        )
        repository.emit(
            ConversationStreamEvent.WebSearchSources(
                request.requestId,
                request.conversationId,
                request.requestId,
                "query",
                listOf(
                    WebSearchSourcePayload(
                        1,
                        "source",
                        "https://example.com",
                        "example.com",
                        "snippet",
                    ),
                ),
            ),
        )
        delay(50)
        assertTrue(viewModel.uiState.value.pendingSearchSources.isNotEmpty())

        repository.connectionState.value = ConnectionState.DISCONNECTED
        delay(50)

        assertTrue(viewModel.uiState.value.pendingSearchSources.isEmpty())
        assertNull(viewModel.uiState.value.toolExecution)
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
    data class Confirmation(
        val conversationId: String,
        val requestId: String,
        val confirmationId: String,
        val confirmed: Boolean,
    )
    val confirmations = mutableListOf<Confirmation>()

    override fun connect() = Unit

    override suspend fun sendMessage(
        conversationId: String,
        requestId: String,
        message: String,
    ): Result<Unit> {
        sent += SentMessage(conversationId, requestId, message)
        return Result.success(Unit)
    }

    override suspend fun sendControlConfirmation(
        conversationId: String,
        requestId: String,
        confirmationId: String,
        confirmed: Boolean,
    ): Result<Unit> {
        confirmations += Confirmation(conversationId, requestId, confirmationId, confirmed)
        return Result.success(Unit)
    }

    override suspend fun syncConversation(
        conversationId: String,
        messages: List<com.carvoice.cockpit.data.ConversationSyncMessage>,
    ): Result<Unit> = Result.success(Unit)

    suspend fun emit(event: ConversationStreamEvent) {
        eventFlow.emit(event)
    }

    override fun close() = Unit
}

private class ControllableHistoryRepository : ConversationHistoryRepository {
    private val sessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    private val messages = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    override fun observeSessions() = sessions.asStateFlow()
    override fun observeMessages(conversationId: String) =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.asStateFlow()

    fun emitSessions(value: List<ConversationSession>) { sessions.value = value }
    fun emitMessages(conversationId: String, value: List<ChatMessage>) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = value
    }

    override suspend fun saveUserMessage(session: ConversationSession, message: ChatMessage) = Unit
    override suspend fun saveCompletedAssistant(
        conversationId: String,
        message: ChatMessage,
        sources: com.carvoice.cockpit.model.WebSearchSourcesUi?,
        knowledgeSources: com.carvoice.cockpit.model.KnowledgeSourcesUi?,
    ) = Unit
    override suspend fun markAssistantFailed(conversationId: String, message: ChatMessage) = Unit
    override suspend fun deleteConversation(conversationId: String) = Unit
    override suspend fun recentCompletedMessages(conversationId: String) = emptyList<ChatMessage>()
}
