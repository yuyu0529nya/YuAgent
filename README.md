# YuAgent

YuAgent is a full-stack AI agent platform for knowledge management and intelligent interaction, built around `LLM + RAG + MCP`.

It supports document upload, OCR extraction, vector retrieval, knowledge-grounded Q&A, long-term memory, tool extension, agent management, and execution tracing.

Rather than being a simple chat demo, YuAgent connects knowledge ingestion, external capability access, async processing, and platform-level agent orchestration into one cohesive system.

## ✨ Highlights

- **🧠 Agent Platform**: Supports agent configuration, version management, publishing, and workspace organization for reusable agent capabilities.
- **📚 End-to-End RAG Pipeline**: Covers knowledge base management, chunking, embedding, retrieval, rerank, and answer generation over private knowledge.
- **🖼️ OCR-Powered Knowledge Ingestion**: Handles PDFs, scanned files, and image-based documents to bring unstructured content into the retrieval pipeline.
- **🔌 MCP Extension Layer**: Connects tools, services, and resources through MCP to provide a unified extension entry for agents.
- **⚙️ Engineering-Grade Async Workflow**: Uses RabbitMQ to decouple OCR and embedding jobs, with progress tracking, failure recovery, and chain self-healing.
- **🧷 Memory and Context Management**: Supports long-term memory, context organization, and multi-turn conversation continuity.
- **📈 Execution Traceability**: Tracks agent execution flow, tool invocation, model usage, and document-processing progress for debugging and observability.
- **🚀 Full-Stack Deployment**: Includes Spring Boot backend, Next.js frontend, PostgreSQL + pgvector, RabbitMQ, and Docker-based deployment.

## 🧩 Core Capabilities

### 1. Agent Management

- Agent creation, editing, publishing, and rollback
- Workspace-based agent organization and installation
- Unified binding of prompts, tools, and knowledge bases
- Conversation context and user memory management

### 2. Knowledge Base and Q&A

- Document upload and dataset management
- OCR, text extraction, chunking, and embedding
- Semantic retrieval based on pgvector
- Rerank-enhanced retrieval quality
- RAG-based knowledge-grounded answering

### 3. MCP Extensions

- MCP Gateway integration
- Tool marketplace and tool management
- Unified external service access
- Standardized extension interface for future agent capability expansion

### 4. Engineering Support

- RabbitMQ-based async task orchestration
- File processing progress tracking
- Failure write-back and stuck-task recovery
- Execution tracing and runtime monitoring
- Billing, scheduled tasks, and account capabilities

## 🔄 Typical Processing Flows

### Document Ingestion Flow

1. User uploads PDF, text, or image documents
2. System creates file records and starts preprocessing
3. OCR extracts text from non-structured pages
4. Content is split into retrievable `document_unit` chunks
5. Embedding model generates vectors
6. Vectors are stored and indexed
7. Documents become searchable and answerable

### Intelligent Q&A Flow

1. User asks a question to an agent
2. System retrieves relevant knowledge snippets
3. Retrieved context is assembled for the model
4. LLM generates an answer grounded in retrieved knowledge
5. Memory, tools, and MCP-connected capabilities can further enhance the response

## 🏗️ Tech Stack

- **Backend**: Spring Boot, Java
- **Frontend**: Next.js, TypeScript
- **Database**: PostgreSQL, pgvector
- **Queue**: RabbitMQ
- **AI Layer**: LLM / Embedding / OCR model integration
- **Protocol / Extension**: MCP
- **Deployment**: Docker Compose

## 📁 Project Structure

- `YuAgent/`: Spring Boot backend
- `yuagent-frontend-plus/`: Next.js frontend
- `deploy/`: Docker Compose, environment variables, and startup scripts
- `docs/`: design docs, monitoring docs, and SQL initialization scripts

## 🌐 Default Ports

- Frontend: `3000`
- Backend: `8088`
- PostgreSQL: `5432`
- RabbitMQ: `5672`
- RabbitMQ Management UI: `15672`
- Adminer: `8082`
- API Gateway: `8081`

## 🔐 Default Accounts

- Admin: `admin@yuagent.ai` / `admin123`
- Test user: `test@yuagent.ai` / `test123`

## 🗄️ Database Connection

Default local development configuration:

- Host: `127.0.0.1`
- Port: `5432`
- Database: `yuagent`
- Username: `yuagent_user`
- Password: `yuagent_pass`

JDBC:

```text
jdbc:postgresql://127.0.0.1:5432/yuagent
```

## 🚀 Quick Start

Recommended: use Docker Compose directly.

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

After startup:

- Frontend: [http://localhost:3000](http://localhost:3000)
- Backend API: [http://localhost:8088/api](http://localhost:8088/api)
- Adminer: [http://localhost:8082](http://localhost:8082)
- RabbitMQ Management UI: [http://localhost:15672](http://localhost:15672)

For Windows:

```bat
cd deploy
start.bat
```

## 🐳 One-Click Docker Deployment

YuAgent already provides a practical one-click Docker deployment entry for local development and quick demos:

```bat
cd deploy
start.bat
```

Or:

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
```

This will start:

- `yuagent-backend`
- `yuagent-frontend`
- `yuagent-postgres`
- `yuagent-rabbitmq`
- `yuagent-adminer` in dev mode
- `yuagent-api-gateway`

### Requirements

- Docker Desktop installed
- Docker Compose available
- Valid `deploy/.env` configuration

### Notes

- Core services can be started in one command
- To fully enable `file upload + OCR + embedding`, object storage config must be set correctly
- If object storage is not configured, upload may fail with errors like `failed to generate upload credentials`

## 🛠️ Runtime Notes

- Backend service: `yuagent-backend`
- Frontend service: `yuagent-frontend`
- Database container: `yuagent-postgres`
- RabbitMQ container: `yuagent-rabbitmq`

Key values provided by `deploy/.env` include:

- `NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api`
- `DB_NAME=yuagent`
- `DB_USER=yuagent_user`
- `DB_PASSWORD=yuagent_pass`
- `BACKEND_PORT=8088`

## 🎯 Use Cases

- Personal knowledge management and intelligent Q&A
- Document retrieval, summarization, and analysis
- Private knowledge base construction
- Agent tool integration and capability extension
- AI applications requiring `OCR + RAG + Agent` combined workflows

## 📚 Documentation

- [Deployment Guide](./deploy/README.md)
- [Frontend Guide](./yuagent-frontend-plus/README.md)
- [Database Initialization Script](./docs/sql/01_init.sql)
- [Agent Design Doc](./docs/agent_design.md)
- [Token Context Strategy](./docs/token_overflow_strategy.md)
- [Execution Trace Monitoring Requirements](./docs/monitoring/agent-execution-trace-requirements.md)
