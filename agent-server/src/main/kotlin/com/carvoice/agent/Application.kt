package com.carvoice.agent

import com.carvoice.agent.routes.configureHealthRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun main() {
    val host = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = host, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            },
        )
    }
    configureHealthRoutes()
}
