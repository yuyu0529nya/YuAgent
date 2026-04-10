# YuAgent 部署说明

本文档基于当前 `YuAgent` 仓库的实际部署方式整理，适用于本地开发、联调和快速演示环境。

## ✨ 部署概览

YuAgent 当前采用 Docker Compose 统一拉起核心服务，默认可启动：

- `yuagent-frontend`
- `yuagent-backend`
- `yuagent-postgres`
- `yuagent-rabbitmq`
- `yuagent-adminer`（开发模式）
- `yuagent-api-gateway`

## 🌐 服务与端口

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

## 🗄️ 默认数据库参数

- Database：`yuagent`
- Username：`yuagent_user`
- Password：`yuagent_pass`

## ⚙️ 环境配置

当前开发环境默认读取：

- [`deploy/.env`](./.env)

常用配置项如下：

```env
FRONTEND_PORT=3000
BACKEND_PORT=8088
POSTGRES_PORT=5432
DB_NAME=yuagent
DB_USER=yuagent_user
DB_PASSWORD=yuagent_pass
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api
```

如果你需要完整体验文件上传、OCR 和向量化链路，还需要额外确认对象存储相关配置，例如：

- `FILE_STORAGE_PLATFORM`
- `S3_ENABLED`
- `S3_SECRET_ID`
- `S3_SECRET_KEY`
- `S3_REGION`
- `S3_ENDPOINT`
- `S3_BUCKET_NAME`
- `OSS_*` 兼容配置

## 🚀 启动方式

### 方式 1：Docker Compose

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

### 方式 2：Windows 一键启动脚本

```bat
cd deploy
start.bat
```

### 方式 3：Linux / macOS 启动脚本

```bash
cd deploy
./start.sh
```

## 🔗 启动后访问地址

- 前端：[http://localhost:3000](http://localhost:3000)
- 后端 API：[http://localhost:8088/api](http://localhost:8088/api)
- Adminer：[http://localhost:8082](http://localhost:8082)
- RabbitMQ 管理台：[http://localhost:15672](http://localhost:15672)
- API Gateway：[http://localhost:8081](http://localhost:8081)

## 🛠️ 常用命令

查看容器状态：

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

## 🧪 数据库连接

宿主机连接：

```text
Host: 127.0.0.1
Port: 5432
Database: yuagent
Username: yuagent_user
Password: yuagent_pass
```

命令行连接：

```bash
psql -h 127.0.0.1 -p 5432 -U yuagent_user -d yuagent
```

容器内连接：

```bash
docker exec -it yuagent-postgres psql -U yuagent_user -d yuagent
```

## 📦 文件上传说明

当前项目的上传链路依赖对象存储配置。

如果对象存储配置不完整，前端上传时可能出现如下问题：

- 生成上传凭证失败
- 文件上传后无法进入后续 OCR / 向量化链路

排查时优先确认：

- 对象存储密钥是否正确
- Endpoint 和 Bucket 是否可用
- 后端环境变量是否已注入到容器

## 🔍 排障建议

后端接口异常优先查看：

- [`deploy/logs/yu-agent.log`](./logs/yu-agent.log)

数据库结构与初始化脚本查看：

- [`docs/sql/01_init.sql`](../docs/sql/01_init.sql)

如果你正在核对运行中的容器，优先检查：

- `yuagent-backend`
- `yuagent-postgres`
- `yuagent-rabbitmq`

## 📚 相关文档

- [项目首页](../README.md)
- [前端说明](../yuagent-frontend-plus/README.md)
- [数据库初始化脚本](../docs/sql/01_init.sql)
