# FlowOS — North Star & Non-Goals

**Status:** Foundational — refer back to this document whenever scope is in question.
**Last updated:** 2026-09-12

---

## 1. Purpose of this document

This is the anchor document for the project. Every other spec (PRD, Agent Architecture, Extensibility Model, Roadmap) must be consistent with what's written here. When in doubt about whether a feature belongs in the current phase, this is the document to check first — not the long-term vision, and not excitement about what's technically possible.

---

## 2. North Star statement

> **FlowOS is an open-source, local-first, self-hosted personal productivity workspace where a context-aware AI agent — scoped to what you're looking at, backed by the same services the UI uses — can actually see and act on your tasks, calendar, habits, and notes, with every action confirmable, undoable, and auditable.**

The product bet, stated as a falsifiable claim:

> Users will trust and prefer a productivity tool where they *tell* the system what they want changed, over one where they manually navigate and edit — **if and only if** the agent's actions are transparent, reversible, and scoped correctly.

Everything in the MVP exists to test this claim as directly and cheaply as possible. Everything that doesn't help test it is deferred.

---

## 3. What FlowOS IS (core identity, permanent — not phase-dependent)

- A **productivity operating layer**, not a chatbot with a productivity app attached.
- **Agent-as-a-layer**: the agent calls the same Task/Calendar/Habit/Note services the UI calls. There is no separate "AI database" and no shortcut path that bypasses the service layer, ever, for either humans or the agent.
- **Local-first and self-hosted**: the user's data lives on infrastructure they control. No mandatory cloud dependency for core functionality.
- **Provider-agnostic**: the agent's intelligence is swappable — the product is not a wrapper around one vendor's model.
- **Confirmable and reversible by design**: every agent-caused mutation is undoable and logged. This is a permanent product principle, not a v1 feature that gets relaxed later.
- **Two symmetric interaction modes**: traditional UI and natural-language agent control, both first-class, both operating on identical underlying state.

---

## 4. What FlowOS is NOT (permanent exclusions — not just "not yet")

- **Not a general-purpose chatbot.** There is no "ask me anything" mode disconnected from the workspace. Every agent interaction is grounded in real application context and tools.
- **Not a SaaS-only product with a hosted AI backend the user cannot inspect or replace.** BYO-provider is core identity, not a settings toggle for power users.
- **Not an autonomous agent that acts without a visible trust boundary.** FlowOS will never ship a mode where the agent silently performs destructive or bulk actions with no confirmation path and no undo. Speed is never traded for irrecoverable action.
- **Not a note-taking app, a full CRM, or a general database builder.** The domain is personal task/time/habit/goal management. Resisting scope creep into "build any app" territory during the core product phase is a permanent discipline, even once self-extension exists as a feature (see §6).

---

## 5. Non-Goals for the current phase (MVP + v1)

These are explicitly OUT of scope right now. They are not rejected ideas — they are sequenced later. Listing them here prevents them from silently creeping into MVP work.

| Non-goal (for now) | Why deferred |
|---|---|
| Multi-device sync / conflict resolution | Local-first single-device first; sync design should be informed by a stable data model, not guessed at upfront |
| Mobile and desktop native clients | Prove the web core loop first |
| Multi-user / auth / teams | Single-user only, by explicit decision |
| Third-party integrations (Gmail, Google Calendar, GitHub, etc.) | Each is its own OAuth + sync + failure-mode surface; adds risk before the core loop is validated |
| AI-generated custom modules / self-extending application | This is the most exciting long-term idea and the most likely thing to consume all engineering time without validating the core bet first. Explicitly gated behind a trustworthy core agent. |
| More than 2 LLM providers | Prove the abstraction with one hosted + one local provider before expanding |
| Long-term episodic/semantic agent memory (vector store, RAG-over-history) | Start with an explicit, user-visible preference list; opaque memory undermines trust before trust is established |
| Habits, Goals, Projects modules | v1.x — MVP proves the loop on Tasks + Calendar + Notes first |
| Marketplace / sharing of custom modules | Depends entirely on self-extension existing and being safe first |

---

## 6. The long-term vision (kept alive, deliberately not built yet)

The self-extending application vision — where the agent can eventually design, generate, test, and install new modules (e.g., a college attendance tracker) — is a legitimate and differentiated long-term direction. It is **not rejected**. It is explicitly sequenced as **Phase 3+ (Long-Term Research)** and must not influence MVP data model or architecture decisions except in one respect: the module/service boundary should be kept clean from day one specifically *so that* this becomes possible later without a rewrite. See the Extensibility & Self-Extension documents for the conceptual design.

---

## 7. Guardrail for future scope discussions

Before adding anything to the current phase, ask:

1. Does this help prove or disprove the North Star claim in §2?
2. Can the core loop (context → plan → tool → confirm → execute → undo → report) be demonstrated without it?
3. Does it introduce a new trust risk (destructive action, new data exposure, new external dependency) before the current trust risks are handled?

If the answer to (1) is no, or (2) is yes, or (3) is yes without mitigation — it does not belong in the current phase.

---

## 8. Working name

**FlowOS**, self-hosted, open-source. Naming/branding is not a blocking decision and can be revisited without touching any of the above.
