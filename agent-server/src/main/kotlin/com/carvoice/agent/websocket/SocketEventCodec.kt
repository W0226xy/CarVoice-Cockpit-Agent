package com.carvoice.agent.websocket

import com.carvoice.contracts.SocketEvent
import kotlinx.serialization.json.Json

class SocketEventCodec(
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun decode(text: String): SocketEvent = json.decodeFromString(text)

    fun encode(event: SocketEvent): String = json.encodeToString(event)
}
