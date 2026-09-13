# FlowOS — Non-Functional Requirements

## 1. Performance
- Scoped-page agent responses should begin streaming feedback (even just "thinking"/plan step) within ~1s of submission to avoid perceived dead time.
- UI mutations (manual, non-agent) must remain instant/local-first — never blocked on network or LLM latency.
- Tool execution against the service layer should be fast enough that a multi-step plan (3–5 tool calls) completes in a few seconds under normal local conditions, excluding LLM latency.

## 2. Reliability
- No agent action may partially apply without being recorded accurately in the audit log (i.e., "what actually happened" must always be reconstructable, even after a partial failure).
- The application must remain fully usable via manual UI if the configured AI provider is unreachable.

## 3. Privacy & data ownership
- No workspace data leaves the user's machine/server except what is explicitly sent to their configured LLM provider as part of a request.
- No telemetry, analytics, or crash reporting enabled by default; if ever added, must be explicit opt-in.
- API keys and provider credentials stored encrypted at rest.

## 4. Portability / self-hosting
- Must run as a self-hosted single-user deployment without requiring any mandatory third-party cloud service (the LLM provider is the one necessary external dependency, and even that can be a local model).
- Should not assume a specific cloud host; deployment target is "runs on your own machine/server."

## 5. Cost control
- Context sent to the LLM per request should be minimized deliberately (scoped context on pages, pull-based on Home) rather than sending full workspace state by default.
- Users should be able to see, at least approximately, token/cost usage per session or per provider (exact mechanism is an implementation detail, but the requirement — visibility into cost — is a product requirement).

## 6. Observability & debuggability
- Every agent turn (input context, plan, tool calls, tool results, final response) should be inspectable in a debug/developer view, since this is an open-source project other contributors will need to debug agent behavior.
- Tool call failures must be distinguishable in logs from model/provider failures and from validation failures.

## 7. Extensibility (architectural readiness, not MVP feature)
- Service layer and tool layer must be structured so that new modules can be added without modifying the agent runtime itself (new tools register into a common registry).
- Data model should not hardcode single-user/single-workspace assumptions so deeply that multi-user/multi-workspace becomes a rewrite later (see Data Model doc).

## 8. Accessibility
- Keyboard navigability for all core flows (task/event CRUD, command bar, confirmation approve/reject).
- Sufficient color contrast even within a light/playful theme; risk tiers and conflicts must not rely on color alone.

## 9. Testability
- Agent behavior must be evaluable via a repeatable scenario suite independent of manual testing (see Agent Evaluation & Testing Strategy) so that prompt/tool/provider changes can be regression-tested.

## 10. Security
- Destructive and bulk tools are hard-gated at confirm-tier or above at the code level, not just by prompt instruction — the LLM's own claims about what tier an action is cannot be trusted as the sole enforcement mechanism (see Permission & Confirmation Model).
