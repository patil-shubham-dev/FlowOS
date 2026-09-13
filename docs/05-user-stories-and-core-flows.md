# FlowOS — User Stories & Core User Flows

## 1. User stories (MVP)

**Onboarding**
- As a new self-hoster, I can add an AI provider (API key or local base URL) before I can use any agent feature, so I understand where my data/requests go.
- As a user, I see a clear message if no provider is configured yet when I try to use the agent.

**Tasks**
- As a user, I can ask "move all urgent tasks to tomorrow morning" and see a confirmation card listing exactly which tasks will change before it happens.
- As a user, I can undo a bulk task move from the action history.

**Calendar**
- As a user, I can ask the agent to move a specific event and have it detect and resolve resulting conflicts, explaining what else changed.
- As a user, I can say "make tomorrow less busy" and get a proposed lighter schedule I can approve or edit before it's applied.

**Notes**
- As a user, I can ask the agent to find everything related to a topic across my notes and turn it into a structured task/project outline.

**Home**
- As a user, I land on Home and see today's plan without asking for it.
- As a user, I can ask a cross-module question ("what do I need to do today?") and get a synthesized answer drawing from tasks, calendar, and notes.

**Trust & control**
- As a user, I can see a full history of everything the agent has changed.
- As a user, I can undo any single agent action, and understand which actions are no longer undoable (e.g., because of downstream dependent edits) and why.
- As a user, I can review and edit/delete anything the agent has "learned" about my preferences.

## 2. Core flows (described step by step)

### Flow A — Scoped reschedule with cascading effects
1. User is on Calendar, viewing today.
2. User types: "Move my maths study session to 7pm and adjust the rest of today's schedule."
3. Agent receives: visible date range, all events today, flags on which are flexible.
4. Agent plans: locate "maths study session" → check 7pm for conflicts → identify flexible events after 7pm that now overlap → propose shifts for those.
5. Agent calls tools: `searchEvents`/`updateEvent` (dry-run/preview mode) to construct a diff, not yet committed.
6. UI shows a confirmation card: "Move Maths Study to 7:00–8:00 PM. This also shifts Reading Block to 8:00–8:30 PM. Reminders will update accordingly." [Approve] [Edit] [Cancel]
7. User approves.
8. Agent executes committed tool calls, service layer persists changes, action log records a grouped action ("Reschedule: Maths + 1 cascading change") with a single Undo affordance for the group.
9. Agent responds in-thread/inline: plain-language summary of what changed.

### Flow B — Bulk task triage
1. User on Tasks page, filtered to "Urgent."
2. User types: "Move all urgent tasks to tomorrow morning."
3. Agent receives current filter (urgent) and the resulting visible task IDs — no extra tool call needed to know scope, since it's page-scoped.
4. Agent proposes new due dates/times for each visible urgent task, shown as a list diff.
5. User approves (bulk = confirm-required tier).
6. Execution + logging + inline summary, as above.

### Flow C — Home hub cross-module synthesis
1. User on Home, types: "Find everything I've written about my TTS project and turn it into a project plan."
2. Agent has no page context to pull from (Home is unscoped) — calls `searchNotes("TTS project")`.
3. Agent synthesizes retrieved notes into a proposed task list/outline.
4. Agent proposes creating N tasks (and optionally a project tag) — shown as a creation diff (creation of new items is lower risk than mutating/deleting existing ones, but still shown for review since it's a bulk creation).
5. User approves, edits count, or cancels.
6. Tasks created via `createTask` calls; logged; summarized.

### Flow D — Provider failure
1. User issues a command; configured provider times out or returns an auth error.
2. Agent surfaces a specific, human-readable error ("Couldn't reach OpenAI: invalid API key") with a direct link to Settings → AI Provider.
3. No partial/silent action is taken.

### Flow E — Undo
1. User opens Action History (from Home or a dedicated panel).
2. Sees a reverse-chronological log of agent (and optionally manual) actions.
3. Selects a past action, clicks Undo.
4. System applies the recorded inverse operation (or restores the snapshot), updates the log to reflect the reversal, and confirms in the UI.
