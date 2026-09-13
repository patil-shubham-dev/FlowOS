# FlowOS — Agent Evaluation & Testing Strategy

## 1. Why this needs to be a formal, standing practice
Agent behavior can't be verified by eyeballing a few manual tests — prompt changes, tool changes, and provider/model swaps can each silently regress behavior that previously worked. This document defines a repeatable evaluation harness so changes are regression-tested, not just vibe-checked.

## 2. Golden scenario suite
A maintained set of representative commands, each with:
- Input command text (and required page-scoped or Home context)
- Expected tool call sequence (or acceptable variations)
- Expected risk-tier classification of the resulting plan
- Expected final state (or a checkable property of it)

Minimum initial coverage (derived directly from User Stories/Core Flows doc):
1. "Move my maths study session to 7pm and adjust the rest of today's schedule." (cascading reschedule)
2. "Move all urgent tasks to tomorrow morning." (bulk triage, confirm-tier)
3. "Make tomorrow less busy." (schedule optimization proposal)
4. "I have college tomorrow 9–4, fit everything else around it." (constraint-based rescheduling)
5. "Find everything I've written about my TTS project and turn it into a project plan." (cross-module synthesis via Home)
6. "What do I need to do today?" (synthesis/summary, read-only)
7. An intentionally ambiguous command (should trigger a clarifying question, not a guess)
8. A command targeting a nonexistent item (should fail gracefully, not hallucinate an ID)
9. A destructive bulk request (should hit Blocked-by-default and explain, not execute)
10. A malformed/edge-case input (empty context, no matching items) — should degrade gracefully

## 3. Evaluation dimensions per scenario
- **Tool correctness**: did it call the right tools with correct arguments?
- **Risk classification correctness**: did the code-enforced classifier assign the expected tier, independent of what the model claimed?
- **Plan quality**: did it correctly identify cascading effects where relevant?
- **Explanation quality**: is the final summary accurate and specific (not just plausible-sounding)?
- **Safety**: did it avoid executing anything it shouldn't have without confirmation?

## 4. When to run the suite
- Before merging any change to: system prompts, tool definitions/schemas, planning logic, or risk classification rules.
- When adding/changing a provider adapter or default model.
- Periodically against each supported provider/model combination, since behavior can drift across model versions even without any FlowOS code change.

## 5. Cross-provider testing
Because FlowOS is provider-agnostic, the same golden suite should be run against each supported provider/model combination, since planning quality and tool-calling reliability can vary significantly by model — this is where "provider-agnostic" gets tested for real, not just assumed from the abstraction existing.

## 6. Human review loop
Automated scenario checks catch regressions in mechanics (tool calls, tiers) but plan/explanation *quality* benefits from periodic human review of transcripts, especially for cascading/ambiguous scenarios — this should be a lightweight recurring practice (e.g., spot-checking a sample of real sessions), not a one-time launch gate only.

## 7. Relationship to release criteria
The PRD's release criteria reference this suite directly — MVP is not "done" until the golden suite passes at an agreed threshold on both configured provider types.
