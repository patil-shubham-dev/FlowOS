# FlowOS — AI Provider Abstraction

## 1. Goal
The Agent Runtime must be able to run against any configured provider without its planning, risk-classification, or tool-execution logic knowing which provider is in use. Providers differ in auth, request/response shape, and tool-calling support — the abstraction's job is to normalize all of that behind one interface.

## 2. Provider adapter interface (conceptual)
Each provider adapter implements:
- `sendMessage(normalizedRequest) → normalizedResponse` — accepts a normalized conversation + tool schema, returns normalized text/tool-call output.
- `supportsToolCalling: boolean` — capability flag, checked before allowing agent features against that provider/model.
- `validateConfig(config) → ok | error` — a lightweight test call used when the user saves provider settings, so failures surface immediately in Settings rather than on first real use.
- `estimateCost(usage) → number | null` — optional, used for the cost-visibility requirement (Non-Functional Requirements §5); returns null for local models where cost isn't meaningful.

## 3. Provider configuration schema (per entry, conceptual — maps to ProviderConfig entity)
- `provider_type`: openai | anthropic | google | openrouter | ollama | lm_studio | custom_openai_compatible
- `model`: string identifier
- `api_key`: encrypted, optional (not required for local providers)
- `base_url`: optional, required for local/custom-compatible providers
- `params`: temperature, max_tokens, and other basic generation settings
- `is_default`: bool

## 4. MVP provider scope
Exactly **two** adapters implemented at launch:
1. One **hosted API provider** (Anthropic or OpenAI — pick one as the reference implementation; the interface must make adding the other trivial).
2. One **local/OpenAI-compatible provider** (Ollama, since it's the most common self-hosted option and speaks an OpenAI-compatible-ish API).

All other listed providers (Google, OpenRouter, LM Studio, generic custom OpenAI-compatible) are architecturally supported by the same interface but implemented post-MVP — adding them should be "write one adapter," not "touch the agent runtime."

## 5. Tool-calling capability handling
- Before allowing agent features to run against a given provider/model, the system checks `supportsToolCalling`.
- If false or unverified, the UI must clearly disable/label agent features for that configuration rather than letting the agent silently fail or hallucinate tool results as text.

## 6. Failure handling
- Auth errors, timeouts, and malformed responses are caught at the adapter level and surfaced as normalized error types up to the Agent Runtime, which then produces the specific, actionable error required by UX Principles §4 (never a generic failure message).

## 7. Cost/token visibility
- Adapters return usage metadata (tokens in/out) where the provider exposes it; the Agent Runtime aggregates this per session for the cost-visibility requirement. Exact display mechanism (running total, per-message estimate) is an implementation detail for later.

## 8. What this abstraction explicitly does NOT do
- It does not attempt to abstract away model *quality* or *behavioral* differences (e.g., how well a model plans) — that's a prompt/eval concern (see Agent Evaluation & Testing Strategy), not an interface concern.
- It does not support streaming multi-modal input in MVP scope unless a specific product need arises (text in, text/tool-calls out is sufficient).
