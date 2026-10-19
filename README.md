# 车语智舱 Agent（CarVoice Cockpit Agent）

面向 Android 车机与车机模拟器的智能座舱实习项目。项目计划通过 Kotlin、Koog 与 Ktor 实现自然语言交互、受控工具调用、模拟车控、导航/媒体控制、语音交互和车辆知识问答。

## 当前状态

已完成项目准备、车机端基础界面和第 3 阶段流式对话闭环：

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
- Koog 0.5.4 OpenAI-compatible 流式模型适配器及无密钥 Mock 模式。

当前不包含 Tool Registry、真实或模拟车控逻辑、语音、RAG、数据库、缓存和鉴权。项目禁止连接 Vehicle HAL、CAN 总线及任何真实车辆设备。

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

## 启动 Agent 服务

Mock 模式无需 API Key：

```bash
LLM_PROVIDER=mock SERVER_PORT=8081 ./gradlew :agent-server:run
```

OpenAI-compatible 模式通过 Koog 调用模型：

```bash
cp .env.example .env
# 编辑 .env：填写真实的 API Key、模型名与服务商域名（不加 /v1）
./gradlew :agent-server:run
```

服务启动时会读取当前目录的 `.env`，但操作系统环境变量优先，可用于容器和 CI。不要把真实 `OPENAI_API_KEY` 写入命令历史、README 或仓库；应由本地环境或密钥管理系统注入。

默认监听 `0.0.0.0:8081`。可通过 `SERVER_HOST` 和 `SERVER_PORT` 修改。健康检查：

```bash
curl http://localhost:8081/health
```

预期响应：

```json
{"status":"ok","projectName":"CarVoice Cockpit Agent","providerMode":"mock"}
```

运行全部单元测试：

```bash
./gradlew test
```

## 开发阶段

1. **车机端基础界面（已完成）**：建立 MVVM、会话页、状态栏和连接状态组件。
2. **实时通信（已完成基础闭环）**：实现 WebSocket、事件分发和错误状态。
3. **Agent 流式回复（已完成）**：接入 Koog 与 OpenAI-compatible 模型，支持 Mock 演示。
4. **模拟座舱工具**：实现 `MockVehicleTool`、导航/媒体模拟器、确认与审计链路。
5. **语音交互**：接入可替换的 ASR/TTS 适配层，完善打断和生命周期管理。
6. **车辆知识问答**：接入 PostgreSQL、pgvector、Redis 和可追溯的 RAG。
7. **联调与交付**：完成安全测试、可观测性、Docker Compose 编排和演示验收。

详细约定见 [`docs/`](docs/)。
