# 车语智舱 Agent（CarVoice Cockpit Agent）

面向 Android 车机与车机模拟器的智能座舱实习项目。项目计划通过 Kotlin、Koog 与 Ktor 实现自然语言交互、受控工具调用、模拟车控、导航/媒体控制、语音交互和车辆知识问答。

## 当前状态

已完成项目准备、流式对话以及意图识别、任务路由与模拟座舱闭环：

- 可构建的 Jetpack Compose 占位应用；
- 带 `GET /health` 的 Ktor 服务；
- 前后端统一的 Kotlin 消息与工具模型；
- 产品边界、架构、工具风险和 API 契约文档；
- 后续数据库、缓存、WebSocket 与 Koog 的明确接入点。
- 面向 16:9 横屏的深色沉浸式对话界面和本地演示消息；
- 连接状态、建议指令、思考动画、消息气泡、内嵌工具状态和车辆操作确认组件；
- 桌面式会话侧栏、本地历史切换和知识库管理占位页面；
- 基于 `StateFlow` 的本地 UI 状态与可替换的会话仓储接口。
- Android 与 Ktor 之间的 `/ws/chat` WebSocket 通信和统一事件协议；
- Koog 0.5.4 OpenAI-compatible 真实模型流式适配器；
- LLM JSON 结构化 `IntentRouter` 与失败时的确定性关键词兜底；
- `TaskRouter` 对聊天、导航、媒体、知识查询、模拟座舱和澄清任务的确定性分发；
- 每个 `conversationId` 独立的运行期模拟座舱状态、确认弹窗和状态卡片；
- 模拟空调、温度、风量、前挡除雾和主/副驾驶车窗控制。
- `GENERAL_CHAT` 的按需联网决策、Tavily MCP 搜索进度和带来源流式回答；
- Android 助手消息下的来源卡片与安全 http/https 外链打开。

已完成车辆知识库 RAG 第一阶段：服务端以内存资料完成检索、受限资料问答与 WebSocket 流式返回。项目禁止连接 Vehicle HAL、CAN 总线及任何真实车辆设备。

车辆知识库第二阶段已增加 pgvector、Flyway、OpenAI-compatible Embedding 与受开关保护的 PDF/TXT 导入 API。默认仍为 `RAG_STORAGE=memory`，不会连接数据库或远程 Embedding；只有显式选择 `pgvector` 才会校验并启用这些配置。

## 技术栈

| 范围 | 技术 |
| --- | --- |
| 车机端 | Kotlin、Android SDK、Jetpack Compose、MVVM（后续引入） |
| Agent 服务端 | Kotlin、Ktor、Coroutines、Koog 0.5.4 |
| 协议 | WebSocket、Kotlin Serialization、共享事件模型 |
| 数据 | PostgreSQL、pgvector、Redis（仅预留） |
| 编排 | Docker Compose（仅预留） |

## 目录

```text
.
├── android-app/          # Android Compose 车机应用
├── agent-server/         # Ktor 服务及 Agent/Tool 接口
├── shared-contracts/     # 统一消息、错误和工具模型
├── docs/                 # 产品、架构、工具和 API 文档
├── docker-compose.yml    # 后续服务编排占位
├── .env.example          # 无敏感信息的环境变量示例
└── README.md
```

## 环境要求

- JDK 21
- Android Studio 与 Android SDK 37
- Android 28 或以上的模拟器/车机模拟器

需要本地环境变量时复制 `.env.example` 为 `.env`，不要提交 `.env` 或任何真实密钥。

## 启动 Android 应用

在 Android Studio 中打开仓库根目录并运行 `android-app`，或连接模拟器后执行：

```bash
./gradlew :android-app:installDebug
adb shell am start -n com.carvoice.cockpit/.MainActivity
```

`debug` 包默认连接 `ws://10.0.2.2:8081/ws/chat`；`10.0.2.2` 是 Android
模拟器访问开发机的专用地址。请先启动下方的 Agent 服务，再启动应用。

真机不能使用 `10.0.2.2`。开发时可在连接设备后执行 `adb reverse tcp:8081
tcp:8081`，并以 `-PagentWsUrl=ws://127.0.0.1:8081/ws/chat` 安装 debug 包：

```bash
./gradlew :android-app:installDebug -PagentWsUrl=ws://127.0.0.1:8081/ws/chat
```

聊天 WebSocket 与知识库管理 HTTP 地址彼此独立：使用
`-PagentWsUrl=ws://10.0.2.2:8081/ws/chat` 配置聊天，使用
`-PagentHttpBaseUrl=http://10.0.2.2:8081` 配置知识库 API。真机配合
`adb reverse tcp:8081 tcp:8081` 时可使用 `http://127.0.0.1:8081`。Release
包不会提供默认 HTTP 地址，必须显式传入已部署的 HTTPS URL；客户端不保存任何
数据库或模型密钥。

发布包不会再错误地尝试访问设备自己的 `wss://localhost`。它必须指向已部署、
具备 TLS 的服务，例如：

```bash
./gradlew :android-app:assembleRelease \
  -PagentWsUrl=wss://agent.example.com/ws/chat
```

未提供该参数时，发布包会明确报告连接失败，而不是访问一个不存在的本地 WSS 服务。

## 启动 Agent 服务

Agent 服务仅支持通过 Koog 调用 OpenAI-compatible 真实模型。复制配置模板并填写服务商参数：

```bash
cp .env.example .env
# 编辑 .env：填写 OPENAI_BASE_URL、OPENAI_API_KEY 和 OPENAI_MODEL
./gradlew :agent-server:run
```

`LLM_PROVIDER` 缺省时默认为 `openai_compatible`，也可显式设置为该值。其他 Provider 值不会降级到测试模型。`OPENAI_BASE_URL` 可以是服务根地址或以 `/v1` 结尾的地址，后者会被规范化，避免 Koog 重复拼接 `/v1/v1/chat/completions`。

服务启动时会读取当前目录的 `.env`，但操作系统环境变量优先，可用于容器和 CI。`OPENAI_BASE_URL`、`OPENAI_API_KEY` 或 `OPENAI_MODEL` 缺失或为空时服务会直接报告配置错误并停止启动。不要把真实 `OPENAI_API_KEY` 写入命令历史、README 或仓库；应由本地环境或密钥管理系统注入。

### 可选 Tavily MCP 联网搜索

联网搜索默认关闭，普通聊天无需 Tavily 配置即可工作。启用时在本地 `.env` 中设置：

```dotenv
TAVILY_ENABLED=true
TAVILY_API_KEY=replace_with_local_secret
TAVILY_MCP_URL=https://mcp.tavily.com/mcp/
TAVILY_SEARCH_TIMEOUT_SECONDS=15
TAVILY_MAX_RESULTS=5
TAVILY_SEARCH_DEPTH=basic
TAVILY_MAX_SNIPPET_CHARS=800
TAVILY_MAX_CONTEXT_CHARS=6000
```

只有 `TAVILY_ENABLED=true`、密钥非空且 MCP URL 合法时才允许联网。服务端通过 Streamable HTTP MCP 仅发现并调用 `tavily-search`/`tavily_search`，不会调用 extract、crawl、map，也不会退化为 Tavily REST API。密钥只留在服务端，不进入日志、WebSocket 或 Android。

`GENERAL_CHAT` 先根据确定性规则和严格 JSON 模型决策判断是否需要联网。搜索超时、无结果、协议异常或远端不可用时会发送失败进度，随后自动回到原普通流式回答，并明确说明没有实时来源。网页内容始终作为不可信参考资料，不能改变系统指令、权限或工具行为。

默认监听 `0.0.0.0:8081`。可通过 `SERVER_HOST` 和 `SERVER_PORT` 修改。健康检查：

```bash
curl http://localhost:8081/health
```

预期响应：

```json
{"status":"ok","projectName":"CarVoice Cockpit Agent","providerMode":"openai_compatible"}
```

### pgvector 知识库与文档导入

复制 `.env.example` 后，设置本地 `DATABASE_PASSWORD`，可先检查并启动 pgvector：

```bash
docker compose config
docker compose up -d postgres
```

将 `RAG_STORAGE=pgvector`，并设置 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL` 与 `EMBEDDING_DIMENSION`。服务启动时执行 Flyway 迁移并验证已有向量维度；配置不完整或维度不一致会失败，不会降级为内存模式。

管理 API 默认由 `KNOWLEDGE_IMPORT_ENABLED=false` 关闭。启用后可创建知识库并上传 UTF-8 TXT 或 PDF：

```bash
curl -X POST http://localhost:8081/api/knowledge-bases -H 'Content-Type: application/json' -d '{"name":"车辆手册"}'
curl -X POST http://localhost:8081/api/knowledge-bases/<baseId>/documents -F 'file=@manual.txt;type=text/plain'
curl http://localhost:8081/api/knowledge-bases/<baseId>/documents
```

上传会同步解析、按重叠切片、批量向量化并写入 pgvector；状态依次为 `PENDING`、`PROCESSING`、`READY` 或 `FAILED`。内存模式保留第一阶段种子资料，不支持导入管理接口。

运行全部单元测试：

```bash
./gradlew test
```

## 开发阶段

1. **车机端基础界面（已完成）**：建立 MVVM、会话页、状态栏和连接状态组件。
2. **实时通信（已完成基础闭环）**：实现 WebSocket、事件分发和错误状态。
3. **Agent 流式回复（已完成）**：接入 Koog 与 OpenAI-compatible 真实模型。
4. **意图路由与模拟座舱（已完成）**：实现结构化分类、任务路由、确认链路和内存态模拟控制。
5. **Tavily MCP 联网搜索（已完成）**：实现按需搜索、来源引用、失败降级和 Android 来源卡片。
6. **车辆知识问答第一阶段（已完成）**：内存资料、可替换 Embedding/Repository/Answer 接口，以及仅依据检索片段的流式回答。
7. **车辆知识问答下一阶段**：接入 PostgreSQL、pgvector、文档导入与可追溯的持久化 RAG。
8. **语音交互**：接入可替换的 ASR/TTS 适配层，完善打断和生命周期管理。
9. **联调与交付**：完成安全测试、可观测性、Docker Compose 编排和演示验收。

详细约定见 [`docs/`](docs/)。
# CarVoice Cockpit Agent

## RAG 第三阶段本地配置

默认 `RAG_ENABLED=false`，此时知识库查询会明确提示未启用，且不会连接 PostgreSQL 或 embedding 服务。开发演示可设为 `RAG_ENABLED=true`、`RAG_STORAGE=memory`；该模式使用内置资料并返回 `system-vehicle-manual` 来源。真实资料库使用 `RAG_STORAGE=pgvector`，并要求 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL` 与 `EMBEDDING_DIMENSION`。

`EMBEDDING_DIMENSION` 会写入 Flyway 的定长 `vector(n)` 列并用于 HNSW 索引。已有库不能只改该环境变量；必须以显式迁移和文档重新索引切换维度。上传接口会返回 `202/PENDING`，随后状态为 `PROCESSING`、`READY` 或 `FAILED`。

运行验证：`./gradlew test`、`./gradlew :agent-server:test`、`./gradlew :android-app:testDebugUnitTest` 和 `docker compose config`。使用 pgvector 时先按 `docker compose up -d postgres` 启动数据库，再提供上述环境变量。
