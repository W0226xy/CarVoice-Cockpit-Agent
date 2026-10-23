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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.carvoice.cockpit.BuildConfig
import com.carvoice.cockpit.data.AgentConnection
import com.carvoice.cockpit.data.AgentConnectionConfig
import com.carvoice.cockpit.data.WebSocketConversationRepository
import com.carvoice.cockpit.data.HttpKnowledgeBaseRepository
import com.carvoice.cockpit.data.UploadDocument
import com.carvoice.cockpit.data.local.CarVoiceDatabase
import com.carvoice.cockpit.data.local.RoomConversationHistoryRepository
import com.carvoice.cockpit.model.WorkspaceDestination
import com.carvoice.cockpit.ui.conversation.ConversationScreen
import com.carvoice.cockpit.ui.conversation.ConversationUiState
import com.carvoice.cockpit.ui.conversation.ConversationViewModel
import com.carvoice.cockpit.ui.conversation.ConversationViewModelFactory
import com.carvoice.cockpit.ui.knowledge.KnowledgeBaseScreen
import com.carvoice.cockpit.ui.knowledge.KnowledgeBaseViewModel
import com.carvoice.cockpit.ui.knowledge.KnowledgeBaseViewModelFactory
import com.carvoice.cockpit.ui.sidebar.ChatSidebar
import com.carvoice.cockpit.ui.sidebar.DeleteConversationDialog
import kotlinx.coroutines.launch

@Composable
fun CarVoiceApp(
    modifier: Modifier = Modifier,
    conversationViewModel: ConversationViewModel? = null,
    externalLinkOpener: ExternalLinkOpener? = null,
) {
    val context = LocalContext.current
    val resolvedLinkOpener = remember(externalLinkOpener, context) {
        externalLinkOpener ?: AndroidExternalLinkOpener(context)
    }
    val knowledgeRepository = remember(context) { HttpKnowledgeBaseRepository(context.applicationContext, BuildConfig.AGENT_HTTP_BASE_URL) }
    val defaultFactory = remember(context) {
        ConversationViewModelFactory(
            WebSocketConversationRepository(
                AgentConnection(AgentConnectionConfig(BuildConfig.CHAT_WS_URL)),
            ),
            RoomConversationHistoryRepository(CarVoiceDatabase.create(context)),
        )
    }
    val resolvedViewModel = conversationViewModel ?: viewModel(factory = defaultFactory)
    val conversationState by resolvedViewModel.uiState.collectAsState()
    val selectedKnowledgeBaseId by knowledgeRepository.observeSelectedKnowledgeBaseId().collectAsState(initial = null)
    LaunchedEffect(selectedKnowledgeBaseId) { resolvedViewModel.updateSelectedKnowledgeBase(selectedKnowledgeBaseId) }
    val knowledgeFactory = remember(knowledgeRepository) { KnowledgeBaseViewModelFactory(knowledgeRepository) }
    val knowledgeViewModel: KnowledgeBaseViewModel = viewModel(factory = knowledgeFactory)
    val knowledgeState by knowledgeViewModel.uiState.collectAsState()
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor -> cursor.moveToFirst(); cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString) } ?: "document"
        val type = resolver.getType(uri) ?: if (name.endsWith(".pdf", true)) "application/pdf" else "text/plain"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0 } }
        knowledgeViewModel.upload(UploadDocument(name, type, size) { requireNotNull(resolver.openInputStream(uri)) })
    }

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
                resolvedViewModel.startNewConversation()
                closeDrawer()
            }
            val selectConversation: (String) -> Unit = { sessionId ->
                resolvedViewModel.selectConversation(sessionId)
                closeDrawer()
            }
            val openKnowledgeBase = {
                resolvedViewModel.openKnowledgeBase()
                closeDrawer()
            }
            val requestDeleteConversation: (String) -> Unit = { sessionId ->
                resolvedViewModel.requestDeleteConversation(sessionId)
            }

            if (showPermanentSidebar) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatSidebar(
                        historyItems = conversationState.historyItems,
                        activeSessionId = conversationState.activeSessionId,
                        destination = conversationState.destination,
                        onNewConversation = startNewConversation,
                        onSelectConversation = selectConversation,
                        onRequestDeleteConversation = requestDeleteConversation,
                        onOpenKnowledgeBase = openKnowledgeBase,
                        modifier = Modifier.width(312.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        WorkspaceContent(
                            state = conversationState,
                            viewModel = resolvedViewModel,
                            onOpenSidebar = null,
                            onOpenSource = resolvedLinkOpener::open,
                            knowledgeState = knowledgeState,
                            knowledgeViewModel = knowledgeViewModel,
                            onPickDocument = { documentPicker.launch(arrayOf("application/pdf", "text/plain")) },
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
                                onRequestDeleteConversation = requestDeleteConversation,
                                onOpenKnowledgeBase = openKnowledgeBase,
                            )
                        }
                    },
                ) {
                    WorkspaceContent(
                        state = conversationState,
                        viewModel = resolvedViewModel,
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                        onOpenSource = resolvedLinkOpener::open,
                        knowledgeState = knowledgeState,
                        knowledgeViewModel = knowledgeViewModel,
                        onPickDocument = { documentPicker.launch(arrayOf("application/pdf", "text/plain")) },
                    )
                }
            }
            if (conversationState.deleteTargetConversationId != null) {
                DeleteConversationDialog(
                    onDismiss = resolvedViewModel::cancelDeleteConversation,
                    onConfirm = resolvedViewModel::confirmDeleteConversation,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceContent(
    state: ConversationUiState,
    viewModel: ConversationViewModel,
    onOpenSidebar: (() -> Unit)?,
    onOpenSource: (String) -> Unit,
    knowledgeState: com.carvoice.cockpit.ui.knowledge.KnowledgeBaseUiState,
    knowledgeViewModel: KnowledgeBaseViewModel,
    onPickDocument: () -> Unit,
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
            onRetry = viewModel::retry,
            onOpenSource = onOpenSource,
            modifier = modifier.fillMaxSize(),
        )

        WorkspaceDestination.KNOWLEDGE_BASE -> KnowledgeBaseScreen(
            state = knowledgeState,
            onBackToConversation = viewModel::returnToConversation,
            onOpenSidebar = onOpenSidebar,
            onRefresh = knowledgeViewModel::refresh,
            onSelect = knowledgeViewModel::select,
            onCreate = knowledgeViewModel::create,
            onUpload = onPickDocument,
            onDelete = knowledgeViewModel::requestDelete,
            onConfirmDelete = knowledgeViewModel::confirmDelete,
            onCancelDelete = knowledgeViewModel::cancelDelete,
            onDismissError = knowledgeViewModel::dismissError,
            modifier = modifier.fillMaxSize(),
        )
    }
}
