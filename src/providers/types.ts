// Provider abstraction (docs/15-ai-provider-abstraction.md).
// The agent runtime builds a normalized request and never knows which
// provider serves it. Adapters normalize auth, shape, tool-calling, errors,
// and usage metadata behind this interface. Adding a provider = one file.
import type { ProviderType } from "../model/types.js";

export type ProviderRole = "user" | "assistant";

export interface TextBlock {
  type: "text";
  text: string;
}

export interface ToolUseBlock {
  type: "tool_use";
  id: string;
  name: string;
  input: unknown;
}

export interface ToolResultBlock {
  type: "tool_result";
  toolUseId: string;
  content: string;
  isError?: boolean;
}

export type AssistantBlock = TextBlock | ToolUseBlock;

export interface NormalizedMessage {
  role: ProviderRole;
  /** Assistant turns: text/tool_use. User turns: text, or tool_result(s). */
  blocks: Array<TextBlock | ToolUseBlock | ToolResultBlock>;
}

export interface NormalizedToolSchema {
  name: string;
  description: string;
  /** JSON Schema object for the tool's input. */
  inputSchema: Record<string, unknown>;
}

export interface NormalizedRequest {
  system: string;
  messages: NormalizedMessage[];
  tools: NormalizedToolSchema[];
  maxTokens: number;
  temperature?: number | undefined;
}

export interface ToolCall {
  id: string;
  name: string;
  input: unknown;
}

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
}

export interface NormalizedResponse {
  text: string;
  toolCalls: ToolCall[];
  usage: TokenUsage | null;
  stopReason: string;
}

export interface ResolvedProviderConfig {
  providerType: ProviderType;
  model: string;
  apiKey: string | null;
  baseUrl: string | null;
  params: Record<string, unknown>;
}

export interface ProviderAdapter {
  readonly providerType: ProviderType;
  /** Checked before agent features run against this provider (docs/15 §5). */
  readonly supportsToolCalling: boolean;
  sendMessage(request: NormalizedRequest): Promise<NormalizedResponse>;
  /** Lightweight test call on save — failures surface in Settings, not mid-task. */
  validateConfig(): Promise<void>;
  /** Null when cost is meaningless (local) or unknown (no hardcoded tables). */
  estimateCost(usage: TokenUsage): number | null;
}
