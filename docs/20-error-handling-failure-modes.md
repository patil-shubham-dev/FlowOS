# FlowOS — Error Handling & Failure Modes

## 1. Principle
Every failure mode below must produce a **specific, actionable message** — never a generic "something went wrong," and never a silent no-op or (worse) a silently *wrong* action. This directly supports the trust bet in the North Star document.

## 2. Failure catalog

**Provider unreachable / auth failure**
- Detection: adapter-level (AI Provider Abstraction §6).
- Response: specific error naming the provider and cause ("Couldn't reach Anthropic: invalid API key"), with a direct link to Settings.
- App remains fully usable via manual UI.

**Model lacks tool-calling support**
- Detection: capability flag check before agent features are enabled for that config.
- Response: agent features disabled/labeled for that provider config with an explanation, rather than attempting and silently failing.

**Malformed tool call (model requests a tool with invalid arguments)**
- Detection: schema validation before execution.
- Response: reject the call, do not execute, surface to the model as a correctable error (allow one retry/self-correction attempt), and if it persists, report to the user that the request couldn't be reliably translated into an action, with a suggestion to rephrase.

**Ambiguous request (scope or intent unclear)**
- Detection: planning stage — required fields for a confident plan are missing or under-specified.
- Response: agent asks a clarifying question rather than guessing, especially before Confirm-tier+ actions (UX Principles §3, Functional Requirements FR-6.8).

**Partial multi-step execution failure**
- Detection: verifier step catches a failed tool call mid-plan.
- Response: halt remaining steps, report precisely what completed and what didn't, and offer to roll back the completed portion (using the same undo mechanism) rather than leaving a half-applied plan unexplained.

**Verification mismatch (tool reported success but resulting state doesn't match intent)**
- Detection: verifier re-fetch step (Agent Architecture §6).
- Response: treat as a failure, not a success — report the discrepancy rather than confirming completion incorrectly.

**Undo no longer possible**
- Detection: a subsequent independent change invalidated the prior snapshot (Audit/Undo/Rollback §5).
- Response: explain clearly why undo isn't available, rather than failing silently or corrupting state.

**Rate limit / cost cap reached**
- Detection: adapter or usage-tracking layer.
- Response: clear message about the limit hit and when/how to proceed (e.g., switch provider, wait, adjust cap in Settings).

**Data store failure (local storage error)**
- Detection: service layer.
- Response: surfaced distinctly from LLM/provider errors, since the cause and remedy are completely different — never conflate "the AI failed" with "the database failed."

## 3. General UX rule for all failures
The user should always be able to answer, from the message alone: *what failed, why, and what to do next.* If any of those three is missing, the error message is not acceptable.
