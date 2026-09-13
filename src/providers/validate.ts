// Provider config validation wiring (docs/07 FR-7.2, docs/15 §2).
// Keeps network code out of ProviderConfigService: resolve the stored
// config (decrypting the key for send-time use only), build the adapter,
// run its test call. Keys are never logged (redactSecrets at call sites).
import { FlowOSError } from "../security/secrets.js";
import { ProviderConfigService } from "../services/providers.js";
import { AnthropicAdapter } from "./anthropic.js";
import { OpenAICompatibleAdapter } from "./openai-compatible.js";
import type { ProviderAdapter, ResolvedProviderConfig } from "./types.js";

export function buildAdapter(resolved: ResolvedProviderConfig): ProviderAdapter {
  switch (resolved.providerType) {
    case "anthropic":
      return new AnthropicAdapter(resolved);
    case "ollama":
    case "lm_studio":
    case "custom_openai_compatible":
    case "openai":
    case "google":
    case "openrouter":
    case "nvidia":
      return new OpenAICompatibleAdapter(resolved);
    default:
      throw new FlowOSError(
        "NOT_IMPLEMENTED",
        `No adapter for provider type "${resolved.providerType}" yet.`,
        "Use anthropic for now; more adapters land behind this same interface.",
      );
  }
}

/** Test-call a stored config by id. Throws a specific FlowOSError on failure. */
export async function validateStoredConfig(svc: ProviderConfigService, id: string): Promise<void> {
  const all = svc.listConfigs();
  const found = all.find((c) => c.id === id);
  if (!found) {
    throw new FlowOSError("NOT_FOUND", `Provider config ${id} not found.`, "Refresh Settings and try again.");
  }
  const adapter = buildAdapter({
    providerType: found.providerType,
    model: found.model,
    apiKey: svc.revealApiKey(found),
    baseUrl: found.baseUrl,
    params: found.params,
  });
  await adapter.validateConfig();
}
