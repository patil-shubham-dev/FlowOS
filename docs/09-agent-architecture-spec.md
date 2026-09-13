# FlowOS — Agent Architecture Specification

## 1. The agent loop
```
USER INPUT
   ↓
CONTEXT ASSEMBLY   (scoped-push on pages / pull-based on Home)
   ↓
PLANNING           (decompose intent into steps, identify needed tools)
   ↓
TOOL SELECTION
   ↓
RISK CLASSIFICATION  (per proposed action: auto / confirm / blocked)
   ↓
[CONFIRM GATE if required] ← user approves / edits / cancels
   ↓
EXECUTION          (tool calls against real service layer)
   ↓
VERIFICATION       (did the mutation succeed as intended? re-read state if needed)
   ↓
AUDIT LOG WRITE    (always, regardless of tier)
   ↓
UI UPDATE
   ↓
PLAIN-LANGUAGE REPORT TO USER
```
This loop is the same regardless of which page/surface triggered it — only context assembly differs (see §2).

## 2. Context assembly
Two modes, both producing the same internal context object consumed by the planner:

- **Scoped-push (Tasks/Calendar/Notes pages)**: the frontend serializes currently-visible state (filters, visible item IDs, selection) and attaches it to the user's message automatically. No tool call is needed to know "what's on screen."
- **Pull-based (Home)**: the agent starts with only a lightweight ambient summary (counts/highlights) and must explicitly call tools (`getWorkspaceContext`, `searchTasks`, `searchNotes`, etc.) to retrieve anything it needs. This keeps Home cheap by default and scales naturally to cross-module requests.

Context assembly must be a distinct, inspectable step in the pipeline (see Non-Functional Requirements §6, observability) — not implicit in the prompt template.

## 3. Planning
- For single-tool, unambiguous requests (e.g., "mark this task done"), planning collapses to a single step.
- For multi-step requests (Flow A in User Stories doc), the agent must produce an explicit plan — a sequence of intended tool calls with rationale — before executing anything with side effects. The plan is what gets rendered as a confirmation diff when required.
- Planning must identify *affected items beyond the literal target* when relevant (cascading effects) — this is the core value proposition of the agent and must not be skipped for the sake of simplicity.

## 4. Tool execution
- All side-effecting actions occur exclusively through the tool interface defined in the Agent Tool Specification. There is no path from agent to database that bypasses the service layer.
- Tools are executed in two possible modes: **preview** (constructs the intended change without committing, used to build confirmation diffs) and **commit** (actually persists). Every mutating tool must support both.
- Tool execution results (success/failure/partial) are captured precisely for verification and audit.

## 5. Risk classification & confirm gate
Handled in detail in the Permission & Confirmation Model document. Architecturally: risk tier is determined by tool metadata + the scope of the specific call (e.g., `updateTask` on one item vs. matching 12 items), evaluated **in code**, not solely by the LLM's self-assessment.

## 6. Verification
- After commit-mode execution, the agent should verify outcomes where feasible (e.g., re-fetch the updated event and confirm the new time matches intent) rather than assuming success from the tool call's return alone, to catch service-layer errors or race conditions.
- Verification failures are treated as execution failures and reported, not silently ignored.

## 7. Memory (explicit, narrow — see North Star)
- A dedicated `preferences`/memory store, written to only via an explicit tool (`saveLearnedPreference` or similar) and always shown to the user.
- The agent may read this store as part of context assembly (lightweight, always included — it's small and cheap) but must not write to it silently as a side effect of unrelated actions without it appearing in the audit trail as a distinct, visible event.

## 8. Sessions & state
- Each Home conversation is a session with persisted thread history.
- Scoped-page command bar interactions may be logged as lightweight, page-associated interactions without necessarily maintaining a long multi-turn thread (single-shot commands are the common case there), but should still appear in the unified action history.

## 9. Provider abstraction hookup
The agent runtime is provider-agnostic: it constructs a normalized request (system context + tools + conversation) and delegates to whichever provider adapter is configured (see AI Provider Abstraction document). Planning and risk classification logic live above this line and must not vary by provider.

## 10. Explicitly out of scope for MVP agent architecture
- Sub-agents / agent-to-agent delegation.
- Autonomous background operation without a user-initiated trigger.
- Long-horizon planning across multiple sessions/days without explicit user re-engagement.
