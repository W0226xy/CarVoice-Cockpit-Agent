package com.carvoice.cockpit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, WebSearchSourceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CarVoiceRoomConverters::class)
abstract class CarVoiceDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun webSearchSourceDao(): WebSearchSourceDao

    companion object {
        fun create(context: Context): CarVoiceDatabase = Room.databaseBuilder(
            context.applicationContext,
            CarVoiceDatabase::class.java,
            "carvoice.db",
        ).build()
    }
}
