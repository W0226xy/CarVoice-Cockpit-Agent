# 架构设计

## 模块职责

| 模块 | 当前职责 | 后续职责 |
| --- | --- | --- |
| `android-app` | MVVM 会话、WebSocket、模拟座舱确认与状态卡片 | 语音及真实客户端动作适配 |
| `agent-server` | Ktor 会话、IntentRouter、TaskRouter、Koog 流式聊天和模拟座舱 | 权限、审计及持久化 |
| `shared-contracts` | 统一意图、消息、确认、状态、工具和错误字段 | 协议版本兼容 |
| 工具层 | 参数校验、确认和按会话隔离的内存模拟状态 | 真实能力适配（须另行安全设计） |
| 知识库/数据层 | 内存车辆资料、可替换嵌入与检索接口 | PostgreSQL/pgvector 检索、Redis 会话缓存、审计存储 |

## 调用关系

```text
Android Compose UI
        |
        | WebSocket events
        v
Ktor transport/session layer
        |
        v
IntentRouter (LLM JSON + safe keyword fallback)
        |
        v
TaskRouter (deterministic when)
        |
        +--> ChatTask -> Koog streaming
        |       +--> SearchDecision -> optional WebSearchPort
        |       +--> Tavily MCP search -> bounded untrusted context
        +--> NavigationTask -> navigation_action -> Android geo map search -> execution result
        +--> MediaTask -> recognition only
        +--> KnowledgeTask -> KnowledgeRetriever -> EmbeddingService + KnowledgeRepository -> dedicated Koog RAG answer stream
        +--> SimulatedCockpitTask -> confirmation -> in-memory state
        +--> ClarificationTask
```

Koog 用于结构化意图识别和 `GENERAL_CHAT` 流式生成，不能决定任务路由。`TaskRouter` 只按 `IntentType` 使用 Kotlin `when` 分发。服务端不能调用 Vehicle HAL、CAN、系统隐藏 API 或其他真实车辆接口。

生产运行仅使用配置的 OpenAI-compatible 模型。`IntentRouter` 和 `ChatTask` 共享同一个 `KoogAgentAdapter`；配置缺失或模型调用失败会显式报错，不存在 Mock LLM 或静默降级路径。测试使用 `src/test` 下的 `FakeCarVoiceAgent`，不会访问真实模型服务。

## 当前 WebSocket 流程

1. Android 发送 `chat_request`，Ktor 校验信封和消息长度。
2. `IntentRouter` 请求模型仅返回 JSON，严格校验后得到 `IntentResult`；失败、非法或低置信度时安全降级，明确业务关键词可兜底。
3. `TaskRouter` 确定性分发到六类任务。只有 `GENERAL_CHAT` 进入现有 Koog 流式聊天。
4. 普通任务统一发送 `assistant_delta` 与 `assistant_completed`，分类过程不产生额外可见回复。
5. 车控任务只生成 `control_confirmation_required`，确认前不修改状态。
6. Android 返回 `control_confirmation_response`；服务端校验会话、重复和过期状态后更新内存状态。
7. 服务端发送 `tool_progress`、`simulated_cockpit_state` 和最终流式文本；取消时不修改状态。

### GENERAL_CHAT 按需联网

1. `SearchDecisionService` 先用确定性规则识别明确搜索、来源要求和天气/新闻/价格等时效请求。
2. 规则无法可靠判断时，复用 Koog Agent 进行严格 JSON 决策；解析失败、模型失败或低置信度均默认不搜索。
3. 决策为否时直接沿用原 `streamReply`，不发送搜索事件。
4. 决策为真时发送 `tool_progress(RUNNING)`，通过 `WebSearchPort` 调用 Tavily Streamable HTTP MCP。
5. Adapter 只从 `tools/list` 选择 `tavily-search` 或 `tavily_search`，不会调用 extract、crawl、map 或 REST API。
6. 有效来源经过协议/URL 校验、去重、条数及文本长度限制后发送 `web_search_sources`，再作为不可信上下文交给 LLM 流式回答。
7. 超时、无结果、禁用、MCP 错误或异常格式发送 `tool_progress(FAILED)`，随后诚实降级为普通流式回答。

Tavily API Key 只存在服务端配置和请求 URI 中，不进入 Android、WebSocket、日志或回答。外部网页不能修改系统提示词、意图路由、工具权限或触发额外工具调用。

### 车辆知识库 RAG 第一阶段

1. 仅车辆手册、故障灯、座舱功能和明确知识库请求会被 `IntentRouter` 标记为 `KNOWLEDGE_QUERY`；天气、新闻和其他实时问题仍为 `GENERAL_CHAT`，由原有搜索决策处理。
2. `TaskRouter` 将该意图交给 `KnowledgeTask`。任务使用 `KnowledgeRetriever`，由注入的 `EmbeddingService` 生成查询向量，并由注入的 `KnowledgeRepository` 返回排序片段。
3. 默认实现是 `CharacterNgramEmbeddingService` 和 `InMemoryKnowledgeRepository`，内置胎压报警、发动机故障灯、前挡除雾和空调使用资料；不连接数据库或网络。
4. 有命中时 `KnowledgeAnswerService` 将问题和编号后的检索候选片段传给 Koog 的独立资料问答 Prompt，再经现有 `assistant_delta` / `assistant_completed` 流式返回。Prompt 明确规定资料为不可信输入、不得执行车控/导航/媒体/联网搜索、且只能依据片段回答。服务端在完整答案中解析实际引用编号，仅在存在有效引用时发送 `knowledge_sources`；Android 来源卡片只展示最终回答实际引用的资料，而非全部检索候选片段。
5. 未命中时返回“当前知识库中没有找到足够依据”；检索异常返回安全的暂不可用提示。知识任务没有 Tavily 端口或工具依赖。

### 车辆知识库 RAG 第三阶段

`RAG_ENABLED=false` 时 RAG 是完全关闭的：不创建 embedding HTTP client、pgvector 连接池或仓库，且不运行 RAG Flyway；`KNOWLEDGE_QUERY` 直接说明功能未启用。启用后 `memory` 模式使用确定性 n-gram 和固定 `system-vehicle-manual` 来源，`pgvector` 模式选择 OpenAI-compatible embedding 与 PostgreSQL/pgvector。

pgvector 的 `document_chunks.embedding` 是 Flyway placeholder 生成的 `vector(${embedding_dimension})`，HNSW 仅建立于这个定长列。启动会核对配置、列 typmod 与既有向量维度；不一致会停止并要求显式迁移/重新索引，不能只改环境变量切换模型。数据库访问使用应用生命周期连接池。

导入链路为 `DocumentUploadRoute → PENDING → lifecycle CoroutineScope → PROCESSING → parser/chunker/embedding/repository → READY|FAILED`。上传立即返回 202；取消应用任务时协程会取消，删除后的任务在写切片前再次检查文档。PDF 还检查扩展名、声明类型和 `%PDF-` 文件头，大小受限；状态分别表示排队、处理中、可检索、处理失败。Android 会将联网与知识库来源一并持久化到 Room，删除消息/会话会级联删除来源。

每个 `conversationId` 的模拟座舱状态相互隔离，仅在服务进程运行期存在。

## 数据与配置边界

- 配置通过环境变量注入，仓库仅保留 `.env.example`；
- PostgreSQL 保存后续知识文档、向量索引和审计数据；
- Redis 只保存短期会话/幂等状态，不作为事实数据源；
- 数据访问通过仓储接口隔离，不在路由或 Koog Prompt 中直接执行 SQL；
- Docker Compose 在引入持久化服务时再增加实际服务定义。

## 服务端包

- `com.carvoice.agent.routes`：HTTP 与后续 WebSocket 入口；
- `com.carvoice.agent.orchestration`：Koog 适配与会话编排；
- `com.carvoice.agent.intent`：结构化意图解析与安全降级；
- `com.carvoice.agent.routing`：确定性任务路由；
- `com.carvoice.agent.tasks`：聊天、导航、媒体、知识、澄清和模拟座舱任务；
- `com.carvoice.agent.search`：联网决策、可替换搜索端口、Tavily MCP Adapter 和受限上下文构建；
- `com.carvoice.agent.knowledge`：第一阶段内存 RAG、可替换检索/嵌入/答案接口；
- `com.carvoice.agent.tools`：工具注册、风险策略与 Mock 能力；
- `com.carvoice.contracts`：跨端统一协议模型。

导航地点搜索通过通用 Android `ACTION_VIEW` + `geo:0,0?q=<encoded destination>` 执行。成功仅表示地图应用已被打开以搜索地点，不能推断路线规划或导航状态。动作等待状态仅存于服务端内存并绑定当前 WebSocket；断线、取消、超时和服务重启均会清理，不会自动重放外部 App 启动。
