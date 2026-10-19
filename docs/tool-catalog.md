# 工具目录

## 风险规则

- **只读**：不改变任何状态，不需要二次确认。
- **低风险执行**：仅改变可逆的模拟座舱状态；显式用户指令下无需二次确认，但仍需参数、权限和审计检查。
- **高风险执行**：可能影响乘员安全或环境，必须展示标准化参数并获得二次确认。

第 1 阶段仅定义工具，不执行任何调用。后续首期的车辆状态与控制工具只能调用 `MockVehicleTool`，不得连接 Vehicle HAL、CAN 总线或真实车辆。Koog 只能提出工具调用，不能绕过 `ToolRegistry`。

## 工具定义

### `query_vehicle_status`

- **用途**：读取模拟车辆状态。
- **输入参数**：`fields: string[]`，可选值包括 `speedKph`、`rangeKm`、`cabinTemperatureC`、`airConditioner`、`windows`。
- **返回结果**：`values: object`、`sampledAt: string`（ISO 8601）。
- **风险等级**：只读。
- **二次确认**：否。
- **执行边界**：仅从 `MockVehicleTool` 的模拟状态读取。

### `control_air_conditioner`

- **用途**：开启、关闭或调整模拟空调。
- **输入参数**：`action: "turn_on" | "turn_off" | "set"`；可选 `targetTemperatureC: number`、`fanLevel: integer`、`mode: string`。
- **返回结果**：`previousState: object`、`currentState: object`、`applied: boolean`。
- **风险等级**：低风险执行。
- **二次确认**：否；参数必须在配置的安全范围内。
- **执行边界**：仅修改 `MockVehicleTool` 的模拟状态。

### `control_window`

- **用途**：调整指定模拟车窗的位置。
- **输入参数**：`window: "driver" | "passenger" | "rear_left" | "rear_right"`、`positionPercent: integer (0..100)`。
- **返回结果**：`window: string`、`previousPositionPercent: integer`、`currentPositionPercent: integer`、`applied: boolean`。
- **风险等级**：高风险执行。
- **二次确认**：是，每次执行都需要确认目标车窗和位置。
- **执行边界**：仅修改 `MockVehicleTool` 的模拟状态。

### `start_navigation`

- **用途**：启动到指定目的地的模拟导航。
- **输入参数**：`destination: string`；可选 `latitude: number`、`longitude: number`、`routePreference: string`。
- **返回结果**：`navigationId: string`、`resolvedDestination: string`、`status: string`。
- **风险等级**：低风险执行。
- **二次确认**：否；目的地不明确时必须先追问，而非猜测。
- **执行边界**：首期只调用模拟导航适配器，不启动真实导航应用。

### `control_media`

- **用途**：控制模拟媒体播放。
- **输入参数**：`action: "play" | "pause" | "next" | "previous" | "set_volume"`；`volumePercent: integer (0..100)` 仅用于音量操作。
- **返回结果**：`previousState: object`、`currentState: object`、`applied: boolean`。
- **风险等级**：低风险执行。
- **二次确认**：否。
- **执行边界**：首期只修改模拟媒体状态，不调用车机媒体会话。

### `search_vehicle_manual`

- **用途**：检索车辆手册并返回可追溯片段。
- **输入参数**：`query: string`；可选 `topK: integer`、`section: string`。
- **返回结果**：`answer: string`、`citations: object[]`、`notFound: boolean`。
- **风险等级**：只读。
- **二次确认**：否。
- **执行边界**：知识库接入前不得伪造手册来源；本阶段不执行检索。

## 通用执行要求

每次调用都必须携带 `requestId`、`callId` 和调用方身份。执行顺序固定为：工具白名单校验、Schema 校验、风险分级、用户确认（如需）、权限校验、执行、审计。失败必须返回结构化错误，不能伪装为成功结果。
