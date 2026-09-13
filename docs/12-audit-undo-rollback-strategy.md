# FlowOS — Audit / Undo / Rollback Strategy

## 1. Principle
Undo is a property of the **data layer**, not a special feature of the agent. Every mutation — whether triggered by manual UI action or by an agent tool call — passes through the same service layer and produces the same kind of action-log record. This is what makes agent trust achievable without building two parallel systems.

## 2. Action log schema (conceptual)
Each entry records:
- `id`, `timestamp`, `actor` (agent or user-manual), `session/thread reference` (if agent-triggered)
- `tool` or `operation` name
- `target entity type + id(s)`
- `before-state` (snapshot of affected fields prior to change)
- `after-state` (snapshot post-change)
- `group id` (for cascading/multi-step actions that must undo as one unit)
- `undo status`: available / already undone / no-longer-undoable (with reason)

## 3. Undo mechanism
Two acceptable strategies, chosen per tool based on what's cleanest:
- **Inverse operation**: e.g., `updateTask(id, {due: X})`'s inverse is `updateTask(id, {due: previousX})` — trivial and preferred for simple field changes.
- **Snapshot restore**: for structural changes (e.g., deletion, bulk creation) restore the full prior snapshot of affected entities rather than trying to compute a precise inverse.

Every mutating tool defined in the Agent Tool Specification must declare which strategy it uses.

## 4. Grouped actions
Cascading changes (e.g., Flow A: moving one event shifts others) are recorded under a single `group id`. The UI presents and undoes them as one unit by default, but should allow expanding to see (and potentially selectively undo) individual sub-changes for advanced users.

## 5. Undo limits
An action becomes "no longer undoable" when a subsequent, independent change has invalidated the prior state (e.g., the user manually edited the same task after the agent changed it). The system must detect this and clearly explain why undo is unavailable rather than silently failing or producing a corrupted state. This is a required error case, not an edge case to skip.

## 6. Audit log as the future sync log
Architecturally, this same action log is the natural foundation for a future sync protocol (each entry is essentially an event in an event-sourced sense) — this doesn't need to be built now, but the schema should be designed with this reuse in mind (see Sync/Offline/Local-First Strategy).

## 7. UI surface
- A dedicated **Action History** view (reachable from Home/Settings) listing entries reverse-chronologically, filterable by module/actor, each with an Undo button where eligible.
- Confirmation cards, at the moment of approval, should make clear that the action will be undoable, reinforcing the trust loop in real time.

## 8. Retention
Action log entries are retained indefinitely by default in this local-first, single-user phase (storage cost is low); a pruning/archival policy can be introduced later without being a launch requirement.
