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
        +--> NavigationTask / MediaTask -> recognition only
        +--> KnowledgeTask -> unavailable notice
        +--> SimulatedCockpitTask -> confirmation -> in-memory state
        +--> ClarificationTask
```

Koog 用于结构化意图识别和 `GENERAL_CHAT` 流式生成，不能决定任务路由。`TaskRouter` 只按 `IntentType` 使用 Kotlin `when` 分发。服务端不能调用 Vehicle HAL、CAN、系统隐藏 API 或其他真实车辆接口。

## 当前 WebSocket 流程

1. Android 发送 `chat_request`，Ktor 校验信封和消息长度。
2. `IntentRouter` 请求模型仅返回 JSON，严格校验后得到 `IntentResult`；失败、非法或低置信度时安全降级，明确业务关键词可兜底。
3. `TaskRouter` 确定性分发到六类任务。只有 `GENERAL_CHAT` 进入现有 Koog 流式聊天。
4. 普通任务统一发送 `assistant_delta` 与 `assistant_completed`，分类过程不产生额外可见回复。
5. 车控任务只生成 `control_confirmation_required`，确认前不修改状态。
6. Android 返回 `control_confirmation_response`；服务端校验会话、重复和过期状态后更新内存状态。
7. 服务端发送 `tool_progress`、`simulated_cockpit_state` 和最终流式文本；取消时不修改状态。

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
- `com.carvoice.agent.knowledge`：未接入的后续 RAG 接口边界；
- `com.carvoice.agent.tools`：工具注册、风险策略与 Mock 能力；
- `com.carvoice.contracts`：跨端统一协议模型。

`ClientAction.Navigate` 与 `ClientAction.PlayMedia` 仅作为未来 Android 动作领域边界，本阶段不会通过协议发送或执行。Tavily MCP、RAG、真实地图/媒体 App、真实车控、持久化和鉴权均未实现。
