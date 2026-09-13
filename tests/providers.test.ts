// Provider tests: normalization, error mapping, adapter routing.
// No network: the Anthropic client is injected as a fake; error mapping is
// verified through both instanceof SDK shapes and duck-typed { status }.
import { describe, expect, it } from "vitest";
import { FlowOSError } from "../src/security/secrets.js";
import {
  AnthropicAdapter,
  fromAnthropicResponse,
  mapAnthropicError,
  toAnthropicMessages,
  toAnthropicTools,
} from "../src/providers/anthropic.js";
import { OllamaAdapter, resolveOllamaBaseUrl, toOllamaMessages } from "../src/providers/ollama.js";
import { TOOL_INPUT_SCHEMAS } from "../src/tools/toolInputSchemas.js";
import { ALL_TOOL_NAMES } from "../src/tools/definitions.js";

const RESOLVED = { providerType: "anthropic" as const, model: "claude-sonnet-4-5", apiKey: "k", baseUrl: null, params: {} };

describe("anthropic normalization", () => {
  it("maps tools, messages, and responses across the interface", async () => {
    const tools = toAnthropicTools([{ name: "searchTasks", description: "q", inputSchema: TOOL_INPUT_SCHEMAS["searchTasks"] }]);
    expect(tools[0]).toMatchObject({ name: "searchTasks", description: "q" });
    expect(tools[0]).toHaveProperty("input_schema");

    const messages = toAnthropicMessages([
      { role: "user", blocks: [{ type: "text", text: "hi" }] },
      { role: "assistant", blocks: [{ type: "tool_use", id: "tu-1", name: "searchTasks", input: { status: "todo" } }] },
      { role: "user", blocks: [{ type: "tool_result", toolUseId: "tu-1", content: "[]" }] },
    ]);
    expect(messages).toHaveLength(3);
    expect(messages[2]).toMatchObject({ role: "user" });

    const seen: Array<Record<string, unknown>> = [];
    const adapter = new AnthropicAdapter(RESOLVED, () => ({
      messages: {
        create: async (params: Record<string, unknown>) => {
          seen.push(params);
          return { content: [{ type: "text", text: "done" }], usage: { input_tokens: 12, output_tokens: 5 }, stop_reason: "end_turn" };
        },
      },
    }));
    const res = await adapter.sendMessage({
      system: "sys",
      messages: [{ role: "user", blocks: [{ type: "text", text: "hi" }] }],
      tools: [{ name: "searchTasks", description: "q", inputSchema: {} }],
      maxTokens: 64,
    });
    expect(res.text).toBe("done");
    expect(res.usage).toEqual({ inputTokens: 12, outputTokens: 5 });
    expect(seen[0]).toMatchObject({ model: "claude-sonnet-4-5", max_tokens: 64 });
    expect(adapter.estimateCost({ inputTokens: 1, outputTokens: 1 })).toBeNull();
  });

  it("parses tool_use blocks out of a mixed response", () => {
    const res = fromAnthropicResponse({
      content: [
        { type: "text", text: "I'll check. " },
        { type: "tool_use", id: "tu-2", name: "deleteTask", input: { ids: ["t-1"] } },
      ],
      usage: { input_tokens: 3, output_tokens: 7 },
      stop_reason: "tool_use",
    });
    expect(res.text).toBe("I'll check. ");
    expect(res.toolCalls).toEqual([{ id: "tu-2", name: "deleteTask", input: { ids: ["t-1"] } }]);
  });

  it("requires an API key at construction", () => {
    expect(() => new AnthropicAdapter({ ...RESOLVED, apiKey: null })).toThrowError(/API key/);
  });
});

describe("anthropic error mapping (never generic)", () => {
  it("maps auth, rate-limit, connection, and bad-request shapes", () => {
    expect(mapAnthropicError({ status: 401 }, "x").code).toBe("PROVIDER_AUTH");
    expect(mapAnthropicError({ status: 429 }, "x").code).toBe("PROVIDER_RATE_LIMIT");
    expect(mapAnthropicError({ status: 400 }, "x").code).toBe("VALIDATION");
    const conn = mapAnthropicError(Object.assign(new Error("boom"), { code: "ECONNREFUSED" }), "x");
    expect(conn.code).toBe("PROVIDER_UNREACHABLE");
    expect(conn.toUserString()).toMatch(/Next step/);
  });

  it("validateConfig surfaces auth failures instead of succeeding silently", async () => {
    const adapter = new AnthropicAdapter(RESOLVED, () => ({
      messages: {
        create: async () => {
          throw { status: 401 };
        },
      },
    }));
    await expect(adapter.validateConfig()).rejects.toMatchObject({ code: "PROVIDER_AUTH" });
  });
});

describe("ollama shape (scaffolded, honest)", () => {
  it("normalizes messages and resolves the base URL default", () => {
    const out = toOllamaMessages([
      { role: "user", blocks: [{ type: "text", text: "plan my day" }] },
      { role: "assistant", blocks: [{ type: "tool_use", id: "t1", name: "searchTasks", input: {} }] },
      { role: "user", blocks: [{ type: "tool_result", toolUseId: "t1", content: "[]" }] },
    ]);
    expect(out).toHaveLength(3);
    expect(out[1]).toHaveProperty("tool_calls");
    expect(resolveOllamaBaseUrl({ providerType: "ollama", model: "m", apiKey: null, baseUrl: null, params: {} })).toBe(
      "http://localhost:11434",
    );
  });

  it("throws NOT_IMPLEMENTED (never a fake success)", async () => {
    const adapter = new OllamaAdapter({ providerType: "ollama", model: "m", apiKey: null, baseUrl: null, params: {} });
    expect(adapter.supportsToolCalling).toBe(true);
    await expect(
      adapter.sendMessage({ system: "", messages: [], tools: [], maxTokens: 1 }),
    ).rejects.toMatchObject({ code: "NOT_IMPLEMENTED" } satisfies Partial<FlowOSError> as object);
  });
});

describe("tool schemas cover every registered tool", () => {
  it("all 20 tools have a JSON schema with type: object", () => {
    for (const name of ALL_TOOL_NAMES) {
      expect(TOOL_INPUT_SCHEMAS[name], name).toMatchObject({ type: "object" });
    }
  });
});
