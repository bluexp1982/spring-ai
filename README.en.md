# spring-ai

English | [中文](README.md)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-6DB33F.svg)

A sample SaaS subscription & order management system built on Spring Boot 4 and Spring AI 2, consisting of a **backend API service** and an **admin frontend**.

## Overview

This project is a complete sample of a SaaS subscription and order management system covering plans, subscriptions, orders, invoices and coupons. The backend runs on Spring Boot 4.1 + Spring AI 2.0 + PostgreSQL 18, the frontend on React 19 + TypeScript + Ant Design. AI is a first-class capability throughout: switchable dual chat models, SSE streaming chat, PostgreSQL-backed short-term memory, Milvus vector retrieval QA, and MCP as well as business tool calling.

## Features

- **AI assistant panel**: SSE streaming output with stop-generation and clear-conversation support, opened from a floating bubble at the bottom-right of the page.
- **Hot-swappable dual models**: switch between Qwen / DeepSeek with a single environment variable — no code changes or repackaging.
- **RAG knowledge base**: ingest text or files → automatic splitting → vectorization → retrieval-augmented answers with cited sources.
- **Two-layer conversation memory**: short-term memory persisted to PostgreSQL, long-term memory isolated per signed-in user as markdown files.
- **Tool calling**: four categories of tools exposed to the model — external MCP tools, read-only business queries, Agent Skills and long-term memory.
- **Complete business loop**: CRUD and paginated query for plans / subscriptions / orders / invoices / coupons.
- **Production-ready plumbing**: JWT authentication, unified response envelope, OpenAPI docs and a one-command start script.

## Getting Started

### Prerequisites

| Dependency | Version | Purpose |
| --- | --- | --- |
| JDK | 21 | Backend runtime |
| Node.js | 22+ | Frontend build; also used to spawn the MCP Server subprocess |
| pnpm | 10+ | Frontend package manager (`npm i -g pnpm`) |
| PostgreSQL | 18+ | Business database + short-term chat memory |
| Milvus | 2.6+ | Vector store for the RAG knowledge base, connects to `localhost:19530` by default (optional if you don't use RAG) |

### Configure Environment Variables

All secrets and connection settings are injected through environment variables; the repository ships no credentials. See [.env.example](.env.example) for a sample (this project does not auto-load `.env` — it reads process environment variables):

| Variable | Required | Description |
| --- | --- | --- |
| `BAILIAN_API_KEY` | Yes | Alibaba Cloud Bailian API key, **shared by chat and embeddings**; requests return 401 without it |
| `DB_PASSWORD` | Yes | PostgreSQL password (`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` are optional) |
| `BASE_URL` | No | Bailian's OpenAI-compatible endpoint, defaults to the public URL; override it when using a dedicated workspace |
| `AMAP_MAPS_API_KEY` | No | Key for the AMap MCP Server; map-related tools are unavailable without it |
| `DEEPSEEK_API_KEY` | No | Only required when `CHAT_PROVIDER=deepseek` |
| `JWT_SECRET` | Recommended | JWT signing key (≥32 bytes); a development default is used when unset, **must be overridden in production** |
| `MILVUS_HOST` / `MILVUS_PORT` | No | Defaults to `localhost:19530` |

```bash
export BAILIAN_API_KEY=sk-your-bailian-key
export DB_PASSWORD=your-db-password
export JWT_SECRET="$(openssl rand -base64 32)"
```

### Initialize the Database

```bash
createdb spring-ai

# Create the update_updated_at function first (several tables depend on it)
psql -d spring-ai -c "
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS \$\$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
\$\$ LANGUAGE plpgsql;
"

psql -d spring-ai -f db/sys_user.sql
psql -d spring-ai -f db/saas_subscription_order.sql

# Optional: load development sample data
psql -d spring-ai -f db/sample_data/sys_user_sample_data.sql
psql -d spring-ai -f db/sample_data/saas_subscription_order_sample_data.sql
```

> If PostgreSQL requires a password, use `PGPASSWORD=$DB_PASSWORD psql -U postgres -d spring-ai -f ...`.
> The `SPRING_AI_CHAT_MEMORY` table is created automatically by Spring AI at startup — no manual DDL needed.

### Run

```bash
# Install frontend dependencies before the first run
cd frontend && pnpm install && cd ..

# Free ports 8080 / 5173, then start both backend and frontend
./scripts/run.sh
```

The backend listens on `http://localhost:8080` and the frontend on `http://localhost:5173` (Vite proxies `/api/*` automatically). Sign in with the default admin account `admin / admin123` (**local demo only — change it before deploying**).

`./scripts/run.sh` is a bash script for macOS / Linux. On Windows, run the two parts separately:

```bash
./mvnw spring-boot:run
cd frontend && pnpm dev
```

## Documentation

All detailed documents are written in Chinese.

| Document | Contents |
| --- | --- |
| [docs/ai.md](docs/ai.md) | Model switching, conversation memory, RAG knowledge base, MCP / Agent Skills / long-term memory |
| [docs/api.md](docs/api.md) | API reference, pagination query parameters, Swagger, controller annotation examples |
| [docs/deployment.md](docs/deployment.md) | Packaging, Nginx, systemd, configuration cheat sheet |
| [docs/development.md](docs/development.md) | Tests, frontend structure, routing and auth flow, AI panel implementation |
| [docs/chat-model-switch.md](docs/chat-model-switch.md) | Full Qwen ⇄ DeepSeek switching steps and verification checklist |

## Project Structure

```text
.
├── src/main/java/com/example/springai/
│   ├── ai/       # Chat, RAG knowledge base, long-term memory
│   ├── auth/     # JWT authentication
│   ├── biz/      # SaaS business (controller / service / entity / mapper / tools)
│   ├── common/   # Unified response envelope, enums, utilities
│   └── config/   # Security, OpenAPI, skill and memory wiring
├── src/main/resources/
│   ├── application.yaml   # Main configuration (includes the MCP Server list)
│   └── .agents/skills/    # Agent Skills (one SKILL.md per skill)
├── data/memory/           # Long-term memory (generated at runtime, isolated per user, not committed)
├── frontend/              # Admin frontend (React + Vite + Ant Design)
├── db/                    # DDL scripts and sample data
├── docs/                  # Detailed documentation
└── scripts/run.sh         # One-command frontend + backend launcher
```

## Tech Stack

- **Backend**: Java 21 / Spring Boot 4.1 / Spring AI 2.0 (JDBC Chat Memory, MCP Client, Milvus Vector Store, Agent Utils) / Spring Security 7 / MyBatis-Plus 3.5 / PostgreSQL 18 / JJWT
- **Frontend**: React 19 / TypeScript 5.9 / Vite 8 / Ant Design 6 / Pro Components 3.1 / React Router 7

## Contributing

Issues and pull requests are welcome. Once you have the project running locally, please keep each change scoped to a single module and describe how you verified it in the PR description.

> **Never** commit `.env`, database passwords, API keys or real memory files under `data/memory/` — these paths are already ignored in `.gitignore`.

## License

Released under the [MIT License](LICENSE).
