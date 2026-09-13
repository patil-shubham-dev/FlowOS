# FlowOS — UX / Product Principles

## 1. Visual & interaction philosophy
- **Playful, light, "white theme" aesthetic** — bright, airy surfaces, generous whitespace, soft rounded corners, gentle shadows rather than heavy borders. The tool should feel calm and human, not clinical or enterprise-dashboard-like.
- **Motion with purpose** — subtle micro-animations for state changes (a task moving, a confirmation card appearing/collapsing, a diff resolving into "applied"), never decorative-only motion that adds latency to perceived responsiveness.
- **Data is primary, chrome is secondary** — the UI's job is to show the user their actual life/work; the agent's job is to act on it. Neither should visually overwhelm the other.
- **Command bar is minimal, not a chat window** — on scoped pages ("Tell me what to do...") it should look like a single-line input that expands only when needed, not a persistent chat panel eating page space. Home is the one place a full chat-style interface is appropriate.

## 2. Confirmation UX (core trust mechanism)
- Confirmations render as an inline **diff card**, not a modal dialog and not a wall of text — show *before → after* for each affected item.
- Every confirmation card has three actions: **Approve**, **Edit** (adjust the proposed change inline before committing), **Cancel**.
- Grouped/cascading changes (Flow A in User Stories doc) are shown as one card with sub-items, and undo later treats them as one grouped action.
- Low-risk, auto-executed actions still show a lightweight, dismissible toast-style confirmation *after* the fact ("Marked 'Buy groceries' as done") — the user should never wonder whether something silently happened.

## 3. Explainability
- Every agent action, whether auto or confirmed, ends with a short plain-language summary of what changed and why (e.g., "because Maths conflicted with your 7:30 gym block").
- When the agent used a learned preference to make a decision, it names the preference explicitly ("Since you usually study at 7pm...").
- When the agent is uncertain or a request is ambiguous, it asks a clarifying question rather than guessing — especially before any confirm-tier or higher action.

## 4. Error & failure visibility
- Errors are specific and actionable, never generic ("Something went wrong"). Always name the cause (model/provider/tool/validation) and the next step.
- Partial multi-step failures are reported precisely: what completed, what didn't, and whether a partial rollback occurred.

## 5. Empty states & onboarding
- No module should show a blank, unexplained empty state — each includes a one-line prompt suggesting either a manual action or an example agent command.
- First-run flow gates agent features behind provider configuration, with a friendly explanation of why (data goes to whichever provider you choose).

## 6. Consistency between manual and agent-driven edits
- Any item changed by the agent should be visually indistinguishable in the data (no "AI-tainted" field), but recently agent-changed items may get a brief, subtle visual highlight that fades — so the user notices without it feeling permanent or stigmatizing.

## 7. Accessibility & tone
- Color is never the sole carrier of meaning (e.g., risk tiers, conflicts) — use icons/labels alongside color.
- Copy tone: direct, warm, competent — not cutesy, not corporate. The agent talks like a capable assistant, not a mascot.

## 8. Design system note
Detailed visual tokens (exact palette, spacing scale, typography, component states, animation curves) belong in a dedicated `design.md` produced during implementation setup — this document defines the *principles* that design.md must satisfy, not the token values themselves.
