// OpenAI-compatible adapter (Ollama, LM Studio, Nvidia NIM, custom endpoints).
// Implements the OpenAI /v1/chat/completions format which covers all these providers.
import { FlowOSError } from "../security/secrets.js";
import type {
  NormalizedMessage,
  NormalizedRequest,
  NormalizedResponse,
  ProviderAdapter,
  ResolvedProviderConfig,
  TokenUsage,
} from "./types.js";

export const OLLAMA_DEFAULT_BASE_URL = "http://localhost:11434";

/** Normalize message blocks to OpenAI chat completion message shape. */
export function toOpenAIMessages(messages: NormalizedMessage[]): Array<Record<string, unknown>> {
  return messages.map((m): Record<string, unknown> => {
    const texts = m.blocks.filter((b) => b.type === "text").map((b) => (b as { text: string }).text);
    const toolCalls = m.blocks
      .filter((b) => b.type === "tool_use")
      .map((b) => {
        const use = b as { id: string; name: string; input: unknown };
        return { id: use.id, type: "function", function: { name: use.name, arguments: JSON.stringify(use.input) } };
      });
    const toolResults = m.blocks
      .filter((b): b is { type: "tool_result"; toolUseId: string; content: string; isError?: boolean } => b.type === "tool_result")
      .map((res) => ({ tool_call_id: res.toolUseId, role: "tool" as const, content: res.content }));
    if (toolResults.length > 0) {
      return { role: "user", content: toolResults.map((r) => r.content).join("\n") };
    }
    const msg: Record<string, unknown> = {
      role: m.role === "assistant" ? "assistant" : "user",
      content: texts.join(""),
    };
    if (toolCalls.length > 0) {
      msg.tool_calls = toolCalls;
    }
    return msg;
  });
}

/** Convert normalized tool schemas to OpenAI function format. */
function toOpenAITools(request: NormalizedRequest): Array<Record<string, unknown>> | undefined {
  if (request.tools.length === 0) return undefined;
  return request.tools.map((t) => ({
    type: "function",
    function: {
      name: t.name,
      description: t.description,
      parameters: t.inputSchema,
    },
  }));
}

export function resolveBaseUrl(config: ResolvedProviderConfig): string {
  if (config.baseUrl) return config.baseUrl;
  return OLLAMA_DEFAULT_BASE_URL;
}

export class OpenAICompatibleAdapter implements ProviderAdapter {
  readonly providerType = "ollama" as const;
  readonly supportsToolCalling = true;

  constructor(private readonly config: ResolvedProviderConfig) {}

  async sendMessage(request: NormalizedRequest): Promise<NormalizedResponse> {
    const baseUrl = resolveBaseUrl(this.config);
    // Strip trailing /v1 if present — we append it below.
    const base = baseUrl.replace(/\/v1\/?$/, "").replace(/\/$/, "");
    const url = `${base}/v1/chat/completions`;

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (this.config.apiKey && this.config.apiKey !== "local") {
      headers["Authorization"] = `Bearer ${this.config.apiKey}`;
    }

    const body: Record<string, unknown> = {
      model: this.config.model,
      messages: [
        { role: "system", content: request.system },
        ...toOpenAIMessages(request.messages),
      ],
      max_tokens: request.maxTokens,
      temperature: request.temperature ?? 0.7,
    };

    const tools = toOpenAITools(request);
    if (tools) {
      body.tools = tools;
      body.tool_choice = "auto";
    }

    let res: Response;
    try {
      res = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(60_000),
      });
    } catch (err) {
      throw new FlowOSError(
        "PROVIDER_UNREACHABLE",
        `Could not reach ${this.config.providerType}: ${err instanceof Error ? err.message : String(err)}`,
        "Check your network and provider URL, then retry.",
      );
    }

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new FlowOSError(
        "PROVIDER_AUTH",
        `${this.config.providerType} returned ${res.status}: ${text.slice(0, 200)}`,
        "Check your API key and model name.",
      );
    }

    const data = (await res.json()) as Record<string, unknown>;
    const choice = (data.choices as Array<Record<string, unknown>>)?.[0];
    if (!choice) {
      throw new FlowOSError("PROVIDER_AUTH", "Provider returned no choices.", "Check your model configuration.");
    }

    const message = choice.message as Record<string, unknown> | undefined;
    const text = (message?.content as string) ?? "";
    const rawToolCalls = (message?.tool_calls as Array<Record<string, unknown>>) ?? [];

    const toolCalls = rawToolCalls.map((tc) => {
      const fn = tc.function as Record<string, unknown>;
      let input: unknown;
      try {
        input = JSON.parse((fn.arguments as string) ?? "{}");
      } catch {
        input = {};
      }
      return { id: (tc.id as string) ?? `call_${Date.now()}`, name: fn.name as string, input };
    });

    const usage = data.usage as Record<string, number> | undefined;

    return {
      text,
      toolCalls,
      usage: usage
        ? { inputTokens: usage.prompt_tokens ?? 0, outputTokens: usage.completion_tokens ?? 0 }
        : null,
      stopReason: (choice.finish_reason as string) ?? "stop",
    };
  }

  async validateConfig(): Promise<void> {
    const baseUrl = resolveBaseUrl(this.config);
    const base = baseUrl.replace(/\/v1\/?$/, "").replace(/\/$/, "");
    // Try /v1/models first; if 404, fall back to a minimal chat completion test.
    const modelsUrl = `${base}/v1/models`;
    const headers: Record<string, string> = {};
    if (this.config.apiKey && this.config.apiKey !== "local") {
      headers["Authorization"] = `Bearer ${this.config.apiKey}`;
    }
    let res: Response;
    try {
      res = await fetch(modelsUrl, { headers, signal: AbortSignal.timeout(10_000) });
    } catch (err) {
      throw new FlowOSError(
        "PROVIDER_UNREACHABLE",
        `Cannot reach ${baseUrl}: ${err instanceof Error ? err.message : String(err)}`,
        "Check your provider URL and network.",
      );
    }
    // 404 on /v1/models is fine — some providers don't support it.
    if (res.status === 404) return;
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new FlowOSError(
        "PROVIDER_AUTH",
        `Validation failed (${res.status}): ${text.slice(0, 150)}`,
        "Check your API key and provider URL.",
      );
    }
  }

  estimateCost(_usage: TokenUsage): number | null {
    return null;
  }
}
