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
import com.carvoice.agent.tasks.DisabledKnowledgeTask
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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import io.ktor.server.application.ApplicationStopped
import com.carvoice.agent.knowledge.DocumentImportManager

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

    // Keep disabled RAG completely inert: no embedding client, repository, Flyway migration or DB pool.
    val resolvedEmbeddingService = if (!ragConfig.enabled) null else embeddingService ?: when (ragConfig.storage) {
        RagStorage.MEMORY -> CharacterNgramEmbeddingService()
        RagStorage.PGVECTOR -> OpenAiCompatibleEmbeddingService(
            ragConfig,
            embeddingHttpClient(),
        )
    }
    val managementRepository = if (!ragConfig.enabled) null else (knowledgeRepository ?: when (ragConfig.storage) {
        RagStorage.MEMORY -> InMemoryKnowledgeRepository(phaseOneVehicleKnowledge(), requireNotNull(resolvedEmbeddingService))
        RagStorage.PGVECTOR -> PgVectorKnowledgeRepository(ragConfig)
    }) as? KnowledgeManagementRepository
    val resolvedRepository = if (ragConfig.enabled) knowledgeRepository ?: managementRepository ?: InMemoryKnowledgeRepository(phaseOneVehicleKnowledge(), requireNotNull(resolvedEmbeddingService)) else null
    val importScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val ingestion = managementRepository?.let { DocumentIngestionService(it, SafeDocumentParser(), DocumentChunker(ragConfig.chunkSize, ragConfig.chunkOverlap), requireNotNull(resolvedEmbeddingService)) }
    val imports = ingestion?.let { DocumentImportManager(importScope, it) }
    environment.monitor.subscribe(ApplicationStopped) {
        imports?.close(); importScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        (managementRepository as? AutoCloseable)?.close()
        (resolvedEmbeddingService as? AutoCloseable)?.close()
    }
    val taskRouter = TaskRouter(
        chatTask = ChatTask(
            agent = agent,
            searchDecisionService = SearchDecisionService(agent),
            webSearchPort = webSearchPort,
            contextBuilder = SearchAnswerContextBuilder(tavilyConfig.maxContextChars),
        ),
        knowledgeTask = if (!ragConfig.enabled) DisabledKnowledgeTask() else KnowledgeTask(
            retriever = knowledgeRetriever ?: DefaultKnowledgeRetriever(
                requireNotNull(resolvedRepository),
                requireNotNull(resolvedEmbeddingService),
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
        ingestion, imports,
    )
    configureChatWebSocketRoutes(sessionHandler)
}

/** OkHttp does not automatically consume shell proxy variables, unlike curl. */
private fun embeddingHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            System.getenv("HTTPS_PROXY").toHttpProxyOrNull()?.let(::proxy)
        }
    }
    // OpenAI-compatible vendors commonly add id/model/usage/object fields.
    install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private fun String?.toHttpProxyOrNull(): Proxy? = this?.takeIf { it.isNotBlank() }?.let { value ->
    runCatching {
        val uri = URI(value)
        require(!uri.host.isNullOrBlank() && uri.port in 1..65535)
        Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
    }.getOrNull()
}
