package com.carvoice.agent

import com.carvoice.agent.agent.CarVoiceAgent
import com.carvoice.agent.agent.createCarVoiceAgent
import com.carvoice.agent.config.LlmConfig
import com.carvoice.agent.config.RuntimeEnvironment
import com.carvoice.agent.config.RagConfig
import com.carvoice.agent.config.RagStorage
import com.carvoice.agent.config.TavilyConfig
import com.carvoice.agent.intent.IntentRouter
import com.carvoice.agent.knowledge.AgentKnowledgeAnswerService
import com.carvoice.agent.knowledge.CharacterNgramEmbeddingService
import com.carvoice.agent.knowledge.DefaultKnowledgeRetriever
import com.carvoice.agent.knowledge.EmbeddingService
import com.carvoice.agent.knowledge.InMemoryKnowledgeRepository
import com.carvoice.agent.knowledge.KnowledgeRepository
import com.carvoice.agent.knowledge.KnowledgeRetriever
import com.carvoice.agent.knowledge.KnowledgeAnswerService
import com.carvoice.agent.knowledge.phaseOneVehicleKnowledge
import com.carvoice.agent.knowledge.OpenAiCompatibleEmbeddingService
import com.carvoice.agent.knowledge.PgVectorKnowledgeRepository
import com.carvoice.agent.knowledge.KnowledgeManagementRepository
import com.carvoice.agent.knowledge.DocumentChunker
import com.carvoice.agent.knowledge.DocumentIngestionService
import com.carvoice.agent.knowledge.SafeDocumentParser
import com.carvoice.agent.routing.TaskRouter
import com.carvoice.agent.routes.configureHealthRoutes
import com.carvoice.agent.routes.configureKnowledgeManagementRoutes
import com.carvoice.agent.service.ConversationService
import com.carvoice.agent.tasks.ChatTask
import com.carvoice.agent.tasks.KnowledgeTask
import com.carvoice.agent.search.SearchAnswerContextBuilder
import com.carvoice.agent.search.SearchDecisionService
import com.carvoice.agent.search.WebSearchPort
import com.carvoice.agent.search.createWebSearchPort
import com.carvoice.agent.websocket.ChatSessionHandler
import com.carvoice.agent.websocket.SocketEventCodec
import com.carvoice.agent.websocket.configureChatWebSocketRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.WebSockets
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun main() {
    val environment = RuntimeEnvironment.load()
    val llmConfig = LlmConfig.fromEnvironment(environment)
    val tavilyConfig = TavilyConfig.fromEnvironment(environment)
    val ragConfig = RagConfig.fromEnvironment(environment)
    val host = environment["SERVER_HOST"] ?: "0.0.0.0"
    val port = environment["SERVER_PORT"]?.toIntOrNull() ?: 8081
    embeddedServer(Netty, host = host, port = port) {
        module(llmConfig, tavilyConfig = tavilyConfig, ragConfig = ragConfig)
    }.start(wait = true)
}

fun Application.module(
    llmConfig: LlmConfig = LlmConfig.fromEnvironment(),
    agent: CarVoiceAgent = createCarVoiceAgent(llmConfig),
    tavilyConfig: TavilyConfig = TavilyConfig.fromEnvironment(),
    webSearchPort: WebSearchPort = createWebSearchPort(tavilyConfig),
    ragConfig: RagConfig = RagConfig.fromEnvironment(),
    embeddingService: EmbeddingService? = null,
    knowledgeRepository: KnowledgeRepository? = null,
    knowledgeRetriever: KnowledgeRetriever? = null,
    knowledgeAnswerService: KnowledgeAnswerService = AgentKnowledgeAnswerService(agent),
) {
    val codec = SocketEventCodec()
    install(ContentNegotiation) {
        json(codec.json)
    }
    install(WebSockets)

    val resolvedEmbeddingService = embeddingService ?: when (ragConfig.storage) {
        RagStorage.MEMORY -> CharacterNgramEmbeddingService()
        RagStorage.PGVECTOR -> OpenAiCompatibleEmbeddingService(
            ragConfig,
            HttpClient(OkHttp) { install(ClientContentNegotiation) { json() } },
        )
    }
    val managementRepository = (knowledgeRepository ?: when (ragConfig.storage) {
        RagStorage.MEMORY -> InMemoryKnowledgeRepository(phaseOneVehicleKnowledge(), resolvedEmbeddingService)
        RagStorage.PGVECTOR -> PgVectorKnowledgeRepository(ragConfig)
    }) as? KnowledgeManagementRepository
    val resolvedRepository = knowledgeRepository ?: managementRepository ?: InMemoryKnowledgeRepository(
        phaseOneVehicleKnowledge(), resolvedEmbeddingService,
    )
    val taskRouter = TaskRouter(
        chatTask = ChatTask(
            agent = agent,
            searchDecisionService = SearchDecisionService(agent),
            webSearchPort = webSearchPort,
            contextBuilder = SearchAnswerContextBuilder(tavilyConfig.maxContextChars),
        ),
        knowledgeTask = KnowledgeTask(
            retriever = knowledgeRetriever ?: DefaultKnowledgeRetriever(
                resolvedRepository,
                resolvedEmbeddingService,
                ragConfig.topK,
            ),
            answerService = knowledgeAnswerService,
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
    configureKnowledgeManagementRoutes(
        ragConfig,
        managementRepository,
        managementRepository?.let { DocumentIngestionService(it, SafeDocumentParser(), DocumentChunker(ragConfig.chunkSize, ragConfig.chunkOverlap), resolvedEmbeddingService) },
    )
    configureChatWebSocketRoutes(sessionHandler)
}
