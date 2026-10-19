package com.carvoice.agent.websocket

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket

fun Application.configureChatWebSocketRoutes(handler: ChatSessionHandler) {
    routing {
        webSocket("/ws/chat") {
            handler.handle(this)
        }
    }
}
