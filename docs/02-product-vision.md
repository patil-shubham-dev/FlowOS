# FlowOS — Product Vision

## 1. One-line vision
An open-source, self-hosted, AI-native personal operating system where a context-aware agent acts on your real tasks, calendar, and notes — through the same services the UI uses — with every action confirmable and undoable.

## 2. Target users (in priority order)
1. **Self-hosting power users / developers** — comfortable running their own services, care about data ownership, want to bring their own LLM (local or API key).
2. **Privacy-conscious productivity enthusiasts** — currently use Notion/Obsidian/Todoist-style tools but distrust cloud-AI productivity SaaS with their calendar/task data.
3. **AI-forward early adopters** — want to test what "agentic" personal software actually feels like beyond chatbot demos.

Not initially targeted: enterprise teams, non-technical mainstream consumers who want a one-click hosted SaaS (that's a possible future distribution model, not the initial audience).

## 3. Primary use cases
- **Daily planning**: "What do I need to do today?" → synthesized plan from tasks, calendar, habits.
- **Ad hoc rescheduling**: "Move my maths session to 7pm and adjust the rest of today."
- **Load management**: "Make today lighter" / "I have college 9–4 tomorrow, fit everything else around it."
- **Bulk triage**: "Move all urgent tasks to tomorrow morning."
- **Notes → structured plan**: "Find everything I've written about my TTS project and turn it into a project plan."
- **Ambient awareness**: Home view surfaces today's plan, upcoming deadlines, habit streaks without being asked.

## 4. Core user journeys
1. **Onboarding**: install self-hosted instance → configure an AI provider (API key or local model) → land on Home.
2. **First real task**: add a few tasks/events manually via UI → try one natural-language command → see a confirmable diff → approve → see it applied and logged.
3. **Daily loop**: open Home → agent-generated summary of the day → adjust via natural language or manual edit, interchangeably.
4. **Trust-building loop**: user makes a destructive/bulk request → sees confirmation card → approves or edits → later checks action history → undoes something if needed. This loop, repeated, is what earns trust in the agent.
5. **Note-to-action**: capture loose notes over time → later ask agent to synthesize them into a task/project structure.

## 5. Differentiators (recap, see also North Star §3)
- Agent-as-a-layer over real services, not a bolted-on chatbot.
- Context scoped to the page you're on, plus a dedicated Home hub for cross-workspace, tool-driven queries.
- Confirm/undo/audit as core identity, not an afterthought.
- Provider-agnostic and self-hosted — your data, your model.
- Cross-module reasoning on one data graph (tasks + calendar + habits + notes).

## 6. Product horizon
| Horizon | Focus |
|---|---|
| **MVP** | Prove the core loop: scoped agent + tools + confirm + undo, over Tasks/Calendar/Notes/Home |
| **v1** | Add Habits, Goals, Projects; broaden tool set; refine planning quality |
| **v2** | Multi-device sync, offline-first sync protocol, first external integration (likely calendar import) |
| **Long-term / research** | Self-extending application platform — AI-assisted design and generation of new modules (e.g. attendance tracker), gated behind a mature extensibility model and a trusted agent core |

## 7. Success signal for the product bet
Users voluntarily choose the natural-language path over manual navigation for tasks it's capable of, *and* trust it enough to let low-risk actions run without confirmation after using it for a while. If users consistently reject or override the agent, or disable it, the core bet (§2 of North Star) has failed and the product needs to pivot toward manual UI with lighter-touch AI assistance.
