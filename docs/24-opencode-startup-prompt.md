# FlowOS — OpenCode Startup Prompt

Copy the block below into OpenCode as the initial project prompt. It assumes all 23 FlowOS documents (numbered 01–23) are present in the repo/context alongside this prompt.

---

## PROMPT

You are acting as the lead implementation agent for **FlowOS**, an open-source, self-hosted, AI-native personal productivity operating system. A complete set of product, architecture, and specification documents already exists — you must treat them as the source of truth for scope and design intent. Do not re-derive product decisions from scratch; read and follow the documents.

### Documents to read first, in this order
1. `01-north-star-and-non-goals.md` — the anchor. Every other decision must be consistent with this.
2. `02-product-vision.md`
3. `03-prd.md`
4. `04-mvp-specification.md`
5. `05-user-stories-and-core-flows.md`
6. `06-ux-product-principles.md`
7. `07-functional-requirements.md`
8. `08-non-functional-requirements.md`
9. `09-agent-architecture-spec.md`
10. `10-agent-tool-specification.md`
11. `11-permission-confirmation-model.md`
12. `12-audit-undo-rollback-strategy.md`
13. `13-data-model.md`
14. `14-architecture-overview.md`
15. `15-ai-provider-abstraction.md`
16. `16-extensibility-plugin-model.md` (future/experimental — read for context, do not implement yet)
17. `17-self-extension-vision.md` (long-term research — read for context, reflect in design, do not implement the pipeline yet)
18. `18-sync-offline-local-first-strategy.md`
19. `19-security-privacy-model.md`
20. `20-error-handling-failure-modes.md`
21. `21-agent-evaluation-testing-strategy.md`
22. `22-roadmap.md`
23. `23-open-questions-decisions-log.md`

### Step 1 — Generate `design.md`
Before writing any implementation code, produce a `design.md` file that translates the product/UX documents above into a concrete visual and interaction design system. Use the **Impeccable skill set** available in the OpenCode skills directory to do this — specifically run through its `/init`, `/border`, `/animation`, and any other relevant Impeccable skill commands, and follow their conventions for how design systems should be structured in this environment. Work according to what those skills produce rather than inventing your own unrelated design process.

`design.md` must cover, at minimum:
- **Visual theme**: a playful, light/white-dominant theme — bright surfaces, generous whitespace, soft rounded corners, gentle shadows over heavy borders. Not sterile/corporate; not garish. Reference `06-ux-product-principles.md` §1 for the intended feel.
- **Color system**: a full palette built around a white/light base with a small set of playful accent colors used purposefully (status, priority, risk tiers) — never relying on color alone to carry meaning (accessibility requirement, `08-non-functional-requirements.md` §8).
- **Typography scale.**
- **Spacing/layout scale.**
- **Component states** for: task cards, calendar events, the scoped command bar (collapsed/expanded), the Home chat interface, confirmation/diff cards (Approve/Edit/Cancel), toasts for auto-executed actions, error states, empty states.
- **Borders** — use the Impeccable `/border` conventions to define a consistent, soft border treatment across cards/inputs/panels consistent with the light theme.
- **Animation** — use the Impeccable `/animation` conventions to define micro-animations for: confirmation cards appearing/resolving, task/event state transitions, agent "thinking" indicators, and page transitions. Motion should feel purposeful and light, never gratuitous or latency-adding (per `06-ux-product-principles.md` §1).
- **The Home AI hub layout** specifically — it should read visually like a clean Claude/ChatGPT-style conversation surface, with the ambient summary cards (today's plan, upcoming events, tasks due) integrated around it without competing with it.
- **Self-extension surface design**: even though the self-extension *pipeline* itself is not being implemented yet (`17-self-extension-vision.md`), `design.md` should include a design treatment for what "the AI is building you a new module" looks like end-to-end from a pure UI/UX standpoint — the spec-review screen, the sandboxed-test status view, the code/permissions review screen, and the installed-module appearance — so that when that pipeline is eventually implemented, the visual language already exists and is consistent with the rest of the product. Do not build the underlying pipeline logic now; design the surfaces it will eventually need.

Get UI/UX right here before any implementation code is written — this is the section that most needs to be impeccable, since it's the layer users will judge the entire product by.

### Step 2 — Confirm scope before coding
After producing `design.md`, summarize your understanding of MVP scope (per `04-mvp-specification.md`) and flag any ambiguity against `23-open-questions-decisions-log.md` before writing implementation code. Do not silently resolve open questions in that log by making an implementation choice — surface them.

### Step 3 — Build in roadmap order
Follow `22-roadmap.md` strictly: implement MVP scope only. Do not build Habits/Goals/Projects, integrations, multi-device sync, or any part of the self-extension pipeline in this pass, even though their designs may appear conceptually in `design.md`. If you find yourself implementing anything listed under "Explicitly out of MVP" in `04-mvp-specification.md`, stop and flag it.

### Non-negotiable architectural rules (do not deviate)
- The agent must never access the data store directly — only through Tools that call the same Service Layer the UI calls (`14-architecture-overview.md` §1).
- Risk-tier classification for every agent action must be enforced in code, never trusted from the model's own output (`11-permission-confirmation-model.md` §2).
- Every mutating tool must define an inverse operation or snapshot-based undo before it is considered complete (`10-agent-tool-specification.md`, `12-audit-undo-rollback-strategy.md`).
- No tool with filesystem/OS command execution or external communication capability may be added to the standard tool set (`10-agent-tool-specification.md` §8).
- Build against the Agent Evaluation golden scenario suite (`21-agent-evaluation-testing-strategy.md`) as you implement agent behavior — treat it as a regression suite from the start, not something added after the fact.

### Working style
Be opinionated where the documents leave room, but never override an explicit decision recorded in `23-open-questions-decisions-log.md`'s "Resolved decisions" table. Ask before making a call on anything in that document's "Open questions" table that materially affects architecture.

---

## Notes for you (not part of the pasted prompt)
- This assumes your OpenCode environment already has the "Impeccable" skill installed with `/init`, `/border`, `/animation` (and possibly others) available in its skills directory, as you described — the prompt above defers to whatever conventions those skills enforce rather than trying to redefine them here.
- If OpenCode's Impeccable skill produces a `design.md` that conflicts with anything in `06-ux-product-principles.md`, that's worth a manual review pass before implementation continues — the principles doc should win on intent (playful/white/trustworthy/explainable), Impeccable should win on execution mechanics (how borders/animation/tokens are technically expressed in this environment).
