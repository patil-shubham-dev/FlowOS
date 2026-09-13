<p align="center">
  <img src="docs/assets/flowos-banner.png" alt="FlowOS" width="600" />
</p>

<h3 align="center">Open-source, local-first, AI-native personal productivity OS</h3>

<p align="center">
  <a href="#quick-start">Quick Start</a> ·
  <a href="#features">Features</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#providers">Providers</a> ·
  <a href="#contributing">Contributing</a> ·
  <a href="#license">License</a>
</p>

<p align="center">
  <a href="https://github.com/patil-shubham-dev/FlowOS/actions"><img src="https://github.com/patil-shubham-dev/FlowOS/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/node-%3E%3D20-blue.svg" alt="Node.js" />
  <img src="https://img.shields.io/badge/typescript-5.5+-blue.svg" alt="TypeScript" />
</p>

---

## What is FlowOS?

FlowOS is a **self-hosted productivity workspace** where a context-aware AI agent can see and act on your tasks, calendar, and notes — with every action transparent, reversible, and auditable.

Unlike chatbot-with-a-calendar attached, FlowOS is a **productivity OS with an agent layer**. The agent calls the same services the UI uses. No shortcuts, no separate AI database.

### Key principles

- **Agent-as-a-layer** — the AI calls the same `TaskService`, `EventService`, `NoteService` that power the UI. No magic backdoor.
- **Scoped context** — the agent only sees what you're looking at. When you're on the Tasks page, it sees your filtered tasks. When you're on Calendar, it sees your visible events.
- **Transparent actions** — every agent action is logged, undoable, and requires confirmation for risky operations.
- **Local-first** — your data lives in a local SQLite database. No mandatory cloud.
- **Provider-agnostic** — works with Anthropic, Nvidia NIM, OpenAI, Ollama, LM Studio, OpenRouter, Google, or any OpenAI-compatible endpoint.

---

## Quick Start

### Prerequisites

- **Node.js >= 20**
- **npm** (or pnpm/yarn)
- An AI provider API key (Anthropic, Nvidia, OpenAI, etc.)

### Install & Run

```bash
# Clone the repo
git clone https://github.com/patil-shubham-dev/FlowOS.git
cd flowos

# Install dependencies
npm install

# Build the server
npm run build:server

# Start the backend (serves API + static files)
npm start
```

Open **http://localhost:3001** in your browser.

### Development

```bash
# Run Vite dev server (hot reload for frontend)
npm run dev:web

# Run tests
npm test

# Type-check all code
npm run typecheck
```

### Available Scripts

| Script | Description |
|--------|-------------|
| `npm start` | Build & run production server on port 3001 |
| `npm run dev:web` | Vite dev server with HMR |
| `npm run build` | Full production build (server + web) |
| `npm test` | Run test suite (vitest) |
| `npm run test:watch` | Run tests in watch mode |
| `npm run typecheck` | Type-check all TypeScript |
| `npm run typecheck:web` | Type-check frontend only |

---

## Features

### Home — AI Hub

- Conversational chat with your AI agent
- Quick-action pills for common tasks
- Ambient cards showing workspace summary (open tasks, today's events, note count)
- Full undo support for agent actions

### Tasks

- Create, edit, delete, and filter tasks
- Status tracking (todo, doing, done)
- Priority levels (low, normal, urgent)
- Tags and estimated minutes
- Board and list views

### Calendar

- Day and week views
- Flexible vs fixed events (agent can reschedule flexible ones)
- Conflict detection
- Free time finder
- Linked tasks

### Notes

- Markdown notes with tags
- Task linking
- Full-text search (FTS5)
- Inline editing

### Settings

- **AI Providers** — add, validate, and manage provider configs
- **Memory** — view and edit learned facts
- **Permissions** — override tool risk tiers (auto/confirm/blocked)
- **Action History** — full audit log with undo

---

## Architecture

```
src/
  agent/          # Agent loop, planner, risk classifier
  model/          # Type definitions
  providers/      # AI provider adapters (Anthropic, OpenAI-compat, etc.)
  services/       # Task, Event, Note, Provider, Preference services
  tools/          # 20 tool definitions + executor
  undo/           # Undo engine with inverse snapshots
  web/            # React 19 frontend (SPA)
    components/   # ChatInput, ChatThread, TopBar, AmbientCards, etc.
    pages/        # Home, Tasks, Calendar, Notes, Settings
    hooks/        # useAgentTurn orchestration
```

### Backend contract

The server exposes a stdlib-only `node:http` API (no frameworks):

- `POST /api/agent/turn` — run an agent turn (planner → tools → execution)
- `GET /api/overview` — workspace summary
- CRUD endpoints for tasks, events, notes, providers, preferences, permissions
- `GET /api/log` — action history
- `POST /api/undo/:id` — undo an action

### Agent flow

```
User input → Planner (LLM) → Tool calls → Risk classifier → Executor → Service layer → SQLite
                                    ↑                                              ↓
                                    └── Verification ←── Audit log ←── Undo snapshots
```

Every mutating tool:
1. Computes risk tier (auto / confirm / blocked)
2. Resolves scope (which items are affected)
3. Takes a snapshot for undo
4. Executes through the service layer
5. Records to the audit log

---

## Providers

FlowOS supports any OpenAI-compatible API. Currently tested:

| Provider | Type | Notes |
|----------|------|-------|
| **Anthropic** | `anthropic` | Claude models via Messages API |
| **Nvidia NIM** | `nvidia` | OpenAI-compatible, `openai/gpt-oss-20b` default |
| **OpenAI** | `openai` | GPT models via Chat Completions |
| **Ollama** | `ollama` | Local models, no API key needed |
| **LM Studio** | `lm_studio` | Local models, no API key needed |
| **OpenRouter** | `openrouter` | Multi-model gateway |
| **Google** | `google` | Gemini models |
| **Custom** | `custom_openai_compatible` | Any OpenAI-compatible endpoint |

### Adding a provider

1. Go to **Settings > AI Providers**
2. Click **+ Add provider**
3. Select type, enter model name and API key
4. Click **Validate** to test the connection
5. Set as default if needed

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite, TypeScript |
| Styling | Plain CSS (design tokens, no framework) |
| Backend | Node.js stdlib (`node:http`), no frameworks |
| Database | SQLite via `better-sqlite3`, FTS5 for search |
| AI SDK | `@anthropic-ai/sdk` + custom OpenAI-compat adapter |
| Testing | Vitest, 121 tests |
| Build | TypeScript compiler + Vite |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:

- Setting up the development environment
- Code style and conventions
- Pull request process
- Testing requirements

---

## Roadmap

### MVP (current)
- Home, Tasks, Calendar, Notes, Settings
- Full agent loop with scoped context
- 2+ provider support
- Undo and audit logging

### v1
- Habits, Goals, Projects modules
- Broader provider support
- Richer memory system

### v2
- Multi-device sync
- Offline queue
- Calendar import/export
- Mobile client

See [docs/22-roadmap.md](docs/22-roadmap.md) for full details.

---

## Documentation

The `docs/` folder contains 24 spec documents covering every aspect of the system:

- [01 — North Star & Non-Goals](docs/01-north-star-and-non-goals.md)
- [02 — Product Vision](docs/02-product-vision.md)
- [03 — PRD](docs/03-prd.md)
- [04 — MVP Specification](docs/04-mvp-specification.md)
- [14 — Architecture Overview](docs/14-architecture-overview.md)
- [09 — Agent Architecture](docs/09-agent-architecture-spec.md)
- [10 — Tool Specification](docs/10-agent-tool-specification.md)
- [11 — Permission Model](docs/11-permission-confirmation-model.md)
- [19 — Security & Privacy](docs/19-security-privacy-model.md)
- [22 — Roadmap](docs/22-roadmap.md)

---

## License

[MIT](LICENSE)

---

## Acknowledgments

Built with care. Designed for trust.
