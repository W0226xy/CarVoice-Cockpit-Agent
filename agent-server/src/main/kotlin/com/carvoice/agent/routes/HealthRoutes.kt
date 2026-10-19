package com.carvoice.agent.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val projectName: String,
)

fun Application.configureHealthRoutes() {
    routing {
        get("/health") {
            call.respond(
                status = HttpStatusCode.OK,
                message = HealthResponse(
                    status = "ok",
                    projectName = "CarVoice Cockpit Agent",
                ),
            )
        }
    }
}
