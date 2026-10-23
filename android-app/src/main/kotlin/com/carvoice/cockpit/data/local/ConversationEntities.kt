package com.carvoice.cockpit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.carvoice.cockpit.model.MessageStatus
import com.carvoice.cockpit.model.MessageAuthor

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["conversationId"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val role: MessageAuthor,
    val content: String,
    val status: MessageStatus,
    val createdAt: Long,
    /** Query metadata, not a web page body. */
    val searchQuery: String? = null,
)

@Entity(
    tableName = "web_search_sources",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["messageId"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("messageId")],
)
data class WebSearchSourceEntity(
    @PrimaryKey val sourceId: String,
    val messageId: String,
    val sourceIndex: Int,
    val title: String,
    val url: String,
    val domain: String?,
    val snippet: String?,
    val publishedAt: String?,
)

@Entity(
    tableName = "knowledge_sources",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["messageId"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId")],
)
data class KnowledgeSourceEntity(
    @PrimaryKey val sourceId: String,
    val messageId: String,
    val sourceIndex: Int,
    val knowledgeBaseId: String,
    val documentId: String,
    val documentName: String,
    val pageNumber: Int?,
    val sectionTitle: String?,
    val snippet: String,
)

class CarVoiceRoomConverters {
    @TypeConverter fun authorToString(value: MessageAuthor): String = value.name
    @TypeConverter fun stringToAuthor(value: String): MessageAuthor = MessageAuthor.valueOf(value)
    @TypeConverter fun statusToString(value: MessageStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
