# API 契约

本文定义 Android 与 Agent 服务之间的 WebSocket 字段约定。字段使用 `camelCase`，标识符由发送方生成并在一次请求内保持不变。

WebSocket 事件后续统一使用以下信封：

```json
{
  "type": "chat_request",
  "requestId": "req-20261019-001",
  "conversationId": "conversation-001",
  "payload": {}
}
```

`type` 用于事件分发，`payload` 使用下列结构。`SocketEvent` 及所有 payload 均以 `shared-contracts` 为唯一代码来源。

## WebSocket 事件

| 方向 | `type` | 用途 |
| --- | --- | --- |
| Android → 服务端 | `chat_request` | 发送用户文本 |
| 服务端 → Android | `connection_ready` | 连接和 Provider 模式就绪 |
| 服务端 → Android | `assistant_delta` | Assistant 增量文本 |
| 服务端 → Android | `assistant_completed` | 当前回复结束 |
| 服务端 → Android | `error` | 参数、协议、连接或模型错误 |
| 服务端 → Android | `control_confirmation_required` | 请求确认模拟座舱操作 |
| Android → 服务端 | `control_confirmation_response` | 确认或取消模拟座舱操作 |
| 服务端 → Android | `tool_progress` | 模拟座舱工具进度 |
| 服务端 → Android | `simulated_cockpit_state` | 执行后的完整模拟座舱状态 |
| 服务端 → Android | `web_search_sources` | 当前助手回答引用的联网来源 |

`chat_request` 的 payload：

```json
{"message":"你好"}
```

`assistant_delta` 的 payload：

```json
{"text":"你好，"}
```

`error` 的 payload：

```json
{
  "code": "MODEL_ERROR",
  "message": "模型服务暂时不可用，请稍后重试。",
  "retryable": true
}
```

服务端拒绝空消息和超过 4000 字符的消息。非法 JSON、未知事件及模型失败均返回安全错误，不包含堆栈、密钥、环境变量或完整模型地址。

## 知识库管理 HTTP API

这些接口独立于 Android WebSocket，并且仅当 `KNOWLEDGE_IMPORT_ENABLED=true` 且服务使用 pgvector 管理仓库时可用；否则返回 `403` 和 `KNOWLEDGE_IMPORT_DISABLED`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/knowledge-bases` | 创建 `{ "name": "车辆手册", "description": "" }` |
| GET | `/api/knowledge-bases` | 列出知识库 |
| POST | `/api/knowledge-bases/{id}/documents` | multipart 字段 `file` 上传 PDF/TXT |
| GET | `/api/knowledge-bases/{id}/documents` | 查询文档与处理状态 |
| GET | `/api/knowledge-bases/{id}/documents/{documentId}` | 查询单文档状态 |
| DELETE | `/api/knowledge-bases/{id}/documents/{documentId}` | 删除文档及其切片 |

上传最大大小受 `KNOWLEDGE_MAX_FILE_SIZE_MB` 限制。服务只接受 `application/pdf` 和 `text/plain`，会清理展示文件名；无效 UUID、未知文档、路径字符、解析失败和数据库内部错误均不会返回本地路径、堆栈或连接信息。

## 联网搜索事件时序

需要联网的 `GENERAL_CHAT` 正常顺序：

1. `tool_progress`，`toolName=tavily_web_search`、`status=RUNNING`；
2. `web_search_sources`；
3. `tool_progress`，`status=COMPLETED`；
4. 一个或多个 `assistant_delta`；
5. `assistant_completed`。

`web_search_sources` payload：

```json
{
  "conversationId": "conversation-001",
  "messageId": "req-20261019-001",
  "query": "最近 AI 新闻",
  "sources": [
    {
      "index": 1,
      "title": "来源标题",
      "url": "https://example.com/news",
      "domain": "example.com",
      "snippet": "受长度限制的来源摘要",
      "publishedAt": "2026-10-20"
    }
  ]
}
```

`messageId` 与当前请求的助手回答关联，正文通过 `[1]`、`[2]` 引用对应来源。仅在搜索成功且至少存在一个合法 http/https 来源时发送。旧客户端可以忽略未知事件。

搜索禁用、超时、无结果、MCP 不可用或格式异常时只发送 `tool_progress(FAILED)`，不发送来源事件；随后同一助手消息继续普通流式回答，并明确说明未获得实时来源。任何事件均不得包含 API Key、授权头、MCP URL 或内部异常。

## 模拟座舱确认

`control_confirmation_required`：

```json
{
  "confirmationId": "confirm-001",
  "description": "打开空调，将温度设置为 24°C",
  "expiresAtEpochMillis": 1792466400000
}
```

`control_confirmation_response`：

```json
{"confirmationId":"confirm-001","confirmed":true}
```

确认标识只能使用一次，并绑定创建它的 `conversationId`。无效、重复和过期确认分别返回稳定错误事件；取消和错误均不修改状态。

`simulated_cockpit_state`：

```json
{
  "acEnabled": true,
  "targetTemperature": 24.0,
  "fanLevel": 1,
  "frontDefrostEnabled": false,
  "driverWindowPercent": 0,
  "passengerWindowPercent": 0
}
```

状态按 `conversationId` 隔离且只保存在服务端内存中。

## ChatRequest

客户端发起一轮会话。

```json
{
  "requestId": "req-20261019-001",
  "sessionId": "session-001",
  "message": "把空调调到 23 度",
  "locale": "zh-CN",
  "metadata": {
    "clientVersion": "0.1.0"
  }
}
```

必填字段为 `requestId`、`sessionId`、`message`。空消息必须拒绝。

## ChatResponse

非流式场景或一轮对话的最终聚合结果。

```json
{
  "requestId": "req-20261019-001",
  "sessionId": "session-001",
  "message": "模拟空调已设置为 23°C。",
  "finishReason": "COMPLETED",
  "toolCalls": [
    {
      "callId": "call-001",
      "toolName": "control_air_conditioner",
      "arguments": {
        "action": "set",
        "targetTemperatureC": 23
      }
    }
  ]
}
```

`finishReason` 为 `COMPLETED`、`TOOL_CALL`、`CANCELLED` 或 `ERROR`。

## ChatChunk

服务端流式文本片段。客户端按 `sequence` 排序，同一请求最后一条的 `done` 为 `true`。

```json
{
  "requestId": "req-20261019-001",
  "sessionId": "session-001",
  "sequence": 0,
  "content": "正在为你",
  "done": false
}
```

## ToolCallProgress

工具生命周期通知，不代表执行结果。

```json
{
  "callId": "call-001",
  "toolName": "simulated_cockpit_control",
  "status": "RUNNING",
  "message": "模拟座舱控制已完成"
}
```

`status` 为 `PENDING_CONFIRMATION`、`RUNNING`、`COMPLETED`、`FAILED` 或 `CANCELLED`。

## ToolExecutionResult

工具执行终态。`success=true` 时可包含 `output`；失败时必须包含 `error`，不能同时声明成功。

```json
{
  "requestId": "req-20261019-001",
  "callId": "call-001",
  "toolName": "control_air_conditioner",
  "success": true,
  "output": {
    "applied": true,
    "currentState": {
      "enabled": true,
      "targetTemperatureC": 23
    }
  },
  "error": null
}
```

## ErrorResponse

协议、鉴权、校验、服务或执行错误统一返回此结构。

```json
{
  "requestId": "req-20261019-001",
  "code": "INVALID_TOOL_ARGUMENT",
  "message": "targetTemperatureC 超出允许范围",
  "retryable": false,
  "details": {
    "field": "targetTemperatureC",
    "allowedRange": "16..30"
  }
}
```

`code` 是稳定、可供客户端分支处理的机器码；`message` 用于展示；`details` 不得包含密钥、Prompt、堆栈或内部网络信息。

## ToolDefinition

服务端注册工具时使用的统一描述。

```json
{
  "name": "control_window",
  "description": "调整指定模拟车窗的位置",
  "riskLevel": "HIGH_RISK_EXECUTION",
  "requiresConfirmation": true,
  "inputSchema": {
    "type": "object",
    "required": ["window", "positionPercent"]
  }
}
```

风险值为 `READ_ONLY`、`LOW_RISK_EXECUTION`、`HIGH_RISK_EXECUTION`。客户端展示只作辅助，服务端始终是风险判定和执行授权的最终责任方。

## 关联、幂等与错误处理

- `requestId` 关联一轮用户请求，重复请求不得造成重复执行；
- `callId` 唯一标识一次工具调用；
- 客户端只接受当前 `sessionId` 的消息；
- 断线恢复时由后续协议协商最后确认的序号；
- JSON 解析、未知事件、参数校验和权限错误必须显式返回 `ErrorResponse`。
