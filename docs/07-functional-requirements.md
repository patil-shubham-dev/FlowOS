# FlowOS — Functional Requirements (MVP)

Requirements are numbered by module for traceability. "Agent" requirements apply across modules.

## FR-1 Tasks
- FR-1.1 Create, read, update, delete tasks with fields: title, description, due date, priority, status, tag, estimated duration.
- FR-1.2 Filter tasks by status, priority, due date range, tag.
- FR-1.3 List and board views.
- FR-1.4 Scoped command bar receives: visible task list (post-filter), selected task, active filters.

## FR-2 Calendar
- FR-2.1 Create, read, update, delete events with fields: title, start, end, fixed/flexible flag, linked task (optional).
- FR-2.2 Day and week views.
- FR-2.3 Automatic conflict detection between overlapping events.
- FR-2.4 `findFreeTime`-style query capability for a given date range and duration.
- FR-2.5 Scoped command bar receives: visible range, events in range, selected event, detected conflicts.

## FR-3 Notes
- FR-3.1 Create, read, update, delete notes (markdown content, optional tags, optional task links).
- FR-3.2 Full-text/semantic search over notes accessible to the agent via tool call.
- FR-3.3 Scoped command bar receives: currently open note(s).

## FR-4 Home
- FR-4.1 Ambient summary cards: today's plan, upcoming events, tasks due today (extendable to habits/goals post-MVP).
- FR-4.2 Full chat-style interface with session/thread history.
- FR-4.3 Pull-based context: no full workspace data preloaded; agent must call tools to retrieve data as needed.
- FR-4.4 Entry point for cross-module requests.

## FR-5 Settings
- FR-5.1 Add/edit/remove AI provider configurations (provider type, model, API key or base URL, model parameters).
- FR-5.2 Set a default provider/model.
- FR-5.3 View/adjust permission tier behavior within allowed bounds.
- FR-5.4 View, edit, delete individual learned preference/memory facts.
- FR-5.5 View action history/audit log; trigger undo on eligible entries.

## FR-6 Agent runtime (cross-cutting)
- FR-6.1 Agent receives page-scoped context automatically on scoped pages; pull-based context on Home.
- FR-6.2 Agent operates exclusively through defined tools calling the real service layer — no direct data store access.
- FR-6.3 Agent produces a plan for multi-step requests before executing tool calls with side effects.
- FR-6.4 Agent classifies each proposed action by risk tier (see Permission & Confirmation Model) and applies the correct execution path (auto / confirm / blocked).
- FR-6.5 Agent generates a preview/diff for confirm-tier and higher actions prior to execution.
- FR-6.6 Agent logs every executed mutation (regardless of tier) to the action history with enough detail to construct an inverse operation.
- FR-6.7 Agent produces a plain-language summary after execution.
- FR-6.8 Agent asks a clarifying question instead of guessing when a request is ambiguous with respect to scope or intent, especially before confirm-tier+ actions.
- FR-6.9 Agent surfaces specific errors on tool failure, model failure, or provider failure — never a silent no-op.
- FR-6.10 Agent can read and write to an explicit, user-visible preference/memory list; may not maintain any other persistent memory in MVP.

## FR-7 Provider abstraction
- FR-7.1 System supports at least two provider types simultaneously configured (one hosted API, one local/OpenAI-compatible).
- FR-7.2 System validates a provider configuration (e.g., test call) before marking it active.
- FR-7.3 System detects and surfaces when a configured model lacks reliable tool-calling support.

## FR-8 Undo / Audit
- FR-8.1 Every tool that mutates data defines an inverse operation or snapshot-based restoration.
- FR-8.2 Grouped/cascading actions are undoable as a single unit.
- FR-8.3 Action history is queryable and persists across sessions.
