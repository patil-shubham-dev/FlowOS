# FlowOS — Sync / Offline / Local-First Strategy

## 1. Current phase: local-first, single-device
- All data lives locally on the self-hosted instance. There is no sync problem to solve yet because there is only one device/instance per user by explicit decision (see North Star §5).
- "Local-first" as a design value is adopted now anyway, because it shapes good architecture regardless: the UI should never feel network-latency-bound for ordinary manual edits (Non-Functional Requirements §1), and the data layer should be structured as if it will eventually need to sync, even though it doesn't yet.

## 2. Why design for sync now without building it
Retrofitting sync onto a data model and mutation pattern that wasn't designed with it in mind is expensive and risky (this is a common cause of painful rewrites in local-first apps). Two decisions made now pay off later at near-zero present cost:
- **The Action Log (see Audit/Undo/Rollback doc) is event-shaped** — each entry captures a discrete, timestamped, attributable change. This is structurally very close to what an event-sourced sync log needs. When sync is eventually built, this log is a natural candidate to double as (or directly feed) the sync stream, rather than needing a parallel system.
- **Entities carry `workspace_id`** now, even though only one workspace exists, so a future sync/multi-device model doesn't require a schema migration to introduce the concept.

## 3. Future sync approach (design sketch, not committed implementation)
When multi-device sync is undertaken (v2+):
- **Candidate approach**: CRDT-based or operation-based sync for structured entities (Tasks, Events, Notes), since these are mostly independent records with occasional field-level conflicts rather than deeply nested collaborative documents — this avoids needing a full OT/CRDT text-editing engine except possibly for Note *content* specifically, which is the one field most likely to need real concurrent-edit handling.
- **Conflict resolution default**: last-write-wins at the field level for most entities, with the Action Log providing enough history to detect and surface true conflicts (edits to the same field from two devices in a close time window) for user resolution rather than silently discarding one side.
- **Agent-caused vs. manual-caused changes** should both sync identically — the sync layer doesn't need to know the difference, reinforcing the "one service layer" principle again.

## 4. Offline operation
- In the current single-device phase, "offline" mostly means "LLM provider unreachable," not "device disconnected from a sync server" (see Error Handling & Failure Modes doc) — manual UI operation must remain fully functional.
- Once multi-device sync exists, a proper offline queue (local mutations recorded and replayed against the sync log on reconnect) becomes necessary — deferred, but the Action Log's shape makes this tractable when the time comes.

## 5. Data ownership principle
Regardless of how sync is eventually implemented, no sync mechanism should require routing data through a third-party server the user doesn't control by default — self-hosted-to-self-hosted (or self-hosted-to-local-client) sync is the target model, consistent with the project's self-hosted identity.
