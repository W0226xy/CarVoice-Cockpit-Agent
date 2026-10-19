# API 契约

本文定义后续 Android 与 Agent 服务之间的字段约定。当前阶段不实现 WebSocket。字段使用 `camelCase`，时间使用 UTC ISO 8601，标识符由发送方生成并在一次请求内保持不变。

WebSocket 事件后续统一使用以下信封：

```json
{
  "type": "chat.request",
  "protocolVersion": "1.0",
  "payload": {}
}
```

`type` 用于事件分发，`payload` 使用下列结构。服务端必须保留未知字段兼容能力，但不能忽略未知的事件类型或风险字段。

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
  "requestId": "req-20261019-001",
  "callId": "call-001",
  "toolName": "control_air_conditioner",
  "status": "RUNNING",
  "message": "正在更新模拟空调状态"
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
