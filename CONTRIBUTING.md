# Contributing to FlowOS

Thanks for your interest in contributing! This document covers everything you need to get started.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)

---

## Code of Conduct

This project follows our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/your-username/flowos.git
   cd flowos
   ```
3. **Install** dependencies:
   ```bash
   npm install
   ```
4. **Create a branch** for your change:
   ```bash
   git checkout -b feat/my-feature
   ```

---

## Development Setup

### Prerequisites

- Node.js >= 20
- npm

### Running in development

```bash
# Build the server (required once, or after server changes)
npm run build:server

# Start the backend server
npm start

# In another terminal, run Vite dev server for frontend hot-reload
npm run dev:web
```

The backend serves on `http://localhost:3001`. The Vite dev server runs on `http://localhost:5173` and proxies API requests to the backend.

### Useful commands

| Command | What it does |
|---------|-------------|
| `npm test` | Run the full test suite |
| `npm run test:watch` | Run tests in watch mode |
| `npm run typecheck` | Type-check all TypeScript |
| `npm run build` | Full production build |

---

## Project Structure

```
src/
  agent/          # Agent loop, planner, risk classifier
  model/          # TypeScript type definitions
  providers/      # AI provider adapters
  services/       # Business logic (Task, Event, Note, etc.)
  tools/          # 20 tool definitions + executor
  undo/           # Undo engine with inverse snapshots
  web/            # React 19 frontend
    components/   # Reusable UI components
    pages/        # Page-level components (Home, Tasks, etc.)
    hooks/        # Custom React hooks
docs/             # 24 design/spec documents
tests/            # Test files
```

### Key architectural rules

- **Agent → Tools → Service Layer only** — the agent never touches the database directly
- **Scoped context** — the agent only sees what the user is looking at
- **Every mutating tool ships an inverse/snapshot** for undo
- **Risk tiers** — tools are classified as auto/confirm/blocked based on impact
- **No external dependencies for the server** — stdlib `node:http` only

---

## Code Style

### TypeScript

- Strict mode enabled
- `exactOptionalPropertyTypes: true` — use `null` not `undefined` for optional fields
- Prefer `interface` for object shapes, `type` for unions/intersections
- No `any` — use `unknown` and narrow

### React

- Functional components only
- Hooks for state and side effects
- Props interfaces defined inline or exported with the component

### CSS

- Plain CSS with design tokens (CSS custom properties)
- No utility frameworks — semantic class names
- Mobile-first responsive design
- Support `prefers-reduced-motion`

### General

- No comments unless explaining *why*, not *what*
- No dead code, no commented-out code
- Handle errors explicitly — no empty `catch` blocks
- Validate inputs at trust boundaries

---

## Testing

### Running tests

```bash
npm test              # Single run
npm run test:watch    # Watch mode
```

### Writing tests

- Tests live in `tests/` mirroring `src/` structure
- Use `describe` blocks for grouping, `it` for individual cases
- Test both success and error paths
- Mock external dependencies (providers, filesystem)
- Aim for meaningful coverage, not 100% line coverage

### What to test

- Service layer logic (CRUD operations, validation)
- Tool executor (risk classification, scope resolution)
- Agent planner (tool call generation, malformed input recovery)
- Frontend components (user interactions, state changes)

---

## Pull Request Process

### Before submitting

1. **Run the full test suite**: `npm test`
2. **Type-check**: `npm run typecheck`
3. **Build**: `npm run build`
4. **Self-review** your diff — would you approve this as a reviewer?

### PR guidelines

- **One concern per PR** — don't mix unrelated changes
- **Write a clear title** — e.g., "Fix undo not working for bulk task updates"
- **Describe what changed and why** — not just what the diff shows
- **Link related issues** — use `Fixes #123` or `Relates to #456`
- **Keep PRs small** — prefer multiple small PRs over one large one
- **Include screenshots** for UI changes

### Review process

1. All PRs require at least one review before merge
2. CI must pass (tests, type-check, lint)
3. Address feedback with new commits (don't force-push during review)
4. Squash-merge is preferred for clean history

---

## Issue Guidelines

### Bug reports

Include:
- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment (OS, Node version, browser)
- Screenshots if applicable

### Feature requests

Include:
- Problem you're trying to solve
- Proposed solution
- Alternatives considered
- How it fits with the project's [north star](docs/01-north-star-and-non-goals.md)

### Good first issues

Look for issues tagged `good-first-issue` — they're scoped, well-defined, and great for getting familiar with the codebase.

---

## Questions?

Open a discussion on GitHub or check the [docs/](docs/) folder for detailed specifications.
