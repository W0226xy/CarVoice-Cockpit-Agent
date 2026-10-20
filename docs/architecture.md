# 架构设计

## 模块职责

| 模块 | 当前职责 | 后续职责 |
| --- | --- | --- |
| `android-app` | MVVM 会话、WebSocket、模拟座舱确认与状态卡片 | 语音及真实客户端动作适配 |
| `agent-server` | Ktor 会话、IntentRouter、TaskRouter、Koog 流式聊天和模拟座舱 | 权限、审计及持久化 |
| `shared-contracts` | 统一意图、消息、确认、状态、工具和错误字段 | 协议版本兼容 |
| 工具层 | 参数校验、确认和按会话隔离的内存模拟状态 | 真实能力适配（须另行安全设计） |
| 知识库/数据层 | 仅环境变量与部署占位 | PostgreSQL/pgvector 检索、Redis 会话缓存、审计存储 |

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
        +--> NavigationTask / MediaTask -> recognition only
        +--> KnowledgeTask -> unavailable notice
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
- `com.carvoice.agent.knowledge`：未接入的后续 RAG 接口边界；
- `com.carvoice.agent.tools`：工具注册、风险策略与 Mock 能力；
- `com.carvoice.contracts`：跨端统一协议模型。

`ClientAction.Navigate` 与 `ClientAction.PlayMedia` 仅作为未来 Android 动作领域边界，本阶段不会通过协议发送或执行。RAG、真实地图/媒体 App、真实车控、持久化和鉴权均未实现。
