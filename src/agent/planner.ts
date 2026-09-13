// Planner service — bridges user instruction to a structured plan via the
// configured LLM provider. Called by the turn endpoint when the frontend
// sends no explicit plan (free-text input).
//
// Architecture: planner builds a normalized request (system prompt + tool
// schemas + instruction), calls the provider adapter, and parses the
// tool_use blocks into ExplicitPlan steps. The backend owns the API key;
// the frontend never sees it.
//
// Failure modes (docs/20):
// - No provider configured → specific error with Settings link
// - Provider unreachable → specific error with retry guidance
// - Malformed tool call (unknown tool name) → one self-correction retry,
//   then honest error with the bad tool name surfaced
// - Invalid tool name after retry → specific error naming the unknown tool
import { FlowOSError } from "../security/secrets.js";
import type { ProviderConfigService } from "../services/providers.js";
import type { AssembledContext } from "./context.js";
import { TOOL_REGISTRY, type ToolName, ALL_TOOL_NAMES } from "../tools/definitions.js";
import { TOOL_INPUT_SCHEMAS } from "../tools/toolInputSchemas.js";
import { buildAdapter } from "../providers/validate.js";
import type { NormalizedToolSchema, NormalizedResponse, ResolvedProviderConfig } from "../providers/types.js";
import type { ExplicitPlan } from "./loop.js";

const SYSTEM_PROMPT = `You are FlowOS, an AI productivity assistant. You have access to tools for managing tasks, calendar events, notes, and user preferences.

RULES:
1. For greetings, small talk, or questions that don't need tools, respond with a friendly text message (no tool calls).
2. For requests that need tools (create, search, update, delete, list), use the appropriate tool calls.
3. Prefer the simplest single tool call that satisfies the request.
4. For multi-step requests, call multiple tools in sequence.
5. For read-only requests (searching, listing, checking), use read tools.
6. For mutations (create, update, delete, move), use the appropriate tool.
7. If the request is ambiguous or underspecified, prefer an empty plan over guessing.
8. Never call tools that don't exist. Only use the tools listed below.
9. For delete operations on bulk/filter-based selectors, prefer itemized explicit IDs over filters.
10. For update operations, prefer explicit IDs over filters when the user names specific items.
11. When a request involves rescheduling, check for cascading effects (flexible events that overlap).
12. Be warm, helpful, and concise. Keep responses under 3 sentences for simple questions.

AVAILABLE TOOLS:
`;

export interface PlannerContext {
  surface: string;
  scopeIds?: string[];
  selectedId?: string | null;
  filters?: Record<string, unknown> | null;
  preferences?: Array<{ id: string; statement: string }>;
  ambient?: { tasksOpen: number; eventsToday: number; notes: number } | null;
}

export interface PlanResult {
  plan: ExplicitPlan | null;
  /** Conversational text response when no tools are needed (greetings, simple chat). */
  textResponse: string | null;
  error: FlowOSError | null;
  usage: { inputTokens: number; outputTokens: number } | null;
}

/**
 * Build the system prompt with workspace context and tool descriptions.
 * Context is included so the planner can reason about existing state
 * without needing extra read calls for simple requests.
 */
function buildSystemPrompt(ctx: PlannerContext): string {
  const tools = ALL_TOOL_NAMES
    .map((name) => {
      const def = TOOL_REGISTRY[name];
      const schema = TOOL_INPUT_SCHEMAS[name];
      return `- ${name}: ${def.description} Input: ${JSON.stringify(schema)}`;
    })
    .join("\n");

  let contextBlock = "";
  if (ctx.ambient) {
    contextBlock = [
      `\nWORKSPACE STATE:`,
      `- Open tasks: ${ctx.ambient.tasksOpen}`,
      `- Events today: ${ctx.ambient.eventsToday}`,
      `- Notes: ${ctx.ambient.notes}`,
    ].join("\n");
  }
  if (ctx.scopeIds && ctx.scopeIds.length > 0) {
    contextBlock += `\n- Visible items in scope: ${ctx.scopeIds.length}`;
  }
  if (ctx.selectedId) {
    contextBlock += `\n- Selected item: ${ctx.selectedId}`;
  }
  if (ctx.preferences && ctx.preferences.length > 0) {
    contextBlock += `\n- User preferences:\n${ctx.preferences.map((p) => `  - ${p.statement}`).join("\n")}`;
  }

  return `${SYSTEM_PROMPT}${tools}${contextBlock}\n\nRespond with tool calls only. Do not write explanations.`;
}

/**
 * Resolve the default provider config and build a ResolvedProviderConfig.
 * Throws specific errors for missing config or missing API key.
 */
function resolveProvider(svc: ProviderConfigService): ResolvedProviderConfig {
  const all = svc.listConfigs();
  const defaultConfig = all.find((c) => c.isDefault) ?? all[0];
  if (!defaultConfig) {
    throw new FlowOSError(
      "NOT_FOUND",
      "No AI provider is configured. The agent needs a provider to plan actions.",
      "Open Settings → AI Provider and add a provider (API key or local model).",
    );
  }
  if (!defaultConfig.apiKeyEncrypted && defaultConfig.providerType !== "ollama" && defaultConfig.providerType !== "lm_studio") {
    throw new FlowOSError(
      "PROVIDER_AUTH",
      `Provider "${defaultConfig.providerType}/${defaultConfig.model}" has no API key configured.`,
      "Open Settings → AI Provider and add an API key.",
    );
  }
  return {
    providerType: defaultConfig.providerType,
    model: defaultConfig.model,
    apiKey: svc.revealApiKey(defaultConfig),
    baseUrl: defaultConfig.baseUrl,
    params: defaultConfig.params,
  };
}

/**
 * Parse a normalized response into ExplicitPlan steps.
 * Maps tool_use blocks to { tool, input } pairs, validating tool names.
 * Returns the plan and any bad tool name that caused the failure.
 */
function parseResponse(res: NormalizedResponse): { plan: ExplicitPlan | null; badTool: string | null } {
  if (res.toolCalls.length === 0) {
    return { plan: null, badTool: null };
  }
  const steps: ExplicitPlan["steps"] = [];
  for (const tc of res.toolCalls) {
    if (!ALL_TOOL_NAMES.includes(tc.name as ToolName)) {
      return { plan: null, badTool: tc.name };
    }
    steps.push({ tool: tc.name as ToolName, input: tc.input });
  }
  return { plan: { steps }, badTool: null };
}

/**
 * Plan a turn: call the provider, parse the response into a plan.
 * Returns the plan or a specific error — never throws.
 */
export async function planTurn(
  providerSvc: ProviderConfigService,
  ctx: PlannerContext,
  instruction: string,
): Promise<PlanResult> {
  let resolved: ResolvedProviderConfig;
  try {
    resolved = resolveProvider(providerSvc);
  } catch (err) {
    return {
      plan: null,
      textResponse: null,
      error: err instanceof FlowOSError ? err : new FlowOSError(
        "NOT_FOUND",
        "Could not resolve a provider configuration.",
        "Open Settings → AI Provider and configure a provider.",
      ),
      usage: null,
    };
  }

  const adapter = buildAdapter(resolved);
  if (!adapter.supportsToolCalling) {
    return {
      plan: null,
      textResponse: null,
      error: new FlowOSError(
        "NOT_IMPLEMENTED",
        `Provider "${resolved.providerType}/${resolved.model}" does not support tool calling.`,
        "Switch to a provider that supports tool calling (e.g., Anthropic Claude).",
      ),
      usage: null,
    };
  }

  const systemPrompt = buildSystemPrompt(ctx);
  const tools: NormalizedToolSchema[] = ALL_TOOL_NAMES.map((name) => ({
    name,
    description: TOOL_REGISTRY[name].description,
    inputSchema: TOOL_INPUT_SCHEMAS[name],
  }));

  let response: NormalizedResponse;
  try {
    response = await adapter.sendMessage({
      system: systemPrompt,
      messages: [{ role: "user", blocks: [{ type: "text", text: instruction }] }],
      tools,
      maxTokens: 1024,
    });
  } catch (err) {
    return {
      plan: null,
      textResponse: null,
      error: err instanceof FlowOSError ? err : new FlowOSError(
        "PROVIDER_UNREACHABLE",
        `Provider request failed: ${err instanceof Error ? err.message : String(err)}`,
        "Check your network and provider configuration, then retry.",
      ),
      usage: null,
    };
  }

  const firstParse = parseResponse(response);

  // If no tool calls, check if there's a text response (conversational).
  if (response.toolCalls.length === 0 && response.text) {
    return { plan: null, textResponse: response.text, error: null, usage: response.usage };
  }

  // Self-correction retry: if the planner produced an unknown tool name,
  // retry once with an explicit correction prompt naming the bad tool.
  if (firstParse.badTool) {
    const retrySystem = systemPrompt +
      `\n\nCORRECTION: Your previous response used "${firstParse.badTool}", which is not a valid tool.` +
      `\nValid tools are: ${ALL_TOOL_NAMES.join(", ")}.` +
      `\nRetry using only the tools listed above. Do not invent tool names.`;
    let retryResponse: NormalizedResponse;
    try {
      retryResponse = await adapter.sendMessage({
        system: retrySystem,
        messages: [
          { role: "user", blocks: [{ type: "text", text: instruction }] },
          { role: "assistant", blocks: [{ type: "tool_use", id: "retry", name: firstParse.badTool, input: {} }] },
          { role: "user", blocks: [{ type: "text", text: `"${firstParse.badTool}" is not a valid tool. Please retry with a valid tool name.` }] },
        ],
        tools,
        maxTokens: 1024,
      });
    } catch (err) {
      return {
        plan: null,
        textResponse: null,
        error: new FlowOSError(
          "MALFORMED_TOOL_CALL",
          `Planner used unknown tool "${firstParse.badTool}" and retry also failed.`,
          `Valid tools: ${ALL_TOOL_NAMES.join(", ")}. Try rephrasing your request.`,
        ),
        usage: response.usage,
      };
    }
    const retryParse = parseResponse(retryResponse);
    if (retryParse.badTool) {
      return {
        plan: null,
        textResponse: null,
        error: new FlowOSError(
          "MALFORMED_TOOL_CALL",
          `Planner used unknown tool "${retryParse.badTool}" even after correction.`,
          `Valid tools: ${ALL_TOOL_NAMES.join(", ")}. Try rephrasing your request.`,
        ),
        usage: retryResponse.usage,
      };
    }
    return { plan: retryParse.plan, textResponse: null, error: null, usage: retryResponse.usage };
  }

  return { plan: firstParse.plan, textResponse: null, error: null, usage: response.usage };
}
