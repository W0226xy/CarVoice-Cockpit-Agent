package com.carvoice.agent

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.agent.createCarVoiceAgent
import com.carvoice.agent.config.LlmConfig
import com.carvoice.agent.config.RuntimeEnvironment
import com.carvoice.agent.config.TavilyConfig
import com.carvoice.agent.intent.IntentRouter
import com.carvoice.agent.routing.TaskRouter
import com.carvoice.agent.routes.configureHealthRoutes
import com.carvoice.agent.service.ConversationService
import com.carvoice.agent.tasks.ChatTask
import com.carvoice.agent.search.SearchAnswerContextBuilder
import com.carvoice.agent.search.SearchDecisionService
import com.carvoice.agent.search.WebSearchPort
import com.carvoice.agent.search.createWebSearchPort
import com.carvoice.agent.websocket.ChatSessionHandler
import com.carvoice.agent.websocket.SocketEventCodec
import com.carvoice.agent.websocket.configureChatWebSocketRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json

fun main() {
    val environment = RuntimeEnvironment.load()
    val llmConfig = LlmConfig.fromEnvironment(environment)
    val tavilyConfig = TavilyConfig.fromEnvironment(environment)
    val host = environment["SERVER_HOST"] ?: "0.0.0.0"
    val port = environment["SERVER_PORT"]?.toIntOrNull() ?: 8081
    embeddedServer(Netty, host = host, port = port) {
        module(llmConfig, tavilyConfig = tavilyConfig)
    }.start(wait = true)
}

fun Application.module(
    llmConfig: LlmConfig = LlmConfig.fromEnvironment(),
    agent: CarVoiceAgent = createCarVoiceAgent(llmConfig),
    tavilyConfig: TavilyConfig = TavilyConfig.fromEnvironment(),
    webSearchPort: WebSearchPort = createWebSearchPort(tavilyConfig),
) {
    val codec = SocketEventCodec()
    install(ContentNegotiation) {
        json(codec.json)
    }
    install(WebSockets)

    val taskRouter = TaskRouter(
        chatTask = ChatTask(
            agent = agent,
            searchDecisionService = SearchDecisionService(agent),
            webSearchPort = webSearchPort,
            contextBuilder = SearchAnswerContextBuilder(tavilyConfig.maxContextChars),
        ),
    )
    val conversationService = ConversationService(
        intentRouter = IntentRouter(agent),
        taskRouter = taskRouter,
        json = codec.json,
    )
    val sessionHandler = ChatSessionHandler(
        conversationService = conversationService,
        providerMode = llmConfig.providerMode.value,
        codec = codec,
    )
    configureHealthRoutes(llmConfig.providerMode.value)
    configureChatWebSocketRoutes(sessionHandler)
}
