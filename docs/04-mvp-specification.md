# FlowOS — MVP Specification

## 1. Scope summary
Four surfaces: **Home, Tasks, Calendar, Notes**, plus **Settings**. One agent runtime, two configurable providers, full confirm/undo/audit infrastructure. No Habits/Goals/Projects, no integrations, no sync, no self-extension in this phase.

## 2. Home (AI-native hub)
- Layout: chat-style interaction area (like a Claude/ChatGPT thread) as the dominant element.
- Above/around the chat: lightweight ambient summary cards — today's plan, next 3 events, tasks due today, active streaks (once Habits exists, post-MVP this expands).
- No workspace data is preloaded into every prompt; the agent uses `getWorkspaceContext` and other tools on demand (pull-based context, see North Star §"Home page").
- Persistent thread history for the session; each agent turn that mutates data shows an inline confirmation/diff card before executing (where risk-tiered as such) and a result summary after.
- Entry point for cross-module requests that don't belong to one page (e.g., notes → project plan).

## 3. Tasks
- Views: list and simple board (by status).
- Fields: title, description, due date, priority, status, project tag (free-text tag in MVP, not a full Projects module), estimated duration (optional).
- Filters: status, priority, due date range, tag.
- Scoped command bar at bottom: "Tell me what to do..." — agent receives current filter state, visible task IDs, selected task (if any).
- Example commands: "Move all urgent tasks to tomorrow morning," "Mark the report task done," "Split this task into three subtasks."

## 4. Calendar
- Views: day and week.
- Events: title, start/end, fixed vs. flexible flag (flexible = agent may reschedule without being asked specifically), linked task (optional).
- Conflict detection: overlapping events are visually flagged.
- Scoped command bar: agent receives visible date range, events in range, selected event, detected conflicts.
- Example commands: "Move my maths session to 7pm and adjust the rest of today," "Make tomorrow less busy," "I have college 9–4 tomorrow, fit everything else around it."

## 5. Notes
- Simple rich-text notes (markdown-backed is fine), optional tags, optional links to tasks.
- Agent-searchable via `searchNotes` (semantic-ish search over content; exact technique is an implementation decision, not a product one).
- Scoped command bar: agent receives currently open/visible note(s).
- Example command: "Find everything I've written about my TTS project and turn it into a project plan" (this is a Home-hub-style cross-module command; Notes page command bar handles narrower in-page requests).

## 6. Settings
- **AI Provider**: add/edit provider configs (provider type, model, API key or base URL, temperature/other basic params). Ability to set a default provider/model.
- **Permissions**: view/adjust risk-tier behavior within allowed bounds (see Permission & Confirmation Model — some tiers are not user-overridable in MVP for safety).
- **Memory / Preferences**: list of learned facts, each editable/deletable, each showing when/why it was learned.
- **Action History**: chronological log of all agent-caused mutations, each with an Undo affordance where still applicable.

## 7. Cross-cutting MVP requirements
- Every agent mutation is logged to the action history regardless of risk tier.
- Every mutation-capable tool has a defined inverse operation or snapshot-based undo (see Audit/Undo/Rollback Strategy).
- Manual UI edits and agent edits are indistinguishable to the service layer — both go through the same service calls and both are logged identically.
- Model/provider failure (timeout, auth error, malformed tool call) must surface a clear, specific error — never a silent no-op or a silently wrong action.

## 8. Explicitly out of MVP
Habits, Goals, Projects modules; any third-party integration; multi-device sync; mobile/desktop clients; module/plugin installation; AI-generated modules; more than two providers; opaque long-term memory.
