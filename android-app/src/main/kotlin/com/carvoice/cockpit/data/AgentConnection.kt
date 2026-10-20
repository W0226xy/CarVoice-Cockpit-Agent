package com.carvoice.cockpit.data

import com.carvoice.cockpit.model.ConnectionState
import com.carvoice.cockpit.network.SocketEventCodec
import com.carvoice.contracts.SocketEvent
import com.carvoice.contracts.SocketEventType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentConnectionConfig(
    val endpoint: String,
)

class AgentConnection(
    private val config: AgentConnectionConfig,
    private val codec: SocketEventCodec = SocketEventCodec(),
    private val client: HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 32)
    private var connectionJob: Job? = null
    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    @Volatile
    private var closed = false

    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    fun connect() {
        if (closed || connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            _state.value = ConnectionState.CONNECTING
            try {
                client.webSocket(urlString = config.endpoint) {
                    session = this
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val event = codec.decode(frame.readText())
                            if (event.type == SocketEventType.CONNECTION_READY) {
                                _state.value = ConnectionState.CONNECTED
                            }
                            _events.emit(event)
                        }
                    }
                }
                if (!closed) _state.value = ConnectionState.DISCONNECTED
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!closed) _state.value = ConnectionState.FAILED
            } finally {
                session = null
            }
        }
    }

    suspend fun send(event: SocketEvent): Result<Unit> = runCatching {
        val activeSession = session ?: error("WebSocket is not connected")
        activeSession.send(Frame.Text(codec.encode(event)))
    }

    fun markReady() {
        _state.value = ConnectionState.CONNECTED
    }

    fun close() {
        closed = true
        connectionJob?.cancel()
        session = null
        client.close()
        scope.cancel()
        _state.value = ConnectionState.DISCONNECTED
    }
}
