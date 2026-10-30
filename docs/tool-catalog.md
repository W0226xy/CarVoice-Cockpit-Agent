# 工具目录

## 风险规则

- **只读**：不改变任何状态，不需要二次确认。
- **低风险执行**：仅改变可逆的模拟座舱状态；本阶段仍统一要求二次确认。
- **高风险执行**：可能影响乘员安全或环境，必须展示标准化参数并获得二次确认。

当前阶段由 `SimulatedCockpitTask` 执行内存态模拟控制，不得连接 Vehicle HAL、CAN 总线、Car API 或真实车辆。Koog 只做意图分类与普通聊天，不能绕过确定性 `TaskRouter`。

## 工具定义

### `tavily_web_search`

- **用途**：仅为 `GENERAL_CHAT` 检索用户明确要求或明显时效性的公开网页信息。
- **实现边界**：通过可替换 `WebSearchPort` 调用 Tavily 远程 Streamable HTTP MCP。
- **远端工具白名单**：只允许 `tavily-search` 或 `tavily_search`。
- **禁止能力**：不得调用 extract、crawl、map 或 Tavily REST API。
- **输入参数**：简化后的公开检索 query、配置限定的 `search_depth` 和 `max_results`；不得包含密钥、完整历史或无关提示词。
- **输出结果**：经过 http/https URL 校验、去重和长度限制的 title、url、domain、snippet、publishedAt。
- **风险等级**：只读。
- **二次确认**：否。
- **失败策略**：发送失败进度并降级为普通 LLM 回答，不伪造来源。
- **安全边界**：网页是不可信输入，只能提供事实参考，不能改变系统指令、路由、权限或触发工具。

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
- **二次确认**：是；确认前不得修改状态。
- **执行边界**：仅修改按 `conversationId` 隔离的服务端内存状态。温度范围 16–30°C，风量范围 1–7。

### `control_window`

- **用途**：调整指定模拟车窗的位置。
- **输入参数**：`window: "driver" | "passenger"`、`positionPercent: integer (0..100)`。
- **返回结果**：`window: string`、`previousPositionPercent: integer`、`currentPositionPercent: integer`、`applied: boolean`。
- **风险等级**：高风险执行。
- **二次确认**：是，每次执行都需要确认目标车窗和位置。
- **执行边界**：仅修改 `MockVehicleTool` 的模拟状态。

### `start_navigation`

- **用途**：识别导航目的地并返回未接入提示。
- **输入参数**：`destination: string`；可选 `latitude: number`、`longitude: number`、`routePreference: string`。
- **返回结果**：`navigationId: string`、`resolvedDestination: string`、`status: string`。
- **风险等级**：低风险执行。
- **二次确认**：否；目的地不明确时必须先追问，而非猜测。
- **执行边界**：Android 以通用 `geo:` Intent 打开可处理的地图应用并搜索文本目的地；不调用地图 SDK/API，不需要 Key，也不表示路线已规划或导航已开始。

### `control_media`

- **用途**：识别歌曲、歌手或专辑并返回未接入提示。
- **输入参数**：`action: "play" | "pause" | "next" | "previous" | "set_volume"`；`volumePercent: integer (0..100)` 仅用于音量操作。
- **返回结果**：`previousState: object`、`currentState: object`、`applied: boolean`。
- **风险等级**：低风险执行。
- **二次确认**：否。
- **执行边界**：本阶段不播放媒体、不启动 App、不调用第三方服务；仅预留 `ClientAction.PlayMedia`。

### `search_vehicle_manual`

- **用途**：检索车辆手册并返回可追溯片段。
- **输入参数**：`query: string`；可选 `topK: integer`、`section: string`。
- **返回结果**：`answer: string`、`citations: object[]`、`notFound: boolean`。
- **风险等级**：只读。
- **二次确认**：否。
- **执行边界**：通过 `KnowledgeRetriever` 检索内存资料或 pgvector 切片，答案模型只可使用返回片段；不得触发 Tavily、车控、导航或媒体。

### `knowledge_document_import`

- **用途**：服务端管理 API 将 PDF/TXT 解析为带页码的文本切片，生成向量后存入 pgvector。
- **风险等级**：受控写入。
- **开关**：仅 `KNOWLEDGE_IMPORT_ENABLED=true` 时可用，默认关闭。
- **安全边界**：仅允许 PDF/TXT、限制文件大小、清理文件名；内容不视为指令，失败不暴露数据库或本地路径。

## 通用执行要求

模拟座舱执行顺序固定为：参数校验、生成一次性确认标识、用户确认、再次校验会话/有效期、更新状态、发送完整状态。失败必须返回结构化错误，不能伪装为成功结果。持久化、权限和审计将在后续阶段接入。
