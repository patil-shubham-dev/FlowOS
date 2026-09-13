# FlowOS — Roadmap

## MVP
- Modules: Home (AI hub), Tasks, Calendar, Notes, Settings.
- Full agent loop: scoped + pull-based context, planning, code-enforced risk tiers, confirm gate, execution, verification, audit log, undo.
- 2 providers: one hosted API, one local/OpenAI-compatible.
- Explicit, user-visible preference memory only.
- Golden scenario evaluation suite passing on both providers.
- No sync, no integrations, no self-extension, no Habits/Goals/Projects.

## v1
- Add Habits, Goals, Projects modules (entities, services, tools, UI, scoped command bars).
- Expand tool set accordingly (`createHabit`, `logHabit`, `analyzeHabit`, `createGoal`, `createProject`, etc.).
- Broaden planning quality (richer multi-module reasoning, e.g., habit-aware scheduling: "move my workout to when I usually complete it").
- Add remaining priority providers (Google, OpenRouter, LM Studio, generic custom OpenAI-compatible) behind the existing abstraction.
- Refine memory: still explicit/visible, but potentially richer (e.g., pattern-detected suggestions the user must approve before they become a saved preference).

## v2
- Multi-device sync (design sketch in Sync/Offline/Local-First Strategy becomes implementation).
- Offline queue for agent commands.
- First external integration — most likely calendar import/export (e.g., Google Calendar) since it's the most directly requested and highest-value; email/deadline-extraction integrations follow after, given their higher complexity (parsing, false-positive risk) and higher-risk action surface (creating calendar events from inferred content).
- Possibly: mobile client (read + light interaction) once sync exists to make it worthwhile.

## Long-term / Experimental Research
- Extensibility & Plugin Model → real implementation (Module SDK, manifest runtime, sandboxing).
- Self-Extension pipeline (AI-generated modules) — gated behind a mature plugin runtime and a core agent with a strong trust/reliability track record from v1/v2 usage.
- Desktop native client.
- Possible hosted/managed offering as a separate distribution model (not required for the open-source self-hosted product to succeed).

## Sequencing rationale
Each phase is gated on the previous phase actually working, not on a calendar date. In particular: self-extension does not start until (a) the plugin/module runtime exists and has been used for at least one hand-authored module, and (b) the core agent has a track record of reliable, trusted behavior on ordinary data — both preconditions, not just "after MVP ships."
