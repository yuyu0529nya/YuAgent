# YuAgent 部署说明

本文档以当前 `YuAgent` 仓库的真实配置为准。

## 服务与端口

- `yuagent-frontend`：`3000`
- `yuagent-backend`：`8088`
- `yuagent-postgres`：`5432`
- `yuagent-rabbitmq`：`5672`
- RabbitMQ 管理台：`15672`
- `yuagent-adminer`：`8082`
- `yuagent-api-gateway`：`8081`

## 默认数据库参数

- Database：`yuagent`
- Username：`yuagent_user`
- Password：`yuagent_pass`

## 默认账号

- 管理员：`admin@yuagent.ai` / `admin123`
- 测试用户：`test@yuagent.ai` / `test123`

## 环境文件

当前开发环境直接使用：

- [deploy/.env](/D:/yuagent/YuAgent/deploy/.env)

关键配置如下：

```env
FRONTEND_PORT=3000
BACKEND_PORT=8088
POSTGRES_PORT=5432
DB_NAME=yuagent
DB_USER=yuagent_user
DB_PASSWORD=yuagent_pass
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api
```

## 启动方式

### 方式 1：Docker Compose

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

### 方式 2：Windows 启动脚本

```bat
cd deploy
start.bat
```

### 方式 3：Linux / macOS 启动脚本

```bash
cd deploy
./start.sh
```

## 常用命令

查看状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs --tail 200 yuagent-backend
docker compose logs --tail 200 yuagent-frontend
docker compose logs --tail 100 postgres
```

重建后端：

```bash
docker compose up -d --build yuagent-backend
```

停止服务：

```bash
docker compose down
```

## 数据库连接

宿主机连接：

```text
Host: 127.0.0.1
Port: 5432
Database: yuagent
Username: yuagent_user
Password: yuagent_pass
```

命令行：

```bash
psql -h 127.0.0.1 -p 5432 -U yuagent_user -d yuagent
```

容器内连接：

```bash
docker exec -it yuagent-postgres psql -U yuagent_user -d yuagent
```

## 初始化与缺表说明

`postgres` 服务会把 [docs/sql/01_init.sql](/D:/yuagent/YuAgent/docs/sql/01_init.sql) 挂载到 `/docker-entrypoint-initdb.d/01_init.sql`。

但需要注意：

- 该脚本只会在 PostgreSQL 数据目录首次初始化时执行
- 如果你复用了旧 volume，新增表不会自动补齐

目前项目里已经遇到过老数据卷缺表的情况，典型缺失表包括：

- `memory_items`
- `agent_execution_summary`
- `agent_execution_details`

如果页面提示 `relation does not exist`，优先做这两步：

```bash
docker exec yuagent-postgres psql -U yuagent_user -d yuagent -c "\dt"
docker exec yuagent-postgres psql -U yuagent_user -d yuagent -c "\d memory_items"
```

如果确认是老库缺表，可以手动执行 `01_init.sql` 中对应 DDL，或者清空数据卷后重新初始化。

## 文件上传说明

当前项目的上传链路依赖存储配置。`deploy/.env` 里默认启用了：

```env
FILE_STORAGE_PLATFORM=amazon-s3-1
S3_ENABLED=true
```

如果对象存储配置不完整，前端上传时可能出现“生成上传凭证失败”。排查时优先核对：

- `S3_SECRET_ID`
- `S3_SECRET_KEY`
- `S3_REGION`
- `S3_ENDPOINT`
- `S3_BUCKET_NAME`
- `OSS_*` 兼容配置

## 排障建议

后端接口异常先看：

- [deploy/logs/yu-agent.log](/D:/yuagent/YuAgent/deploy/logs/yu-agent.log)

数据库结构问题先看：

- [docs/sql/01_init.sql](/D:/yuagent/YuAgent/docs/sql/01_init.sql)

如果你正在核对运行中的容器，重点检查：

- `yuagent-backend`
- `yuagent-postgres`
- `yuagent-rabbitmq`
