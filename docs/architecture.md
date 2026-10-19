# 架构设计

## 模块职责

| 模块 | 当前职责 | 后续职责 |
| --- | --- | --- |
| `android-app` | 展示静态首页和未连接状态 | MVVM 会话界面、WebSocket 客户端、语音适配、工具确认 UI |
| `agent-server` | Ktor 启动与健康检查、编排/工具接口占位 | 会话管理、Koog 编排、协议路由、确认/权限/审计 |
| `shared-contracts` | 统一消息、工具、错误字段 | WebSocket 事件和协议版本兼容 |
| 工具层 | 仅定义 `ToolRegistry`、`MockVehicleTool` 边界 | 参数校验、风险策略、模拟状态、导航/媒体适配 |
| 知识库/数据层 | 仅环境变量与部署占位 | PostgreSQL/pgvector 检索、Redis 会话缓存、审计存储 |

## 调用关系

```text
Android Compose UI
        |
        | future WebSocket messages
        v
Ktor transport/session layer
        |
        v
Koog Agent orchestration
        |
        v
Tool Registry -> validation -> risk/confirmation -> authorization -> audit
        |                                      |
        v                                      v
MockVehicleTool / mock navigation/media   manual search interface
        |                                      |
        v                                      v
in-memory simulated state             PostgreSQL + pgvector (future)
```

Koog 只负责理解意图、编排对话和提出工具调用。它不能直接调用 Vehicle HAL、CAN、系统隐藏 API 或其他底层车辆接口。所有工具调用必须经过服务端注册表和安全链路；首期车辆状态的读取与修改只能委托给 `MockVehicleTool`。

## 后续 WebSocket 流程

1. Android 建立带协议版本和会话身份的 WebSocket 连接。
2. 客户端发送 `ChatRequest`，服务端按 `requestId` 去重并绑定 `sessionId`。
3. Ktor 将请求交给 Koog 编排器。
4. 普通回答以有序 `ChatChunk` 流式返回。
5. 如需工具，服务端先验证工具白名单、参数和权限，再发送 `ToolCallProgress`。
6. 高风险工具进入 `PENDING_CONFIRMATION`，客户端明确确认后才能继续；拒绝或超时均取消。
7. 工具层只调用模拟能力并返回 `ToolExecutionResult`，服务端写入审计记录。
8. Koog 基于工具结果生成最终回复；异常统一返回 `ErrorResponse`。

客户端必须按 `requestId`、`callId` 和 `sequence` 关联事件，不依赖消息到达时间推断状态。

## 数据与配置边界

- 配置通过环境变量注入，仓库仅保留 `.env.example`；
- PostgreSQL 保存后续知识文档、向量索引和审计数据；
- Redis 只保存短期会话/幂等状态，不作为事实数据源；
- 数据访问通过仓储接口隔离，不在路由或 Koog Prompt 中直接执行 SQL；
- Docker Compose 在引入持久化服务时再增加实际服务定义。

## 服务端预留包

- `com.carvoice.agent.routes`：HTTP 与后续 WebSocket 入口；
- `com.carvoice.agent.orchestration`：Koog 适配与会话编排；
- `com.carvoice.agent.tools`：工具注册、风险策略与 Mock 能力；
- `com.carvoice.contracts`：跨端统一协议模型。
