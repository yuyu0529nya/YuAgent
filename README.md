# YuAgent

YuAgent 是一个面向知识管理与智能体应用的全栈平台，围绕 `LLM + RAG + MCP` 构建，支持文档上传、OCR 识别、向量化检索、智能问答、长期记忆、工具扩展、Agent 管理与执行链路追踪。

它不是一个只会聊天的 Demo，而是一套真正把知识接入、能力扩展、异步处理和平台化管理串起来的智能体系统。

## ✨ 项目亮点

- **🧠 Agent 平台化**：支持 Agent 配置、版本管理、发布与工作区组织，便于按角色和场景复用智能体能力。
- **📚 完整 RAG 链路**：内置知识库管理、文档切分、向量化、检索与问答生成，支持基于私有知识的智能问答。
- **🖼️ OCR 文档理解**：支持 PDF、扫描件、图片型文档识别，将非结构化资料纳入知识处理链路。
- **🔌 MCP 扩展能力**：支持 MCP 工具与服务接入，为智能体连接外部工具、资源和服务提供统一入口。
- **⚙️ 工程化异步处理**：基于 RabbitMQ 解耦 OCR 和向量化任务，支持状态回写、失败恢复与链路自愈。
- **🧷 长对话与记忆管理**：支持上下文组织、长期记忆与对话状态维护，增强连续交互体验。
- **📈 执行链路可观测**：覆盖 Agent 执行过程、工具调用、模型链路与文档处理进度，方便排障与追踪。
- **🚀 全栈可部署**：提供 Spring Boot 后端、Next.js 前端、PostgreSQL + pgvector、RabbitMQ 以及 Docker 一键部署方案。

## 🧩 核心能力

### 1. 智能体管理

- Agent 创建、编辑、发布、回滚
- 工作区 Agent 组织与安装
- 提示词、知识库、工具能力统一绑定
- 会话上下文与用户记忆管理

### 2. 知识库与问答

- 文档上传与知识库管理
- OCR 识别、文本抽取、切分与向量化
- 基于 pgvector 的语义检索
- Rerank 能力增强检索结果质量
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
- 计费、定时任务、账户能力



## 🏗️ 技术栈

- **Backend**: Spring Boot, Java
- **Frontend**: Next.js, TypeScript
- **Database**: PostgreSQL, pgvector
- **Queue**: RabbitMQ
- **AI**: LLM / Embedding / OCR 模型接入
- **Protocol / Extension**: MCP
- **Deployment**: Docker Compose

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

JDBC:

```text
jdbc:postgresql://127.0.0.1:5432/yuagent
```

## 🚀 快速启动

推荐直接使用 Docker Compose。

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

启动后访问：

- 前端：[http://localhost:3000](http://localhost:3000)
- 后端 API：[http://localhost:8088/api](http://localhost:8088/api)
- Adminer：[http://localhost:8082](http://localhost:8082)
- RabbitMQ 管理台：[http://localhost:15672](http://localhost:15672)

Windows 也可以使用：

```bat
cd deploy
start.bat
```



该方式会自动拉起：

- `yuagent-backend`
- `yuagent-frontend`
- `yuagent-postgres`
- `yuagent-rabbitmq`
- `yuagent-adminer`（开发模式）
- `yuagent-api-gateway`

### 一键部署前提

- 已安装 Docker Desktop
- 已安装 Docker Compose
- `deploy/.env` 配置可用

### 需要注意

- 项目基础服务可以一键拉起
- 如果你要完整体验“文件上传 + OCR + 向量化”，还需要正确配置对象存储相关参数，例如 `S3_*` 或 `OSS_*`
- 如果对象存储未配置完成，前端上传时可能提示“生成上传凭证失败”

## 🛠️ 运行说明

- 后端服务名：`yuagent-backend`
- 前端服务名：`yuagent-frontend`
- 数据库容器名：`yuagent-postgres`
- RabbitMQ 容器名：`yuagent-rabbitmq`

当前项目默认通过 `deploy/.env` 提供本地开发配置，关键项包括：

- `NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api`
- `DB_NAME=yuagent`
- `DB_USER=yuagent_user`
- `DB_PASSWORD=yuagent_pass`
- `BACKEND_PORT=8088`

## 🎯 适用场景

- 个人知识管理与智能问答
- 文档资料检索与总结
- 私有知识库构建
- 智能体工具接入与能力扩展
- 需要 OCR + RAG + Agent 组合能力的 AI 应用原型与平台化项目

## 📚 相关文档

- [部署说明](/D:/yuagent/YuAgent/deploy/README.md)
- [前端说明](/D:/yuagent/YuAgent/yuagent-frontend-plus/README.md)
- [数据库初始化脚本](/D:/yuagent/YuAgent/docs/sql/01_init.sql)
- [Agent 设计文档](/D:/yuagent/YuAgent/docs/agent_design.md)
- [Token 上下文策略](/D:/yuagent/YuAgent/docs/token_overflow_strategy.md)
- [执行链路监控需求](/D:/yuagent/YuAgent/docs/monitoring/agent-execution-trace-requirements.md)
