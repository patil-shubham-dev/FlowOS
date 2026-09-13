// Ollama (local/OpenAI-compatible) adapter — SHAPE-READY, not implemented.
// The interface conformance, config normalization, and message-mapping
// helpers are real and unit-tested; sendMessage throws NOT_IMPLEMENTED until
// the next pass wires the /api/chat call. This keeps the "two providers"
// MVP requirement honest: Anthropic works, Ollama is scaffolded against the
// same interface (docs/15 §4) rather than half-built.
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

/** Normalize message blocks to Ollama /api/chat message shape. */
export function toOllamaMessages(messages: NormalizedMessage[]): Array<Record<string, unknown>> {
  return messages.map((m) => {
    const texts = m.blocks.filter((b) => b.type === "text").map((b) => (b as { text: string }).text);
    const toolCalls = m.blocks
      .filter((b) => b.type === "tool_use")
      .map((b) => {
        const use = b as { id: string; name: string; input: unknown };
        return { id: use.id, type: "function", function: { name: use.name, arguments: use.input } };
      });
    const toolResults = m.blocks
      .filter((b) => b.type === "tool_result")
      .map((b) => {
        const res = b as { toolUseId: string; content: string; isError?: boolean };
        return { tool_call_id: res.toolUseId, role: "tool", content: res.content };
      });
    if (toolResults.length > 0) {
      return { role: "user", content: toolResults.map((r) => r.content).join("\n") };
    }
    return {
      role: m.role,
      content: texts.join(""),
      ...(toolCalls.length > 0 ? { tool_calls: toolCalls } : {}),
    };
  });
}

export function resolveOllamaBaseUrl(config: ResolvedProviderConfig): string {
  return config.baseUrl ?? OLLAMA_DEFAULT_BASE_URL;
}

export class OllamaAdapter implements ProviderAdapter {
  readonly providerType = "ollama" as const;
  readonly supportsToolCalling = true;

  constructor(private readonly config: ResolvedProviderConfig) {}

  async sendMessage(_request: NormalizedRequest): Promise<NormalizedResponse> {
    throw new FlowOSError(
      "NOT_IMPLEMENTED",
      "The Ollama adapter is scaffolded but not wired yet — nothing was sent.",
      "Use the Anthropic provider for now; Ollama lands in the next pass.",
    );
  }

  async validateConfig(): Promise<void> {
    throw new FlowOSError(
      "NOT_IMPLEMENTED",
      "Ollama validation is not wired yet.",
      "Use the Anthropic provider for now; Ollama lands in the next pass.",
    );
  }

  estimateCost(_usage: TokenUsage): number | null {
    return null; // local model — cost is meaningless (docs/15 §2)
  }
}
