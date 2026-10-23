package com.carvoice.cockpit.data.local

import androidx.room.withTransaction
import com.carvoice.cockpit.data.ConversationHistoryRepository
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.MessageStatus
import com.carvoice.cockpit.model.WebSearchSourceUi
import com.carvoice.cockpit.model.WebSearchSourcesUi
import com.carvoice.cockpit.model.KnowledgeSourcesUi
import com.carvoice.cockpit.model.KnowledgeSourceUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomConversationHistoryRepository(
    private val database: CarVoiceDatabase,
) : ConversationHistoryRepository {
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()
    private val sources = database.webSearchSourceDao()
    private val knowledgeSources = database.knowledgeSourceDao()

    override fun observeSessions(): Flow<List<ConversationSession>> = conversations.observeAll()
        .map { entities -> entities.map(ConversationEntity::toDomain) }
        .flowOn(Dispatchers.IO)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messages.observeForConversation(conversationId).map { entities ->
            val sourceByMessage = sources.forMessages(entities.map(MessageEntity::messageId))
                .groupBy(WebSearchSourceEntity::messageId)
            val knowledgeByMessage = knowledgeSources.forMessages(entities.map(MessageEntity::messageId)).groupBy(KnowledgeSourceEntity::messageId)
            entities.map { it.toDomain(sourceByMessage[it.messageId].orEmpty(), knowledgeByMessage[it.messageId].orEmpty()) }
        }.flowOn(Dispatchers.IO)

    override suspend fun saveUserMessage(session: ConversationSession, message: ChatMessage) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                conversations.upsert(session.toEntity())
                messages.insert(message.toEntity(session.id))
            }
        }

    override suspend fun saveCompletedAssistant(
        conversationId: String,
        message: ChatMessage,
        sources: WebSearchSourcesUi?,
        knowledgeSources: KnowledgeSourcesUi?,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            messages.insert(message.toEntity(conversationId).copy(status = MessageStatus.COMPLETED))
            val sourceEntities = sources?.sources.orEmpty().map { source ->
                source.toEntity(message.id)
            }
            if (sourceEntities.isNotEmpty()) this@RoomConversationHistoryRepository.sources.insertAll(sourceEntities)
            val knowledgeEntities = knowledgeSources?.let { payload -> payload.sources.map { it.toEntity(message.id, payload.knowledgeBaseId) } }.orEmpty()
            if (knowledgeEntities.isNotEmpty()) this@RoomConversationHistoryRepository.knowledgeSources.insertAll(knowledgeEntities)
            val existing = messages.recentCompleted(conversationId) // validates that the conversation exists before touch
            if (existing.isNotEmpty()) {
                val title = conversations.titleFor(conversationId) ?: "新对话"
                conversations.update(conversationId, title, message.createdAt)
            }
        }
    }

    override suspend fun markAssistantFailed(conversationId: String, message: ChatMessage) =
        withContext(Dispatchers.IO) {
            messages.insert(message.toEntity(conversationId).copy(status = MessageStatus.FAILED))
        }

    override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        conversations.delete(conversationId)
    }

    override suspend fun recentCompletedMessages(conversationId: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            messages.recentCompleted(conversationId).asReversed().map { it.toDomain(emptyList(), knowledgeSources.forMessage(it.messageId)) }
        }

}

private fun ConversationEntity.toDomain() = ConversationSession(
    id = conversationId,
    title = title,
    updatedAtLabel = "",
    messages = emptyList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    persisted = true,
)

private fun ConversationSession.toEntity() = ConversationEntity(id, title, createdAt, updatedAt)

private fun ChatMessage.toEntity(conversationId: String) = MessageEntity(
    messageId = id,
    conversationId = conversationId,
    role = author,
    content = content,
    status = status,
    createdAt = createdAt,
    searchQuery = searchQuery,
)

private fun MessageEntity.toDomain(sources: List<WebSearchSourceEntity>, knowledgeSources: List<KnowledgeSourceEntity> = emptyList()) = ChatMessage(
    id = messageId,
    author = role,
    content = content,
    searchQuery = searchQuery,
    sources = sources.map(WebSearchSourceEntity::toDomain),
    knowledgeSources = knowledgeSources.map(KnowledgeSourceEntity::toDomain),
    status = status,
    createdAt = createdAt,
)
private fun KnowledgeSourceEntity.toDomain() = KnowledgeSourceUi(sourceIndex, documentId, documentName, pageNumber, sectionTitle, snippet)
private fun KnowledgeSourceUi.toEntity(messageId: String, baseId: String) = KnowledgeSourceEntity("$messageId:$index:$documentId", messageId, index, baseId, documentId, documentName, pageNumber, sectionTitle, snippet)

private fun WebSearchSourceEntity.toDomain() = WebSearchSourceUi(
    sourceIndex, title, url, domain.orEmpty(), snippet.orEmpty(), publishedAt,
)

private fun WebSearchSourceUi.toEntity(messageId: String) = WebSearchSourceEntity(
    sourceId = "$messageId:$index:${url.hashCode()}",
    messageId = messageId,
    sourceIndex = index,
    title = title,
    url = url,
    domain = domain.ifBlank { null },
    snippet = snippet.ifBlank { null },
    publishedAt = publishedAt,
)
