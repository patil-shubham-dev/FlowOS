// Anthropic reference adapter (verdict session 4: Anthropic first; OpenAI
// follows against the same interface as an abstraction check).
// API surface verified against the installed @anthropic-ai/sdk@0.125.0
// typings (messages.create, tool_use blocks, input_schema, error classes).
import Anthropic, {
  APIConnectionError,
  APIConnectionTimeoutError,
  AuthenticationError,
  BadRequestError,
  RateLimitError,
} from "@anthropic-ai/sdk";
import { FlowOSError } from "../security/secrets.js";
import type {
  NormalizedMessage,
  NormalizedRequest,
  NormalizedResponse,
  NormalizedToolSchema,
  ProviderAdapter,
  ResolvedProviderConfig,
  TokenUsage,
} from "./types.js";

export interface AnthropicRawResponse {
  content: Array<Record<string, unknown>>;
  usage?: { input_tokens?: number; output_tokens?: number };
  stop_reason?: string | null;
}

export interface AnthropicClientLike {
  messages: {
    create(params: Record<string, unknown>): Promise<AnthropicRawResponse>;
  };
}

/** Single cast boundary: the SDK's overloaded create is narrowed once here. */
function adaptSdk(client: Anthropic): AnthropicClientLike {
  return {
    messages: {
      create: (params: Record<string, unknown>) =>
        client.messages.create(
          params as unknown as Parameters<Anthropic["messages"]["create"]>[0],
        ) as unknown as Promise<AnthropicRawResponse>,
    },
  };
}

export function toAnthropicTools(tools: NormalizedToolSchema[]): Array<Record<string, unknown>> {
  return tools.map((t) => ({ name: t.name, description: t.description, input_schema: t.inputSchema }));
}

export function toAnthropicMessages(messages: NormalizedMessage[]): Array<Record<string, unknown>> {
  return messages.map((m) => ({
    role: m.role,
    content: m.blocks.map((b) => {
      if (b.type === "text") return { type: "text", text: b.text };
      if (b.type === "tool_use") return { type: "tool_use", id: b.id, name: b.name, input: b.input };
      return {
        type: "tool_result",
        tool_use_id: b.toolUseId,
        content: b.content,
        ...(b.isError ? { is_error: true } : {}),
      };
    }),
  }));
}

export function fromAnthropicResponse(res: AnthropicRawResponse): NormalizedResponse {
  const text: string[] = [];
  const toolCalls: NormalizedResponse["toolCalls"] = [];
  for (const block of res.content) {
    if (block["type"] === "text" && typeof block["text"] === "string") {
      text.push(block["text"] as string);
    } else if (block["type"] === "tool_use") {
      toolCalls.push({
        id: String(block["id"] ?? ""),
        name: String(block["name"] ?? ""),
        input: block["input"] ?? {},
      });
    }
  }
  return {
    text: text.join(""),
    toolCalls,
    usage:
      res.usage
        ? { inputTokens: res.usage.input_tokens ?? 0, outputTokens: res.usage.output_tokens ?? 0 }
        : null,
    stopReason: res.stop_reason ?? "unknown",
  };
}

function statusOf(err: unknown): number | null {
  if (typeof err === "object" && err !== null && "status" in err && typeof (err as { status: unknown }).status === "number") {
    return (err as { status: number }).status;
  }
  return null;
}

/** Adapter-level normalization (docs/15 §6): never a generic failure. */
export function mapAnthropicError(err: unknown, providerLabel: string): FlowOSError {
  if (err instanceof AuthenticationError || statusOf(err) === 401) {
    return new FlowOSError(
      "PROVIDER_AUTH",
      `Couldn't reach Anthropic: invalid API key (${providerLabel}).`,
      "Open Settings → AI Provider and check the key, then try again.",
    );
  }
  if (err instanceof RateLimitError || statusOf(err) === 429) {
    return new FlowOSError(
      "PROVIDER_RATE_LIMIT",
      `Anthropic rate limit hit (${providerLabel}). Nothing was executed.`,
      "Wait a minute and retry, switch provider/model, or set a cap in Settings.",
    );
  }
  if (
    err instanceof APIConnectionError ||
    err instanceof APIConnectionTimeoutError ||
    (typeof err === "object" && err !== null && "code" in err && ["ECONNREFUSED", "ENOTFOUND", "ETIMEDOUT"].includes(String((err as { code: unknown }).code)))
  ) {
    return new FlowOSError(
      "PROVIDER_UNREACHABLE",
      `Couldn't reach Anthropic (${providerLabel}): connection failed. The app still works manually.`,
      "Check your network, then retry the command.",
      err instanceof Error ? err.message : String(err),
    );
  }
  if (err instanceof BadRequestError || statusOf(err) === 400) {
    return new FlowOSError(
      "VALIDATION",
      `Anthropic rejected the request (${providerLabel}) — possibly an unknown model id.`,
      "Check the model id in Settings → AI Provider and try again.",
      err instanceof Error ? err.message : String(err),
    );
  }
  if (err instanceof FlowOSError) return err;
  return new FlowOSError(
    "PROVIDER_UNREACHABLE",
    `Anthropic request failed (${providerLabel}). The app still works manually.`,
    "Retry the command; if it persists, check Settings → AI Provider.",
    err instanceof Error ? err.message : String(err),
  );
}

export class AnthropicAdapter implements ProviderAdapter {
  readonly providerType = "anthropic" as const;
  readonly supportsToolCalling = true;

  private readonly client: AnthropicClientLike;
  private readonly model: string;
  private readonly label: string;

  constructor(
    config: ResolvedProviderConfig,
    clientFactory?: (config: ResolvedProviderConfig) => AnthropicClientLike,
  ) {
    if (!config.apiKey) {
      throw new FlowOSError(
        "PROVIDER_AUTH",
        "Anthropic adapter needs an API key.",
        "Add the key in Settings → AI Provider.",
      );
    }
    this.model = config.model;
    this.label = `${config.providerType}/${config.model}`;
    this.client =
      clientFactory?.(config) ??
      adaptSdk(
        new Anthropic({
          apiKey: config.apiKey,
          ...(config.baseUrl ? { baseURL: config.baseUrl } : {}),
          timeout: 60_000,
          maxRetries: 1,
        }),
      );
  }

  async sendMessage(request: NormalizedRequest): Promise<NormalizedResponse> {
    const maxTokens =
      typeof request.maxTokens === "number" && request.maxTokens > 0 ? request.maxTokens : 1024;
    try {
      const res = await this.client.messages.create({
        model: this.model,
        max_tokens: maxTokens,
        ...(request.temperature !== undefined ? { temperature: request.temperature } : {}),
        system: request.system,
        messages: toAnthropicMessages(request.messages),
        tools: toAnthropicTools(request.tools),
      });
      return fromAnthropicResponse(res);
    } catch (err) {
      throw mapAnthropicError(err, this.label);
    }
  }

  /** 1-token ping: surfaces bad keys/models in Settings, not mid-task. */
  async validateConfig(): Promise<void> {
    try {
      await this.client.messages.create({
        model: this.model,
        max_tokens: 1,
        messages: [{ role: "user", content: [{ type: "text", text: "ping" }] }],
      });
    } catch (err) {
      throw mapAnthropicError(err, this.label);
    }
  }

  estimateCost(_usage: TokenUsage): number | null {
    // Deliberately null: no hardcoded price table to rot. Cost visibility
    // (docs/08 §5) comes from token usage aggregated per session.
    return null;
  }
}
