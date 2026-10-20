package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.KnowledgeBaseItem
import kotlinx.coroutines.flow.Flow

/**
 * 后续可由 Room、DataStore 或服务端实现；本阶段知识条目只来自本地 UI 状态。
 */
interface KnowledgeBaseRepository {
    fun observeKnowledgeBases(): Flow<List<KnowledgeBaseItem>>

    suspend fun selectKnowledgeBase(id: String)
}
