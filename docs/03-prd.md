# FlowOS — Product Requirements Document (PRD)

## 1. Problem statement
Existing productivity tools require users to manually translate intent ("I need to reschedule my study block and everything downstream of it") into a sequence of UI operations across multiple screens. AI features bolted onto these tools are conversational overlays that suggest text, not agents that reliably act on real data with visible, reversible consequences. No open-source, self-hosted product currently combines: a real multi-module productivity data model, a tool-using agent grounded in that data, provider-agnostic model configuration, and a confirm/undo trust layer.

## 2. Goals
- G1: Ship a working single-user, self-hosted web app where an agent can read page-scoped context and execute real mutations via explicit tools.
- G2: Every agent mutation must be confirmable (where risk-tiered as such) and undoable.
- G3: Support at least two LLM providers (one hosted API, one local) via a common abstraction.
- G4: Deliver a Home view that acts as an AI-native hub with ambient context and pull-based tool access to the full workspace.
- G5: Establish the architectural seams (service layer, tool layer, manifest concepts) that make later extensibility possible without a rewrite.

## 3. Non-goals (see North Star §5 for full table)
Multi-device sync, mobile/desktop clients, multi-user/auth, third-party integrations, AI-generated custom modules, more than 2 providers, opaque long-term memory — all deferred, not rejected.

## 4. Users & personas
See Product Vision §2. Primary persona for design decisions: **"Dev Dana"** — self-hosts services, has an OpenAI/Anthropic key or runs Ollama locally, wants control and transparency over convenience-at-all-costs.

## 5. Feature scope (MVP)
### 5.1 Modules
- **Home** — AI-native hub, chat-like interface, ambient summary cards (today's plan, upcoming, deadlines), pull-based full-workspace tool access.
- **Tasks** — list/board view, CRUD, filters (status, priority, project tag, due date), scoped command bar.
- **Calendar** — day/week view, events, time blocks, conflict detection, scoped command bar.
- **Notes** — simple rich-text notes, tagging/linking to tasks, searchable by agent.

### 5.2 Agent capabilities
- Scoped, page-aware command bar on Tasks/Calendar/Notes.
- Home hub with full conversational interface and pull-based context.
- Tool-calling execution against the real service layer (see Agent Tool Specification).
- Planning for multi-step requests (e.g., reschedule + conflict resolution + reminder update analog).
- Confirmation flow for multi-item/destructive actions (see Permission & Confirmation Model).
- Action history / audit log with undo.
- Explicit, user-visible preference memory (not opaque long-term memory).

### 5.3 Settings
- AI Provider configuration: provider, model, API key or base URL, basic model parameters.
- Permission tier overrides (within allowed bounds — deletes never fully auto in MVP).
- Preference/memory list — view, edit, delete learned facts.

## 6. Success metrics (directional, for a pre-1.0 open-source project)
- Agent command success rate on a defined golden-scenario test suite (see Agent Evaluation Strategy) ≥ target threshold before calling MVP "done."
- Zero data-loss incidents from agent actions during internal testing (every mutation undoable).
- Time-to-first-successful-agent-action for a new self-hoster under a defined onboarding budget.

## 7. Constraints
- Local-first, single-user, self-hosted only for this phase.
- No mandatory external service dependency beyond the user's chosen LLM provider.
- Must not violate the "no separate AI database" principle — all agent tools call the same service layer as the UI.

## 8. Assumptions
- Users configuring local models (Ollama/LM Studio) accept that tool-calling reliability varies by model; the product must degrade gracefully (clear error, not silent wrong action) when a model can't reliably tool-call.
- Single device is sufficient for the validation phase; sync is a v2 concern.

## 9. Release criteria for MVP
- All core modules functional via UI independent of the agent.
- Agent can complete the full golden-scenario suite (see Agent Evaluation Strategy) at an acceptable pass rate.
- Undo works for 100% of mutation-capable tools.
- Confirmation flow cannot be bypassed for tools tagged destructive/bulk.
- At least 2 providers configurable and functionally tested end-to-end.
