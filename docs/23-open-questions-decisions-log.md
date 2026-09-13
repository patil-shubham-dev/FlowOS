# FlowOS — Open Questions / Decisions Log

## Resolved decisions
| Decision | Answer | Source |
|---|---|---|
| Data locality | Local-first | User, session 2 |
| Multi-user | Single-user only | User, session 2 |
| Confirmation policy | Multi-item/destructive = confirm-required | User, session 2 |
| Memory approach | Explicit, user-visible preference facts only — no opaque long-term memory | Claude recommendation, accepted |
| Context model | Scoped-push on pages; Home is a dedicated AI-native hub with pull-based full-workspace context | User, session 2 |
| Hosting model | Self-hosted only for now | User, session 2 |
| Name | FlowOS | User, session 2 |
| Self-extension scope | Included in long-term vision/design docs; explicitly gated, not part of MVP build | User, session 3 |
| Visual theme direction | Playful, white/light theme, more animation/personality than a typical enterprise dashboard | User, session 3 |
| Reference hosted provider (MVP) | Anthropic first as reference implementation; adapter interface generic, OpenAI follows as correctness check on the abstraction | User, session 4 (design verdict) |
| Undo granularity | Whole-group by default, expand-to-inspect | User, session 4 (design verdict) |
| Cost cap default | No hard default cap; surface running cost prominently (top bar), user may set a cap in Settings | User, session 4 (design verdict) |
| Note search technique (MVP) | Full-text search (FTS); no embedding/vector-store dependency — revisit semantic in v1 on usage evidence | User, session 4 (design verdict) |
| Blocked-by-default UX | Auto-propose the itemized confirm-tier breakdown inline in the Blocked card — user never asks twice | User, session 4 (design verdict) |
| Design token ownership | `docs/design.md` is the living source of truth; changes via reviewed diffs, never wholesale regeneration (§11 traceability stays trustworthy) | User, session 4 (design verdict) |
| Reminders | Folded into Task/Event through v1; revisit only if Habits genuinely needs standalone reminder semantics | User, session 4 (design verdict) |
| MVP tech stack | TypeScript + SQLite (better-sqlite3); vitest for the golden-scenario suite | User, session 4 (design verdict) |

## Open questions (need a decision before or during implementation)
1. **Sandboxing technique for future self-extension**: not a current-phase decision — known unresolved research question (process isolation vs. language-level sandbox vs. container-per-module). Correctly deferred; no action now.
2. **Provider gate on UI input (TODO — revisit when the planner lands)**: the Home/scoped inputs are deliberately NOT disabled without a configured provider, because nothing consumes a model yet (all plans are explicit; token count stays 0). This silently becomes wrong the moment the planner makes real model calls — at that point the backend must reject provider-less turns AND the UI must disable/label input per design.md §5.3. Code marker: `src/web/app.tsx` (input-gating site).

## How to use this log
Add new entries as they arise during PRD refinement or implementation. Resolved items move to the top table with their source noted. This log, together with the North Star document, is the arbiter when a downstream document appears to conflict with an earlier decision.
