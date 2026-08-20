# LLM Connector Gateway

An enterprise-focused **AI gateway** that lets organizations run and integrate open-source LLMs **locally** via [llama.cpp](https://github.com/ggml-org/llama.cpp), as a self-hosted alternative to paid AI model APIs. It combines a Java/Spring Boot backend with a React admin/playground UI so teams can configure models once, test them interactively, and expose them to internal applications through a stable Agent API.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Getting Started](#getting-started)
- [API Overview](#api-overview)
- [Default Admin Access](#default-admin-access)
- [Roadmap Ideas](#roadmap-ideas)
- [Contributing](#contributing)
- [License](#license)

## Overview

LLM Connector Gateway manages the full lifecycle of self-hosted, open-source models: starting them via `llama.cpp` on application boot, exposing chat/embedding/vision endpoints for them, letting developers experiment in a **Playground**, and packaging a tested configuration into a reusable **AI Agent** that any enterprise application can call by ID. It also ships with role-based access control, group/user management, and an Admin Panel for centralized governance.

The goal is to give enterprise developers greater control, privacy, and cost efficiency: with a one-time investment in GPU infrastructure, organizations can run and reuse AI capabilities across many internal applications instead of paying per-request for third-party APIs.

## Key Features

- **Local model execution via llama.cpp** — models are launched automatically as OS processes on application startup, based on YAML configuration (model path, port, context size, GPU usage, parallelism, multimodal projector, etc).
- **Multi-source model registry** — configure both `free` (self-hosted/local) and `paid` (e.g. OpenAI) model sources side by side, each tagged with the capabilities it supports (`chat`, `agent`, `rag`, `tool`, `summarization`, `generation`, `code`, `vision`, `classification`, `embedding`).
- **Interactive Playground** — pick a running model and tune parameters (temperature, system instructions), configure RAG (vector store, embedding model, document chunking strategy), and optionally enable internet search — all before committing to a configuration.
- **RAG & Vector Stores** — multiple in-memory vector store implementations, versioned RAG services (v1–v4), and configurable ingestion/chunking strategies for document-grounded responses.
- **Multimodal / Vision workflows** — image and PDF classification, information extraction, and structured data extraction from images and image-based PDFs.
- **AI Agents** — save a tested Playground configuration as an Agent; each Agent gets a unique Agent ID and a persisted configuration that can be invoked from other applications via the API.
- **Web search integration** — optional web search tooling (e.g. SearXNG) to augment model responses with live internet results.
- **Role-based access control (RBAC)** — users, roles, and groups, with a default `Admin` group and `Super Admin` / `System Admin` roles preconfigured out of the box.
- **Admin Panel** — centralized management of users, groups, roles, agents, and system-level configuration, restricted to authorized administrators.
- **Usage & consumption stats** — token/consumption tracking endpoints for visibility into model usage.
- **JWT-based authentication** with access/refresh tokens, and a resilience layer (Resilience4j circuit breakers) around model calls.

## Architecture

```
┌─────────────────────┐        HTTPS / REST         ┌──────────────────────────────┐
│   llm-ui (React)     │ ───────────────────────────▶ │  llm-connector-gateway (API)  │
│  Playground / Agents │ ◀─────────────────────────── │  Spring Boot backend          │
│  Admin / Stats        │                              │                               │
└─────────────────────┘                              └───────────────┬───────────────┘
                                                                      │ spawns & proxies to
                                                                      ▼
                                                       ┌───────────────────────────────┐
                                                       │  llama.cpp model servers        │
                                                       │  (chat / vision / embedding)    │
                                                       │  one process per configured     │
                                                       │  model, on its own port         │
                                                       └───────────────────────────────┘
                                                                      │
                                                                      ▼
                                                       ┌───────────────────────────────┐
                                                       │  PostgreSQL (users, roles,     │
                                                       │  groups, agents, stats)        │
                                                       └───────────────────────────────┘
```

On startup, the backend reads the model registry from YAML, and for every model marked `active: true` it launches a corresponding `llama.cpp` server process (using a vision-specific, embedding-specific, or standard chat script depending on the model's declared capabilities) on its configured port. The gateway then routes API traffic to the right local model process, or to a paid provider (e.g. OpenAI) when configured.

## Tech Stack

**Backend**
- Java 21, Spring Boot (Spring MVC, WebSocket, Actuator, Validation, Security, Data JPA)
- [Spring AI](https://spring.io/projects/spring-ai) (chat model abstraction, vector store abstraction, OpenAI-compatible client, Tika document reader)
- PostgreSQL (via Spring Data JPA / Hibernate)
- Spring Cloud + Resilience4j (circuit breakers)
- JWT auth (`jjwt`), Lombok, MapStruct
- Apache PDFBox and OpenCV (document/image preprocessing for vision workflows)
- Jsoup (web page download/content extraction for the web-search tool)
- Maven build (`mvnw` wrapper included)

**Frontend (`llm-ui`)**
- React 19 + Vite
- React Router 7
- Tailwind CSS 4
- Axios for API calls
- Recharts (stats/usage charts), React Markdown, React Syntax Highlighter
- Served in production via Nginx (multi-stage Docker build)

## Project Structure

```
llm-connector-gateway/
├── src/main/java/com/an/llm/connector/gateway/
│   ├── config/            # App & LLM startup configuration (LlmLocalStartConfig spawns llama.cpp processes)
│   ├── connector/         # HTTP client wrapper used to talk to model servers
│   ├── controller/
│   │   ├── ai/             # Chat, RAG (v1–v4), Embedding (v1–v2), Vision (v1–v2), LLM config controllers
│   │   ├── agent/           # Agent, agent configuration, agent file controllers
│   │   ├── user/            # User, role, group controllers
│   │   ├── stats/           # Token/consumption stats
│   │   ├── web/             # Web search controller
│   │   └── ui/              # UI-serving controller
│   ├── service/
│   │   ├── ai/              # AiService, RAG services, embedding services, vision services
│   │   ├── agent/           # Agent persistence & invocation logic
│   │   ├── classification/  # Image/PDF classification logic
│   │   ├── factory/         # AiBeanFactory, VectorStoreBeanFactory (build model/vector-store beans from config)
│   │   ├── tokenize/        # Token counting, chunking, context-budget & history trimming
│   │   ├── stats/           # Consumption statistics
│   │   ├── user/            # User/role/group management
│   │   └── web/             # Web search, page download & HTML extraction
│   ├── security/           # JWT auth, filters, security config
│   ├── entity/, repository/, dto/, mapper/  # JPA entities, repositories, DTOs, MapStruct mappers
│   ├── model/config/       # YAML-bound model/source configuration classes
│   ├── enums/              # LlmCapability, Source, ChatRole, IngestionMode, ClassificationMode, etc.
│   └── Application.java    # Spring Boot entry point
├── src/main/resources/
│   ├── application.yaml          # Active profile selector
│   ├── application-local.yaml    # Local dev config: DB, mail, JWT secrets, model registry, default admin
│   └── application-dev.yaml      # Dev profile config
├── postman/                # Postman collections/specs for the API
└── llm-ui/                 # React frontend (Playground, Agents, Admin, Stats, Settings)
    ├── src/pages/           # Home, Playground, Agents, Admin, Stats, Settings, Login
    ├── src/components/, services/, routers/, context/, configs/, utils/
    ├── Dockerfile           # Multi-stage build (Vite build → Nginx)
    └── nginx.conf
```

## Prerequisites

- **Java 21** and Maven (or use the bundled `./mvnw`)
- **Node.js 20+** and npm (for `llm-ui`)
- **PostgreSQL** instance
- **[llama.cpp](https://github.com/ggml-org/llama.cpp)** built and available on the host (the backend shells out to it to launch model servers)
- Downloaded **GGUF model weights** for whichever models you intend to run (chat, vision, embedding, etc.)
- (Optional) GPU + drivers if running models with `gpu: true`
- (Optional) A [SearXNG](https://github.com/searxng/searxng) instance if you want to enable web search
- (Optional) An OpenAI API key if you want to enable the `paid` model source

## Configuration

Configuration lives under `src/main/resources`:

- `application.yaml` — selects the active Spring profile (`local` by default).
- `application-local.yaml` / `application-dev.yaml` — per-environment settings, including:
  - `server` — port and virtual thread settings.
  - `spring.datasource` — PostgreSQL connection details.
  - `spring.mail` — SMTP settings (e.g. for notifications/invites).
  - `app.security` — JWT signing secret and token expiration settings.
  - `llm.sources.free.models[]` — the local model registry. Each entry defines: `id`, `type` (one or more capabilities), `base-url`, `api-path`, `api-key`, `port`, `model-name` (GGUF file), `mm-proj` (multimodal projector, for vision models), `context` size, `parallelExecution`, `gpu`, and `active` (whether it should be auto-started on boot).
  - `llm.sources.paid.models[]` — paid provider models (e.g. OpenAI `gpt-4o`, `text-embedding-3-large`).
  - `admin.user` — the bootstrap administrator account (name, credentials, roles, groups) created on first startup.

> **Security note:** The sample `application-local.yaml` in this repo is meant for local development only. Before deploying anywhere shared, replace the datasource credentials, mail credentials, JWT signing secret, model `api-key` values, and default admin password with your own secrets — ideally injected via environment variables rather than committed to source control.

## Getting Started

### 1. Backend

```bash
cd llm-connector-gateway

# Configure PostgreSQL connection, JWT secret, mail, and model registry
# in src/main/resources/application-local.yaml (or add your own profile)

# Ensure llama.cpp is installed and any GGUF models referenced in the
# model registry are downloaded and reachable at the configured paths.

./mvnw spring-boot:run
```

On startup, the gateway will:
1. Connect to PostgreSQL and run schema updates (`ddl-auto: update`).
2. Create the default `Admin` group, `Super Admin` / `System Admin` roles, and the bootstrap admin user if they don't already exist.
3. Launch a `llama.cpp` server process for every model marked `active: true` in the model registry, on its configured port.

### 2. Frontend

```bash
cd llm-connector-gateway/llm-ui
npm install
npm run dev
```

The UI is served under the `/ui` base path and expects the backend API to be reachable (see `src/configs` / `src/services` for the API base URL).

For a production build:

```bash
npm run build
```

or build the Docker image directly:

```bash
docker build -t llm-connector-ui ./llm-ui
```

### 3. Bundled UI (served directly by the backend)

The backend also ships with a pre-built copy of the UI at `src/main/resources/static/ui` (the `npm run build` output already copied into Spring's static resources folder). `UiController` forwards `/`, `/ui`, and any `/ui/**` client-side route to `static/ui/index.html`, so **once the Spring Boot app is running, you can open it straight in the browser — no separate `npm run dev` needed**:

```
http://localhost:6969/        # redirects to /ui
http://localhost:6969/ui      # serves the bundled React app
```

Use `npm run dev` (step 2) only when you're actively developing the frontend and want hot-reload; for normal day-to-day use, running the backend alone is enough since it already serves the compiled UI. If you make frontend changes, rebuild (`npm run build`) and copy the new `dist/` output into `src/main/resources/static/ui` to update the bundled version.

## API Overview

All backend endpoints are namespaced under `api/llm/{version}`. Highlights:

| Area | Base Path | Purpose |
|---|---|---|
| Chat | `api/llm/v2` | Send chat completions to a configured model |
| AI (misc) | `api/llm/v1` | Core AI operations |
| RAG | `api/llm/v1/rag` … `api/llm/v4/rag` | Retrieval-augmented generation (multiple iterations) |
| Embeddings | `api/llm/v1/embed`, `api/llm/v2/embed` | Generate embeddings |
| Vision | `api/llm/v1/vl`, `api/llm/v2/vl` | Image/PDF classification & extraction |
| LLM Config | `api/llm/v1/config` | Inspect/manage the model registry |
| Agents | `api/llm/v1/agent`, `api/llm/v1/agent-config`, `api/llm/v1/agent-file` | Create, configure, and invoke saved AI Agents |
| Users / Roles / Groups | `api/llm/v1/users`, `api/llm/v1/roles`, `api/llm/v1/groups` | RBAC management |
| Stats | `api/llm/v1/stats` | Token/usage consumption statistics |
| Web Search | `api/llm/v1/web` | Internet search tool used by RAG/chat |

A Postman collection is included under `postman/` for exploring and testing the full API surface.

## Default Admin Access

A default administrator account is provisioned on first startup from the `admin.user` section of the active configuration profile (name, username, email, password, and assigned roles/groups). **Change this password immediately** after first login, and avoid keeping real credentials in a checked-in YAML file — prefer environment variable overrides in any non-local environment.

## Roadmap Ideas

- Persistent (on-disk) vector store options alongside the current in-memory stores
- Additional model providers beyond OpenAI-compatible endpoints
- Containerized backend (Dockerfile / docker-compose) alongside the existing UI Dockerfile
- Fine-grained, per-agent access control

## Contributing

Issues and pull requests are welcome. Please open an issue to discuss significant changes before submitting a PR.

## License

*No license file is currently included in this repository. Add a `LICENSE` file to clarify usage rights before publishing or accepting external contributions.*
