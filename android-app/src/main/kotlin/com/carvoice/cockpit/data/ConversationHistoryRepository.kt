package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConversationSession
import kotlinx.coroutines.flow.Flow

/**
 * 本地历史当前由 ViewModel 提供；后续可在此接入 Room、DataStore 或服务端会话记录。
 */
interface ConversationHistoryRepository {
    fun observeSessions(): Flow<List<ConversationSession>>

    suspend fun saveSession(session: ConversationSession)
}
