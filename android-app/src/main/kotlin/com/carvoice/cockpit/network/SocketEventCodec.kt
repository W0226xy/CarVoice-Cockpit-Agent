package com.carvoice.cockpit.network

import com.carvoice.contracts.SocketEvent
import kotlinx.serialization.json.Json

class SocketEventCodec(
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun encode(event: SocketEvent): String = json.encodeToString(event)

    fun decode(text: String): SocketEvent = json.decodeFromString(text)
}
