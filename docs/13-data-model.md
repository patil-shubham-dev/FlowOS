# FlowOS — Data Model (Conceptual)

This is a conceptual/entity-relationship level document, not a schema-ready DDL. Field lists are illustrative of what's required, not exhaustive or final-typed.

## 1. Future-proofing note
Even though FlowOS is single-user and local-first for this phase, every core entity includes a `workspace_id` (defaulted to a single fixed value for now). This avoids a breaking migration when multi-device/multi-workspace concepts arrive later, per North Star §"one more consequence."

## 2. Core entities (MVP)

**Task**
- id, workspace_id, title, description, due_at, priority, status, tag, estimated_duration, created_at, updated_at, source (manual | agent)

**Event**
- id, workspace_id, title, start_at, end_at, is_flexible (bool), linked_task_id (nullable), created_at, updated_at, source

**Note**
- id, workspace_id, title, content (markdown), tags[], linked_task_ids[], created_at, updated_at, source

**Preference** (explicit memory fact)
- id, workspace_id, statement (human-readable fact), source_session_id, created_at, last_used_at

**ProviderConfig**
- id, workspace_id, provider_type (enum: openai | anthropic | google | openrouter | ollama | lm_studio | custom_openai_compatible), model, api_key (encrypted), base_url (nullable), params (temperature etc.), is_default (bool)

**ActionLogEntry**
- id, workspace_id, timestamp, actor (agent | manual), session_id (nullable), tool_or_operation, target_type, target_ids[], before_state, after_state, group_id (nullable), undo_status

**AgentSession**
- id, workspace_id, surface (home | scoped:tasks | scoped:calendar | scoped:notes), started_at, messages[] (role, content, tool_calls, tool_results)

## 3. Deferred entities (v1+)
- **Habit** — name, frequency, schedule, streak, completion_history[]
- **Project** — name, description, linked tasks/goals, status
- **Goal** — name, target, progress, linked tasks/projects
- **Reminder** — currently folded into Task.due_at / Event notifications for MVP; may become a distinct entity in v1 if standalone reminders (not tied to a task/event) are needed

## 4. Relationships
- Task ↔ Note: many-to-many via `linked_task_ids` on Note (or a join table if preferred at implementation time).
- Task ↔ Event: optional one-to-one via `linked_task_id` on Event (a task can have a scheduled block).
- ActionLogEntry → any entity: polymorphic reference via `target_type` + `target_ids`.
- AgentSession → ActionLogEntry: one-to-many, linked via `session_id` where the action originated from an agent turn.

## 5. Long-term entities (design placeholders only, not built now)
- **ModuleManifest** — for self-extension/plugin phase: module id, version, declared entities, declared tools, declared UI slots, permissions requested, install status.
- **GeneratedModuleArtifact** — code/spec artifacts produced during self-extension pipeline, versioned, with review/approval status.

These are listed here only so the core schema doesn't accidentally make them impossible later (e.g., by hardcoding a closed enum of "modules" somewhere core) — no implementation work on these is in scope now.
