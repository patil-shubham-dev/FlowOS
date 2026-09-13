# FlowOS — Architecture Overview

## 1. Layered architecture (the non-negotiable principle)
```
        Traditional UI                    Agent (Home hub + scoped command bars)
              │                                          │
              ▼                                          ▼
       ┌─────────────────────────────────────────────────────────┐
       │                     SERVICE LAYER                        │
       │   TaskService · CalendarService · NoteService ·          │
       │   PreferenceService · ActionLogService                   │
       └─────────────────────────────────────────────────────────┘
                              │
                              ▼
                        DATA LAYER (local-first store)
```
- The agent never talks to the Data Layer directly. It talks to **Tools**, which are thin wrappers that call the exact same Service Layer methods the UI calls.
- This is the enforcement mechanism for "no separate AI database" — it's structural, not a style guideline.

## 2. Agent subsystem (expanded)
```
User input + assembled context
        ↓
   Agent Runtime  ── calls ──▶  Provider Adapter (OpenAI/Anthropic/Ollama/etc.)
        ↓  (plan + proposed tool calls)
   Risk Classifier (code-enforced, reads tool metadata + resolved scope)
        ↓
   [Confirm Gate UI]  ←── only if tier requires it
        ↓
   Tool Executor  ── calls ──▶  Service Layer
        ↓
   Verifier
        ↓
   Action Log Writer  ── writes ──▶  ActionLogService
        ↓
   Response Composer → UI
```

## 3. Deployment shape (MVP)
- Single self-hosted process (or small set of processes) serving: a web frontend, a backend API (Service Layer + Agent Runtime + Tool Executor), and a local-first data store.
- No mandatory external dependency besides the user's chosen LLM provider endpoint.
- Designed to run comfortably on a home server / NAS / personal machine — not designed around a specific cloud platform.

## 4. Module boundary discipline (for future extensibility)
- Each domain (Tasks, Calendar, Notes, later Habits/Goals/Projects) is implemented as a self-contained module exposing: its Service, its Tools (registered into a common Tool Registry), and its UI components/pages.
- The Agent Runtime and Tool Registry are domain-agnostic — they don't hardcode knowledge of "Tasks" or "Calendar," they just execute whatever tools are registered. This is what makes the long-term self-extension vision structurally possible without a rewrite, even though nothing in that vision is being built yet.

## 5. Cross-cutting subsystems
- **Provider Abstraction** — normalizes chat/tool-calling across providers (see dedicated doc).
- **Permission/Risk Classifier** — sits between planning and execution, enforced server-side.
- **Action Log / Audit** — shared by Service Layer regardless of caller (UI or Agent).
- **Preference Store** — explicit memory, read during context assembly, written only via explicit tool.

## 6. What this architecture deliberately defers
- Sync engine, multi-device conflict resolution — designed for later (see Sync/Offline strategy) but not implemented; the Action Log's event-like shape is chosen partly to make this easier later.
- Module install/uninstall runtime, sandboxing, code generation pipeline — conceptually scoped (Extensibility & Self-Extension docs) but not built.
- Auth/multi-tenancy — `workspace_id` exists in the schema but no auth layer sits in front of it yet.
