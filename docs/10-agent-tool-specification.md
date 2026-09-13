# FlowOS — Agent Tool Specification

## 1. Design rules for all tools
- Every tool maps 1:1 to a real service-layer method — no tool may access storage directly.
- Every tool declares: name, description, input schema, output schema, **risk tier** (see Permission & Confirmation Model), whether it supports **preview mode**, and its **inverse operation** (or "snapshot-based" if no clean inverse exists).
- Search/read tools return structured data only — they never mutate.
- Mutating tools must be idempotent-safe where feasible (calling with the same input twice shouldn't cause duplicate side effects beyond the first).

## 2. Task tools
| Tool | Purpose | Risk tier (single item) | Risk tier (bulk / >1 item) |
|---|---|---|---|
| `createTask` | Create a task | Auto | Confirm (bulk create) |
| `updateTask` | Update fields on a task | Auto (non-destructive fields) | Confirm |
| `deleteTask` | Delete a task | Confirm | Blocked-by-default* |
| `searchTasks` | Query tasks by filter | Read (always auto) | — |

## 3. Calendar tools
| Tool | Purpose | Risk tier |
|---|---|---|
| `createEvent` | Create an event | Auto |
| `updateEvent` | Update/move an event | Auto (single, non-conflicting) / Confirm (creates conflict or cascades) |
| `deleteEvent` | Delete an event | Confirm |
| `findFreeTime` | Query available time in a range | Read |
| `detectConflicts` | Identify overlapping events | Read |

## 4. Note tools
| Tool | Purpose | Risk tier |
|---|---|---|
| `createNote` | Create a note | Auto |
| `updateNote` | Edit a note | Auto |
| `searchNotes` | Semantic/full-text search | Read |
| `linkNoteToTask` | Associate a note with a task | Auto |

## 5. Scheduling / planning tools
| Tool | Purpose | Risk tier |
|---|---|---|
| `reschedule` | Move one item and resolve cascading effects (composite: internally may call updateEvent/updateTask multiple times) | Confirm (always — inherently multi-item by nature) |
| `optimizeSchedule` / `lightenSchedule` | Propose a lighter/adjusted version of a day (used by "make today lighter") | Confirm |
| `generateSchedule` | Produce a full plan for a period (e.g., "plan my study week") from tasks/events/goals | Confirm |

## 6. Workspace / cross-module tools
| Tool | Purpose | Risk tier |
|---|---|---|
| `getWorkspaceContext` | Pull-based ambient summary (Home) | Read |
| `globalSearch` | Search across tasks/events/notes | Read |
| `saveLearnedPreference` | Write an explicit fact to the visible preference store | Auto, but always surfaced distinctly in audit log |
| `deleteLearnedPreference` | Remove a preference | Auto |

## 7. Deferred tools (v1+, not MVP)
`createHabit`, `updateHabit`, `logHabit`, `analyzeHabit`, `createProject`, `updateProject`, `createGoal`, `updateGoal`, `createReminder`, `updateReminder`, `deleteReminder` (MVP folds reminders into event/task due-date behavior rather than a separate entity — revisit in v1).

## 8. Tools intentionally NOT built at all in the current architecture
- Any tool with direct filesystem/OS/command execution access.
- Any tool that sends external communications (email, messages) — deferred to integrations phase and, even then, treated as a distinct high-risk category requiring explicit opt-in per North Star principles.
- Any tool that can modify the application's own code — reserved exclusively for the gated, human-approved self-extension pipeline (see Self-Extension Vision document), never exposed as a normal agent tool.

*\*`deleteTask`/`deleteEvent` bulk operations (matching many items via a filter) are blocked-by-default in MVP — the agent must break them into an explicit, itemized confirm-tier list rather than executing a single "delete all matching X" call, so the user reviews exactly what's being removed.*
