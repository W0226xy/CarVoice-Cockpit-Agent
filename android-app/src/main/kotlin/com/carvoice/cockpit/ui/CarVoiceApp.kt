package com.carvoice.cockpit.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.ui.conversation.ConversationScreen
import com.carvoice.cockpit.ui.conversation.ConversationUiState
import com.carvoice.cockpit.ui.conversation.ConversationViewModel
import com.carvoice.cockpit.ui.knowledge.KnowledgeBaseScreen
import com.carvoice.cockpit.ui.sidebar.ChatSidebar
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import kotlinx.coroutines.launch

@Composable
fun CarVoiceApp(
    modifier: Modifier = Modifier,
    conversationViewModel: ConversationViewModel = viewModel(),
) {
    val conversationState by conversationViewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val contentModifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)

        BoxWithConstraints(modifier = contentModifier) {
            val showPermanentSidebar = maxWidth >= 900.dp
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            val closeDrawer = {
                if (!showPermanentSidebar) {
                    scope.launch { drawerState.close() }
                }
                Unit
            }
            val startNewConversation = {
                conversationViewModel.startNewConversation()
                closeDrawer()
            }
            val selectConversation: (String) -> Unit = { sessionId ->
                conversationViewModel.selectConversation(sessionId)
                closeDrawer()
            }
            val openKnowledgeBase = {
                conversationViewModel.openKnowledgeBase()
                closeDrawer()
            }

            if (showPermanentSidebar) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatSidebar(
                        historyItems = conversationState.historyItems,
                        activeSessionId = conversationState.activeSessionId,
                        destination = conversationState.destination,
                        onNewConversation = startNewConversation,
                        onSelectConversation = selectConversation,
                        onOpenKnowledgeBase = openKnowledgeBase,
                        modifier = Modifier.width(312.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        WorkspaceContent(
                            state = conversationState,
                            viewModel = conversationViewModel,
                            onOpenSidebar = null,
                        )
                    }
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(320.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            ChatSidebar(
                                historyItems = conversationState.historyItems,
                                activeSessionId = conversationState.activeSessionId,
                                destination = conversationState.destination,
                                onNewConversation = startNewConversation,
                                onSelectConversation = selectConversation,
                                onOpenKnowledgeBase = openKnowledgeBase,
                            )
                        }
                    },
                ) {
                    WorkspaceContent(
                        state = conversationState,
                        viewModel = conversationViewModel,
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceContent(
    state: ConversationUiState,
    viewModel: ConversationViewModel,
    onOpenSidebar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (state.destination) {
        WorkspaceDestination.CONVERSATION -> ConversationScreen(
            state = state,
            onOpenSidebar = onOpenSidebar,
            onSuggestedPrompt = viewModel::prepareQuickAction,
            onInputChange = viewModel::updateInput,
            onSend = viewModel::sendMessage,
            onCancelVehicleAction = viewModel::cancelVehicleAction,
            onConfirmVehicleAction = viewModel::confirmVehicleAction,
            modifier = modifier.fillMaxSize(),
        )

        WorkspaceDestination.KNOWLEDGE_BASE -> KnowledgeBaseScreen(
            items = state.knowledgeBases,
            onBackToConversation = viewModel::returnToConversation,
            onOpenSidebar = onOpenSidebar,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "车机应用", widthDp = 1280, heightDp = 720)
@Composable
private fun CarVoiceAppPreview() {
    CarVoiceCockpitTheme {
        val previewViewModel = remember { ConversationViewModel() }
        CarVoiceApp(conversationViewModel = previewViewModel)
    }
}
