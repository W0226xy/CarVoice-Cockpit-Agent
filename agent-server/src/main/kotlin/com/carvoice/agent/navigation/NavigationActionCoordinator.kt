package com.carvoice.agent.navigation

import com.carvoice.contracts.NavigationExecutionResultPayload
import com.carvoice.contracts.NavigationExecutionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds an in-memory, connection-bound navigation handoff until the same socket
 * reports its result. It deliberately does not survive a server restart or reconnect:
 * replaying an external map launch would not be safe.
 */
class NavigationActionCoordinator(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val mutex = Mutex()
    private val pending = mutableMapOf<String, PendingNavigation>()

    suspend fun awaitResult(
        actionId: String,
        requestId: String,
        conversationId: String,
        connectionId: String,
        emitAction: suspend () -> Unit,
    ): NavigationAwaitResult {
        val entry = PendingNavigation(actionId, requestId, conversationId, connectionId)
        mutex.withLock { pending[actionId] = entry }
        return try {
            emitAction()
            withTimeout(timeoutMillis) { entry.result.await() }
        } catch (_: TimeoutCancellationException) {
            NavigationAwaitResult.TimedOut
        } finally {
            mutex.withLock {
                if (pending[actionId] === entry) pending.remove(actionId)
            }
        }
    }

    suspend fun complete(
        requestId: String,
        conversationId: String,
        connectionId: String,
        payload: NavigationExecutionResultPayload,
    ): Boolean = mutex.withLock {
        val entry = pending[payload.actionId] ?: return@withLock false
        if (entry.requestId != requestId ||
            entry.conversationId != conversationId ||
            entry.connectionId != connectionId ||
            entry.result.isCompleted
        ) {
            return@withLock false
        }
        entry.result.complete(NavigationAwaitResult.Completed(payload.status))
    }

    suspend fun cancelConnection(connectionId: String) {
        val entries = mutex.withLock {
            pending.values.filter { it.connectionId == connectionId }.also {
                it.forEach { pending.remove(it.actionId) }
            }
        }
        entries.forEach { it.result.cancel() }
    }

    private class PendingNavigation(
        val actionId: String,
        val requestId: String,
        val conversationId: String,
        val connectionId: String,
        val result: CompletableDeferred<NavigationAwaitResult> = CompletableDeferred(),
    )

    sealed interface NavigationAwaitResult {
        data class Completed(val status: NavigationExecutionStatus) : NavigationAwaitResult
        data object TimedOut : NavigationAwaitResult
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}
