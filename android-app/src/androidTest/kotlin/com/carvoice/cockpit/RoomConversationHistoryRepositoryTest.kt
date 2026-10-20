package com.carvoice.cockpit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carvoice.cockpit.data.local.CarVoiceDatabase
import com.carvoice.cockpit.data.local.RoomConversationHistoryRepository
import com.carvoice.cockpit.model.ChatMessage
import com.carvoice.cockpit.model.ConversationSession
import com.carvoice.cockpit.model.MessageAuthor
import com.carvoice.cockpit.model.MessageStatus
import com.carvoice.cockpit.model.WebSearchSourceUi
import com.carvoice.cockpit.model.WebSearchSourcesUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomConversationHistoryRepositoryTest {
    private lateinit var database: CarVoiceDatabase
    private lateinit var repository: RoomConversationHistoryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CarVoiceDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomConversationHistoryRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun createsReadsOrdersAndCascadesConversation() = runBlocking {
        val session = session("c1")
        repository.saveUserMessage(session, message("u2", MessageAuthor.USER, "second", 20))
        repository.saveUserMessage(session, message("u1", MessageAuthor.USER, "first", 10))
        assertEquals("c1", repository.observeSessions().first().single().id)
        assertEquals(listOf("u1", "u2"), repository.observeMessages("c1").first().map { it.id })
        repository.deleteConversation("c1")
        assertTrue(repository.observeMessages("c1").first().isEmpty())
    }

    @Test fun assistantSourcesCascadeAndRecentCompletedFiltersStatuses() = runBlocking {
        val session = session("c2")
        repository.saveUserMessage(session, message("u", MessageAuthor.USER, "question", 1))
        repository.saveCompletedAssistant(
            "c2", message("a", MessageAuthor.AGENT, "answer", 2),
            WebSearchSourcesUi("question", listOf(WebSearchSourceUi(1, "source", "https://example.com", "example.com", "snippet"))),
        )
        database.messageDao().insert(
            com.carvoice.cockpit.data.local.MessageEntity("stream", "c2", MessageAuthor.AGENT, "partial", MessageStatus.STREAMING, 3),
        )
        database.messageDao().insert(
            com.carvoice.cockpit.data.local.MessageEntity("failed", "c2", MessageAuthor.AGENT, "bad", MessageStatus.FAILED, 4),
        )
        assertEquals(listOf("u", "a"), repository.recentCompletedMessages("c2").map { it.id })
        assertEquals(1, repository.observeMessages("c2").first().first { it.id == "a" }.sources.size)
        database.messageDao().delete("a")
        assertTrue(database.webSearchSourceDao().forMessage("a").isEmpty())
    }

    private fun session(id: String) = ConversationSession(id, "title", "now", emptyList(), 1, 1)
    private fun message(id: String, author: MessageAuthor, content: String, createdAt: Long) =
        ChatMessage(id, author, content, status = MessageStatus.COMPLETED, createdAt = createdAt)
}
