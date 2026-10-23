package com.carvoice.cockpit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, WebSearchSourceEntity::class, KnowledgeSourceEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(CarVoiceRoomConverters::class)
abstract class CarVoiceDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun webSearchSourceDao(): WebSearchSourceDao
    abstract fun knowledgeSourceDao(): KnowledgeSourceDao

    companion object {
        fun create(context: Context): CarVoiceDatabase = Room.databaseBuilder(
            context.applicationContext,
            CarVoiceDatabase::class.java,
            "carvoice.db",
        ).addMigrations(MIGRATION_1_2).build()
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_sources (sourceId TEXT NOT NULL, messageId TEXT NOT NULL, sourceIndex INTEGER NOT NULL, knowledgeBaseId TEXT NOT NULL, documentId TEXT NOT NULL, documentName TEXT NOT NULL, pageNumber INTEGER, sectionTitle TEXT, snippet TEXT NOT NULL, PRIMARY KEY(sourceId), FOREIGN KEY(messageId) REFERENCES messages(messageId) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_sources_messageId ON knowledge_sources(messageId)")
            }
        }
    }
}
