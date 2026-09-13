# FlowOS — Permission & Confirmation Model

## 1. Risk tiers
| Tier | Definition | Default behavior |
|---|---|---|
| **Read** | No mutation, information retrieval only | Always automatic, never logged as a mutation (may be logged as a query for debugging) |
| **Auto** | Low-risk, single-item, easily reversible mutation | Executes immediately, logged, shown as a dismissible after-the-fact notice |
| **Confirm** | Multi-item, cascading, or moderately consequential mutation | Agent constructs a preview/diff; execution blocked until user approves, edits, or cancels |
| **Blocked-by-default** | Bulk destructive actions, or anything without a clean undo path | Not executable by the agent at all in MVP; the agent instead explains what it would do and asks the user to perform it manually or explicitly break it into itemized confirm-tier actions |

## 2. Classification rules (enforced in code, not by prompting alone)
- Tier is determined primarily by **tool identity + call scope**, evaluated server-side after the agent proposes a call, before execution:
  - A tool's *base* tier is fixed metadata (see Agent Tool Specification tables).
  - Scope escalates tier: e.g., `updateTask` is Auto for one item but automatically escalates to Confirm when the resolved target set contains more than one item.
  - Certain tools have a tier floor that scope can never lower (e.g., `deleteEvent` is never Auto, regardless of scope).
- The LLM's own claim about risk ("this is a small change") is never trusted as the enforcement mechanism — the backend independently computes tier from the actual resolved action before allowing execution.

## 3. Confirmation flow
1. Agent produces a plan containing one or more proposed tool calls.
2. Backend classifies the *actual* tier of each proposed call (post-resolution, not pre-resolution — e.g., after resolving "all urgent tasks" to a concrete list of IDs).
3. If any call in the plan is Confirm-tier or above, the entire plan is held and rendered as a confirmation card (see UX Principles §2) instead of executed.
4. User response:
   - **Approve** → plan proceeds to execution as originally proposed.
   - **Edit** → user adjusts specific items/values inline; edited plan is re-classified (an edit could itself lower or raise tier) before execution.
   - **Cancel** → nothing executes; no audit log mutation entry is created (a "proposed but cancelled" trace may exist for debugging only).
5. Blocked-by-default calls are never silently dropped — the agent must explain the limitation and offer an alternative (e.g., itemized confirm-tier actions, or "please do this one manually").

## 4. User-configurable permission settings
- Users may **raise** friction (e.g., force Confirm on things that default to Auto) freely.
- Users may **lower** friction only within safe bounds — e.g., they may set single-item `updateTask` to fully silent-auto, but may not set `deleteEvent` or any Blocked-by-default tool to Auto in MVP. This asymmetry is intentional: it should always be easier to ask for more caution than less.
- Settings changes are themselves logged (who changed what permission, when).

## 5. Relationship to audit/undo
Every executed action, regardless of tier, produces an audit log entry (see Audit/Undo/Rollback Strategy) — tier affects *whether confirmation was required before execution*, not whether the action is tracked afterward. Read-tier calls are the only exception (not treated as mutations).

## 6. Open items for later phases
- As integrations are added (v2+), define new risk tiers or sub-policies for external-effect actions (e.g., sending an email) — likely a distinct tier above Confirm, since undo may not exist for external side effects.
- As self-extension is explored (long-term), code-modifying actions require an entirely separate approval pipeline outside this model (see Self-Extension Vision document) — they are not just a higher risk tier of the same system.
