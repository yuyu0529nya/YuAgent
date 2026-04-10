# YuAgent

YuAgent 是一个面向知识管理与智能体应用的全栈 AI 平台，围绕 `LLM + RAG + MCP` 构建，支持文档上传、OCR 识别、向量检索、知识问答、长期记忆、工具扩展、Agent 管理与执行链路追踪。

它不只是一个聊天 Demo，而是一套把知识接入、能力扩展、异步处理和平台化管理真正串起来的智能体系统。

## ✨ 项目亮点

- **🧠 智能体平台化**：支持 Agent 配置、版本管理、发布与工作区组织，便于按角色和场景复用能力。
- **📚 完整 RAG 链路**：覆盖知识库管理、文本切分、向量化、检索、重排与知识增强问答。
- **🖼️ OCR 文档理解**：支持 PDF、扫描件和图片型文档，将非结构化资料纳入知识处理链路。
- **🔌 MCP 扩展能力**：支持工具、服务与资源接入，为智能体提供统一的外部能力入口。
- **⚙️ 工程化异步处理**：基于 RabbitMQ 解耦 OCR 和向量化任务，支持状态回写、失败恢复与链路自愈。
- **🧷 记忆与上下文管理**：支持长期记忆、上下文组织与多轮对话连续性维护。
- **📈 执行链路可观测**：覆盖 Agent 执行、工具调用、模型使用和文件处理进度，便于定位问题。
- **🚀 全栈一体部署**：提供 Spring Boot 后端、Next.js 前端、PostgreSQL + pgvector、RabbitMQ 和 Docker 部署方案。

## 🧩 核心能力

### 1. Agent 管理

- Agent 创建、编辑、发布、回滚
- 工作区 Agent 组织与安装
- 提示词、工具、知识库统一绑定
- 会话上下文与用户记忆管理

### 2. 知识库与问答

- 文档上传与知识库管理
- OCR、文本抽取、切分与向量化
- 基于 pgvector 的语义检索
- Rerank 增强检索质量
- 基于 RAG 的知识增强问答

### 3. MCP 扩展

- MCP Gateway 接入
- 工具市场与工具管理
- 外部服务能力统一接入
- 为后续复杂任务执行与能力扩展预留标准化接口

### 4. 工程支撑

- RabbitMQ 异步任务编排
- 文件处理进度跟踪
- 失败回写与卡死恢复
- 执行链路追踪与运行监控
- 计费、定时任务与账户能力

## 🔄 典型处理链路

### 文档入库链路

1. 用户上传 PDF、文本或图片文档
2. 系统创建文件记录并进入预处理流程
3. OCR 识别非结构化页面内容
4. 文档被切分为可检索的 `document_unit`
5. embedding 模型生成向量
6. 向量写入数据库并建立索引
7. 文档进入可检索、可问答状态

### 智能问答链路

1. 用户向 Agent 发起问题
2. 系统从知识库检索相关内容
3. 将检索结果组装为问答上下文
4. LLM 基于检索结果生成回答
5. 结合记忆、工具与 MCP 能力进一步增强响应

## 🏗️ 技术栈

- **后端**：Spring Boot、Java
- **前端**：Next.js、TypeScript
- **数据库**：PostgreSQL、pgvector
- **消息队列**：RabbitMQ
- **AI 能力层**：LLM / Embedding / OCR 模型接入
- **扩展协议**：MCP
- **部署方式**：Docker Compose

## 📁 项目结构

- `YuAgent/`：Spring Boot 后端
- `yuagent-frontend-plus/`：Next.js 前端
- `deploy/`：Docker Compose、环境变量和启动脚本
- `docs/`：设计文档、监控文档与 SQL 初始化脚本

## 🌐 默认端口

- 前端：`3000`
- 后端：`8088`
- PostgreSQL：`5432`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`
- Adminer：`8082`
- API Gateway：`8081`

## 🔐 默认账号

- 管理员：`admin@yuagent.ai` / `admin123`
- 测试用户：`test@yuagent.ai` / `test123`

## 🗄️ 数据库连接

默认本地开发配置如下：

- Host：`127.0.0.1`
- Port：`5432`
- Database：`yuagent`
- Username：`yuagent_user`
- Password：`yuagent_pass`

JDBC：

```text
jdbc:postgresql://127.0.0.1:5432/yuagent
```

## 🚀 快速启动

推荐直接使用 Docker Compose：

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

启动后访问：

- 前端：[http://localhost:3000](http://localhost:3000)
- 后端 API：[http://localhost:8088/api](http://localhost:8088/api)
- Adminer：[http://localhost:8082](http://localhost:8082)
- RabbitMQ 管理台：[http://localhost:15672](http://localhost:15672)

Windows 也可以直接使用：

```bat
cd deploy
start.bat
```

## 🐳 Docker 一键部署

YuAgent 已提供适合本地联调和快速演示的 Docker 一键部署入口：

```bat
cd deploy
start.bat
```

或使用：

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

该方式会自动拉起以下服务：

- `yuagent-backend`
- `yuagent-frontend`
- `yuagent-postgres`
- `yuagent-rabbitmq`
- `yuagent-adminer`（开发模式）
- `yuagent-api-gateway`

### 部署前提

- 已安装 Docker Desktop
- 已安装 Docker Compose
- `deploy/.env` 配置可用

### 说明

- 核心服务可以一键拉起
- 如需完整启用“文件上传 + OCR + 向量化”能力，还需正确配置对象存储参数
- 若对象存储未配置完成，上传时可能出现“生成上传凭证失败”等提示

## 🛠️ 运行说明

- 后端服务名：`yuagent-backend`
- 前端服务名：`yuagent-frontend`
- 数据库容器名：`yuagent-postgres`
- RabbitMQ 容器名：`yuagent-rabbitmq`

`deploy/.env` 中的关键配置包括：

- `NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api`
- `DB_NAME=yuagent`
- `DB_USER=yuagent_user`
- `DB_PASSWORD=yuagent_pass`
- `BACKEND_PORT=8088`

## 🎯 适用场景

- 个人知识管理与智能问答
- 文档资料检索、总结与分析
- 私有知识库构建
- 智能体工具接入与能力扩展
- 需要 `OCR + RAG + Agent` 组合能力的 AI 应用与平台项目

## 📚 相关文档

- [部署说明](./deploy/README.md)
- [前端说明](./yuagent-frontend-plus/README.md)
- [数据库初始化脚本](./docs/sql/01_init.sql)
- [Agent 设计文档](./docs/agent_design.md)
- [Token 上下文策略](./docs/token_overflow_strategy.md)
- [执行链路监控需求](./docs/monitoring/agent-execution-trace-requirements.md)
