# YuAgent

YuAgent 是一个基于 LLM、MCP 和 RAG 的智能 Agent 平台，包含前端、后端、PostgreSQL、RabbitMQ 以及可选的 Adminer / API Gateway。


## 项目结构

- `YuAgent/`：Spring Boot 后端
- `yuagent-frontend-plus/`：Next.js 前端
- `deploy/`：Docker Compose、环境变量和启动脚本
- `docs/`：设计文档与 SQL 初始化脚本

## 默认端口

- 前端：`3000`
- 后端：`8088`
- PostgreSQL：`5432`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`
- Adminer：`8082`
- API Gateway：`8081`

## 默认账号

- 管理员：`admin@yuagent.ai` / `admin123`
- 测试用户：`test@yuagent.ai` / `test123`

## 数据库连接

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

## 快速启动

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

## 运行说明

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

## 已知初始化注意事项

如果你以前已经启动过旧版本容器，PostgreSQL 数据卷里可能缺少后续新增的表。近期已经确认过以下表在老数据卷中可能缺失：

- `memory_items`
- `agent_execution_summary`
- `agent_execution_details`

这类问题的根因通常是：

- `docs/sql/01_init.sql` 只会在数据库首次初始化时自动执行
- 旧的 Docker volume 不会因为脚本更新而自动补表

如果再次遇到“relation does not exist”，优先检查当前 volume 是否为老库，再决定补表或重建数据卷。

## 功能概览

- Agent 管理与发布
- 会话与上下文管理
- MCP 工具与工具市场
- RAG 知识库、OCR、向量化
- 长期记忆
- 计费与账户
- 定时任务
- Agent 执行链路监控

## 相关文档

- [部署说明](D:/yuagent/YuAgent/deploy/README.md)
- [前端说明](D:/yuagent/YuAgent/yuagent-frontend-plus/README.md)
- [数据库初始化脚本](D:/yuagent/YuAgent/docs/sql/01_init.sql)
