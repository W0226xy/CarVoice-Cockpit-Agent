package com.carvoice.agent.navigation

import com.carvoice.contracts.NavigationExecutionResultPayload
import com.carvoice.contracts.NavigationExecutionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class NavigationActionCoordinatorTest {
    @Test
    fun `only matching action request conversation and connection completes wait`() = runBlocking {
        val coordinator = NavigationActionCoordinator(timeoutMillis = 1_000)
        val waiter = async {
            coordinator.awaitResult("action-1", "request-1", "conversation-1", "connection-1") {}
        }
        yield()

        assertFalse(
            coordinator.complete(
                "request-1", "conversation-other", "connection-1",
                NavigationExecutionResultPayload("action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED),
            ),
        )
        assertFalse(
            coordinator.complete(
                "request-1", "conversation-1", "connection-other",
                NavigationExecutionResultPayload("action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED),
            ),
        )
        assertEquals(
            true,
            coordinator.complete(
                "request-1", "conversation-1", "connection-1",
                NavigationExecutionResultPayload("action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED),
            ),
        )
        assertEquals(
            NavigationActionCoordinator.NavigationAwaitResult.Completed(
                NavigationExecutionStatus.MAP_APP_LAUNCHED,
            ),
            waiter.await(),
        )
        assertFalse(
            coordinator.complete(
                "request-1", "conversation-1", "connection-1",
                NavigationExecutionResultPayload("action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED),
            ),
        )
    }

    @Test
    fun `timeout removes pending action`() = runBlocking {
        val coordinator = NavigationActionCoordinator(timeoutMillis = 1)
        assertEquals(
            NavigationActionCoordinator.NavigationAwaitResult.TimedOut,
            coordinator.awaitResult("action-1", "request-1", "conversation-1", "connection-1") {},
        )
        assertFalse(
            coordinator.complete(
                "request-1", "conversation-1", "connection-1",
                NavigationExecutionResultPayload("action-1", NavigationExecutionStatus.MAP_APP_LAUNCHED),
            ),
        )
    }
}
