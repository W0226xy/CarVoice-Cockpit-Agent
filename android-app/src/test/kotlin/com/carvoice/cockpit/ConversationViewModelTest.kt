package com.carvoice.cockpit

import com.carvoice.cockpit.model.QuickActionType
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.model.dashboardQuickActions
import com.carvoice.cockpit.ui.conversation.ConversationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationViewModelTest {
    @Test
    fun sendMessageAppendsUserMessageAndLocalReply() = runBlocking {
        val viewModel = ConversationViewModel()
        viewModel.startNewConversation()

        viewModel.updateInput("你好，座舱")
        viewModel.sendMessage()

        assertEquals("你好，座舱", viewModel.uiState.value.messages.single().content)
        assertTrue(viewModel.uiState.value.isAssistantResponding)

        delay(900)

        val state = viewModel.uiState.value
        assertEquals(
            "当前为界面演示模式，Agent 服务将在下一阶段接入。",
            state.messages.last().content,
        )
        assertEquals("", state.inputText)
        assertFalse(state.isAssistantResponding)
    }

    @Test
    fun airConditionerActionRequiresLocalConfirmation() {
        val viewModel = ConversationViewModel()
        viewModel.startNewConversation()
        val action = dashboardQuickActions.first {
            it.type == QuickActionType.AIR_CONDITIONER
        }

        viewModel.prepareQuickAction(action)

        assertEquals(action.demoPrompt, viewModel.uiState.value.inputText)
        assertEquals("将空调设置为 24 ℃", viewModel.uiState.value.pendingVehicleAction?.description)

        viewModel.confirmVehicleAction()

        val state = viewModel.uiState.value
        assertNull(state.pendingVehicleAction)
        assertTrue(state.messages.last().content.contains("未执行车辆操作"))
    }

    @Test
    fun newConversationCreatesAndSelectsEmptySession() {
        val viewModel = ConversationViewModel()
        val previousSessionCount = viewModel.uiState.value.sessions.size
        viewModel.updateInput("临时内容")
        viewModel.prepareQuickAction(
            dashboardQuickActions.first { it.type == QuickActionType.AIR_CONDITIONER },
        )

        viewModel.startNewConversation()

        val state = viewModel.uiState.value
        assertEquals(previousSessionCount + 1, state.sessions.size)
        assertEquals("新会话", state.activeSession?.title)
        assertTrue(state.messages.isEmpty())
        assertEquals("", state.inputText)
        assertNull(state.pendingVehicleAction)
        assertFalse(state.isAssistantResponding)
    }

    @Test
    fun selectingHistoryChangesActiveConversation() {
        val viewModel = ConversationViewModel()
        val targetSession = viewModel.uiState.value.sessions[1]

        viewModel.selectConversation(targetSession.id)

        val state = viewModel.uiState.value
        assertEquals(targetSession.id, state.activeSessionId)
        assertEquals(targetSession.messages, state.messages)
        assertEquals(WorkspaceDestination.CONVERSATION, state.destination)
    }

    @Test
    fun knowledgeBaseCanOpenAndReturnToConversation() {
        val viewModel = ConversationViewModel()

        viewModel.openKnowledgeBase()
        assertEquals(
            WorkspaceDestination.KNOWLEDGE_BASE,
            viewModel.uiState.value.destination,
        )

        viewModel.returnToConversation()
        assertEquals(
            WorkspaceDestination.CONVERSATION,
            viewModel.uiState.value.destination,
        )
    }
}
