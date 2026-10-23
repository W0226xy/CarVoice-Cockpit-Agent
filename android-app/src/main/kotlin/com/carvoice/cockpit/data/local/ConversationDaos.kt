package com.carvoice.cockpit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.carvoice.cockpit.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, conversationId ASC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt, title = :title WHERE conversationId = :conversationId")
    suspend fun update(conversationId: String, title: String, updatedAt: Long)

    @Query("SELECT title FROM conversations WHERE conversationId = :conversationId")
    suspend fun titleFor(conversationId: String): String?

    @Query("DELETE FROM conversations WHERE conversationId = :conversationId")
    suspend fun delete(conversationId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, messageId ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, searchQuery = :searchQuery WHERE messageId = :messageId")
    suspend fun updateAssistant(messageId: String, content: String, status: MessageStatus, searchQuery: String?)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'COMPLETED' ORDER BY createdAt DESC, messageId DESC LIMIT 20")
    suspend fun recentCompleted(conversationId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun delete(messageId: String)
}

@Dao
interface WebSearchSourceDao {
    @Query("SELECT * FROM web_search_sources WHERE messageId = :messageId ORDER BY sourceIndex ASC, sourceId ASC")
    suspend fun forMessage(messageId: String): List<WebSearchSourceEntity>

    @Query("SELECT * FROM web_search_sources WHERE messageId IN (:messageIds) ORDER BY sourceIndex ASC, sourceId ASC")
    suspend fun forMessages(messageIds: List<String>): List<WebSearchSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<WebSearchSourceEntity>)

}

@Dao
interface KnowledgeSourceDao {
    @Query("SELECT * FROM knowledge_sources WHERE messageId = :messageId ORDER BY sourceIndex ASC, sourceId ASC")
    suspend fun forMessage(messageId: String): List<KnowledgeSourceEntity>
    @Query("SELECT * FROM knowledge_sources WHERE messageId IN (:messageIds) ORDER BY sourceIndex ASC, sourceId ASC")
    suspend fun forMessages(messageIds: List<String>): List<KnowledgeSourceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<KnowledgeSourceEntity>)
}
