# YuAgent Frontend Plus

`yuagent-frontend-plus` 是 YuAgent 的前端项目，基于 Next.js 15、React 19、TypeScript 和 Tailwind CSS。

## 本地开发

```bash
npm install
npm run dev
```

默认开发地址：

- 前端：[http://localhost:3000](http://localhost:3000)
- 后端 API：[http://localhost:8088/api](http://localhost:8088/api)

## 主要技术栈

- Next.js 15
- React 19
- TypeScript
- Tailwind CSS
- Radix UI
- Sonner

## 环境依赖

前端最关键的运行参数是：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api
```

当前 Docker Compose 也是按这个地址注入的，所以前端文档、容器配置和实际项目保持一致。

## 与当前项目的对应关系

- 前端目录名：`yuagent-frontend-plus`
- 默认 API 前缀：`/api`
- 后端默认端口：`8088`
- 上传、记忆管理、RAG、Agent 工作台等页面都依赖后端接口和数据库结构完整

## 调试建议

如果前端页面出现“服务器内部错误”或 Toast 报错：

1. 先看浏览器请求是否命中了 `http://localhost:8088/api`
2. 再看后端日志 [yu-agent.log](/D:/yuagent/YuAgent/deploy/logs/yu-agent.log)
3. 如果报 `relation does not exist`，优先检查数据库是否缺表

## 说明

当前这个前端项目已经对接 `YuAgent` 命名与服务配置，不再使用 `AgentX` 的项目名或默认接口地址。
