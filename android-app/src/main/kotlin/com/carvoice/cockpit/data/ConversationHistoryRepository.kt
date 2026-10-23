package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.WebSearchSourcesUi
import com.carvoice.cockpit.model.KnowledgeSourcesUi
import kotlinx.coroutines.flow.Flow

/**
 * 本地历史当前由 ViewModel 提供；后续可在此接入 Room、DataStore 或服务端会话记录。
 */
interface ConversationHistoryRepository {
    fun observeSessions(): Flow<List<ConversationSession>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    suspend fun saveUserMessage(session: ConversationSession, message: ChatMessage)
    suspend fun saveCompletedAssistant(
        conversationId: String,
        message: ChatMessage,
        sources: WebSearchSourcesUi?,
        knowledgeSources: KnowledgeSourcesUi? = null,
    )
    suspend fun markAssistantFailed(conversationId: String, message: ChatMessage)
    suspend fun deleteConversation(conversationId: String)
    suspend fun recentCompletedMessages(conversationId: String): List<ChatMessage>
}
