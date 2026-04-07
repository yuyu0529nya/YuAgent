# Agent执行链路追踪需求文档

## 📋 业务背景

### 项目概述
YuAgent采用DDD架构，每个Agent会话都会经过复杂的执行链路：高可用模型调度 → 消息处理 → 工具调用 → 响应生成。用户需要能够追溯和查看每个会话的完整执行链路，了解Agent"是如何工作的"。

### 核心需求
实现**Agent执行链路追踪日志**，让用户能够：
1. **执行过程可视化** - 看到Agent执行的每个步骤和技术细节
2. **问题排查支持** - 当Agent表现异常时，能够定位具体原因
3. **成本消耗透明** - 清楚了解每次对话的Token消耗和费用明细
4. **技术细节暴露** - 了解模型选择、工具调用、参数传递等底层机制

### 应用场景
- **用户使用场景**：查看自己与Agent对话的详细执行过程
- **Agent开发场景**：Agent创建者测试和调试自己创建的Agent
- **问题排查场景**：当Agent响应异常时，查看具体的执行链路定位问题

## 🔍 Agent执行链路分析

基于YuAgent项目代码分析，Agent执行的完整链路如下：

### 1. 请求接收阶段
```
用户发送消息 → ConversationAppService.chat()
└── 参数：ChatRequest(message, agentId, sessionId, fileUrls)
└── 用户身份验证：UserContext.getCurrentUserId()
```

### 2. 环境准备阶段  
```
prepareEnvironment()
├── 获取会话信息：sessionDomainService.getSession()
├── 获取Agent配置：agentDomainService.getAgentById()
├── 获取工具列表：getMcpServerNames()
├── 获取工作空间：agentWorkspaceDomainService.getWorkspace()
└── 高可用模型选择：highAvailabilityDomainService.selectBestProvider()
    ├── 输入：model, userId, sessionId, fallbackChain
    ├── 会话亲和性：AffinityType.SESSION
    ├── 降级链：用户配置的模型降级顺序
    └── 输出：HighAvailabilityResult(provider, model, instanceId)
```

### 3. 消息处理阶段
```
AbstractMessageHandler.chat()
├── 创建连接：transport.createConnection(CONNECTION_TIMEOUT)
├── 余额检查：checkBalanceBeforeChat()
├── 创建消息实体：createLlmMessage() + createUserMessage()
├── 初始化聊天内存：initMemory() + buildHistoryMessage()
├── 获取工具提供者：provideTools() → AgentToolManager.createToolProvider()
└── 执行对话：processStreamingChat() 或 processSyncChat()
```

### 4. 模型调用阶段
```
LLM客户端调用
├── 获取客户端：llmServiceFactory.getStreamingClient(provider, model)
├── 构建Agent：buildStreamingAgent(client, toolProvider)
├── 发起调用：agent.chat(userMessage)
└── TokenStream处理：
    ├── onPartialResponse：流式响应处理
    ├── onCompleteResponse：完整响应和Token统计
    ├── onToolExecuted：工具调用处理
    └── onError：错误处理和高可用上报
```

### 5. 工具调用阶段
```
AgentToolManager工具调用
├── MCP客户端创建：McpTransport + McpClient
├── 工具发现：client.listTools()
├── 工具执行：client.callTool(toolName, arguments)
└── 结果处理：返回工具执行结果
```

### 6. 结果处理阶段
```
响应完成处理
├── 保存消息：messageDomainService.saveMessage()
├── 更新Token信息：设置inputTokens + outputTokens
├── 执行计费：billingService.charge(billingContext)
├── 上报高可用结果：reportCallResult(instanceId, success, latency)
└── 返回响应：transport.sendMessage()
```

## 📊 链路追踪监控指标

### 用户维度监控
所有追踪数据以**用户会话**为中心，每个会话生成一条完整的执行链路记录。

### 1. 会话基础信息
```yaml
会话标识:
  - traceId: 执行追踪ID (唯一标识一次完整执行)
  - sessionId: 会话ID
  - userId: 用户ID
  - agentId: Agent ID
  - agentName: Agent名称
  - timestamp: 执行开始时间

用户输入:
  - userMessage: 用户发送的原始消息内容
  - fileUrls: 附件文件列表 (如果有)
  - messageType: 消息类型 (TEXT/IMAGE/FILE)

Agent响应:
  - agentResponse: Agent的完整响应内容  
  - responseType: 响应类型
  - isStreaming: 是否为流式响应
```

### 2. 高可用模型调度记录
```yaml
模型选择过程:
  - originalModelId: 原始请求的模型ID
  - originalProviderName: 原始模型提供商
  - selectedModelId: 实际使用的模型ID
  - selectedProviderName: 实际使用的提供商
  - instanceId: 高可用网关分配的实例ID

会话亲和性:
  - hasSessionAffinity: 是否启用会话亲和性
  - affinityKey: 亲和性键值
  - previousInstanceId: 之前使用的实例ID (如果有)

模型降级信息:
  - isModelDegraded: 是否发生了模型降级
  - degradeReason: 降级原因 (模型不健康/负载过高/服务异常)
  - fallbackChain: 用户配置的降级链
  - fallbackLevel: 降级等级 (使用降级链中的第几个)

高可用网关状态:
  - gatewayEnabled: 高可用网关是否启用
  - gatewayResponse: 网关返回的选择结果
  - fallbackToDefault: 是否降级到默认逻辑
  - gatewayLatency: 网关响应时间
```

### 3. Token使用和计费明细
```yaml
Token统计:
  - inputTokens: 输入Token数量
  - outputTokens: 输出Token数量  
  - totalTokens: 总Token数量

计费信息:
  - serviceId: 计费服务ID (模型表主键)
  - inputCost: 输入Token成本
  - outputCost: 输出Token成本
  - totalCost: 总计费金额
  - billingRequestId: 计费请求ID (幂等性)
  - billingSuccess: 计费是否成功
  - billingErrorMessage: 计费错误信息 (如果失败)

模型定价:
  - inputPricePerToken: 输入Token单价
  - outputPricePerToken: 输出Token单价
  - pricingConfig: 模型定价配置
```

### 4. 工具调用执行记录
```yaml
工具调用序列:
  - toolCallCount: 工具调用总数
  - toolCalls: 工具调用列表

单个工具调用:
  - sequence: 调用序号
  - toolName: 工具名称
  - mcpServerName: MCP服务器名称
  - toolDescription: 工具功能描述

调用参数:
  - requestArguments: 完整的调用参数 (JSON)
  - argumentTypes: 参数类型信息
  - presetParams: 工具预设参数 (来自Agent配置)

执行结果:
  - response: 工具返回的完整结果
  - success: 执行是否成功
  - errorMessage: 错误信息 (如果失败)
  - executionTime: 工具执行耗时 (毫秒)

MCP连接信息:
  - mcpTransportUrl: MCP传输层URL
  - connectionSuccess: MCP连接是否成功
  - mcpClientId: MCP客户端标识
```

### 5. 执行阶段时间记录
```yaml
执行阶段耗时:
  - totalExecutionTime: 总执行时间 (毫秒)
  - environmentPrepareTime: 环境准备耗时
  - balanceCheckTime: 余额检查耗时
  - memoryInitTime: 聊天内存初始化耗时
  - modelCallTime: 模型调用耗时
  - toolExecutionTime: 工具执行总耗时
  - billingTime: 计费处理耗时

流式响应时间:
  - firstTokenTime: 首Token响应时间 (TTFB)
  - streamingDuration: 流式响应总时长
  - avgTokensPerSecond: 平均Token生成速度
```

### 6. 错误和异常记录
```yaml
执行状态:
  - success: 整体执行是否成功
  - errorPhase: 错误发生的阶段
  - errorType: 错误类型 (BUSINESS/TECHNICAL/MODEL)

错误详情:
  - errorMessage: 详细错误信息
  - errorCode: 错误码
  - stackTrace: 异常堆栈 (开发模式)

恢复处理:
  - autoRetry: 是否自动重试
  - retryCount: 重试次数
  - retrySuccess: 重试是否成功
  - fallbackAction: 降级处理动作
```

### 7. 系统上下文信息
```yaml
执行环境:
  - serverInstanceId: 服务器实例ID
  - executionThreadId: 执行线程ID
  - memoryUsage: 内存使用情况
  - connectionPoolStatus: 连接池状态

用户配置:
  - userFallbackChain: 用户配置的模型降级链
  - userToolPresets: 用户的工具预设参数
  - userWorkspaceId: 用户工作空间ID

Agent配置:
  - agentSystemPrompt: Agent系统提示词摘要
  - agentToolConfigs: Agent工具配置
  - agentModelConfigs: Agent模型配置
```

## 🎯 链路追踪场景示例

### 场景1：正常执行链路
```
执行追踪 [trace_1738305025_a8b9c1d2]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 会话信息
   会话ID: session_20250131_143025
   Agent: 编程助手 (agent_coding_assistant)
   用户消息: "帮我写一个Python快速排序函数"
   执行时间: 2025-01-31 14:30:25

🎯 模型调度
   请求模型: GPT-4 Turbo (gpt-4-turbo-2024-04-09)
   实际使用: GPT-4 Turbo (gpt-4-turbo-2024-04-09)
   提供商: OpenAI
   实例ID: instance-us-east-1-003
   高可用状态: ✅ 正常调度
   会话亲和性: ✅ 已绑定到同一实例

💬 消息处理
   环境准备: 45ms
   余额检查: 12ms ✅ 余额充足
   聊天内存: 23ms (加载历史3条消息)
   工具准备: 67ms (加载2个工具: code_runner, file_manager)

🤖 模型调用
   调用开始: 14:30:25.123
   首Token时间: 1,247ms
   流式响应: 3,456ms
   输入Token: 145
   输出Token: 312
   总Token: 457
   成本: ¥0.0184

🛠️ 工具调用 (1次)
   工具: code_runner
   调用时间: 14:30:28.789
   参数: {"code": "def quicksort(arr):\n...", "language": "python"}
   结果: ✅ 代码执行成功，输出测试结果
   耗时: 892ms

💰 计费处理
   计费请求ID: billing_1738305025_001
   服务ID: model_gpt4_turbo
   计费金额: ¥0.0184
   计费状态: ✅ 成功

📊 执行总结
   总执行时间: 4,567ms
   执行状态: ✅ 成功完成
   高可用上报: ✅ 成功上报 (success=true, latency=4567ms)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 场景2：模型降级链路
```
执行追踪 [trace_1738305125_b9c2d3e4]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 会话信息
   会话ID: session_20250131_143125
   Agent: 翻译助手 (agent_translator)
   用户消息: "请翻译这段英文..."

⚠️ 模型调度异常
   请求模型: Claude-3.5-Sonnet
   实际使用: GPT-4 Turbo (降级)
   降级原因: Claude模型实例不健康
   降级链: [claude-3-5-sonnet, gpt-4-turbo, gpt-3.5-turbo]
   降级等级: 第2级
   实例ID: instance-us-east-1-005

🔄 高可用处理过程
   1. 尝试Claude-3.5-Sonnet → ❌ 实例健康检查失败
   2. 降级到GPT-4 Turbo → ✅ 实例健康正常
   3. 会话亲和性更新: 绑定到新实例

💬 消息处理
   [正常处理流程...]

🤖 模型调用
   降级说明: 由于Claude模型暂时不可用，已自动切换到GPT-4 Turbo
   模型参数调整: temperature=0.3 (适配翻译任务)
   [调用详情...]

📊 执行总结
   执行状态: ✅ 成功完成 (已降级)
   高可用上报: ✅ 上报降级结果和新实例性能
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 场景3：工具调用失败链路
```
执行追踪 [trace_1738305225_c0d3e4f5]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 会话信息
   用户消息: "帮我搜索最新的AI新闻"

🛠️ 工具调用异常
   工具: web_search
   MCP服务器: mcp_web_tools
   调用参数: {"query": "AI news 2025", "limit": 10}
   错误信息: MCP连接超时 (connection timeout after 30s)
   
🔄 错误处理流程
   1. 检测到工具调用失败
   2. 尝试重连MCP服务器 → ❌ 仍然失败
   3. Agent降级到无工具模式
   4. 基于已有知识回答

💬 Agent响应策略
   原计划: 使用web_search获取最新新闻 → 工具调用失败
   降级方案: 基于训练数据提供AI领域概述
   用户通知: "抱歉，网络搜索功能暂时不可用，我基于已有知识为您介绍..."

📊 执行总结
   执行状态: ⚠️ 部分成功 (工具功能受限)
   用户体验: 仍然获得了有用的回答
   问题上报: ✅ 已上报MCP服务异常
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 🔧 技术实现方案

### 数据收集方案
- **埋点位置**: 在AbstractMessageHandler的关键节点埋点
- **数据存储**: 异步存储，不影响对话响应性能  
- **链路标识**: 使用traceId关联整个执行链路的所有节点

### 数据存储设计
- **主表**: agent_execution_traces (执行链路主记录)
- **详情表**: model_call_details, tool_call_details, error_details
- **索引**: 用户ID + 时间、会话ID、Agent ID

### 用户界面设计
- **链路详情页**: 显示单次执行的完整链路信息
- **会话历史页**: 列出用户的所有执行链路记录
- **搜索过滤**: 按Agent、时间、状态等条件筛选
- **导出功能**: 导出链路数据用于分析或问题排查

## 💡 核心价值

1. **技术透明化**: 用户清楚了解Agent的工作机制
2. **问题排查**: 快速定位Agent异常的根本原因
3. **成本可控**: 精确的Token和费用明细
4. **开发支持**: Agent创建者可优化Agent配置和工具选择
5. **用户教育**: 帮助用户更好地理解和使用AI Agent