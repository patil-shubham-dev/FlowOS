// ProviderConfigService — CRUD over provider configs. API keys are encrypted
// at rest and decrypted only at send time; settings changes are logged
// (docs/11 §4). Validation (test call) lives in src/providers/validate.ts
// to keep this service free of network dependencies.
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { MutationContext, PreviewResult, ProviderConfig, ProviderType } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, decryptSecret, encryptSecret } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";
import { readRawOne } from "./rows.js";

type Row = Record<string, unknown>;

export const PROVIDER_TYPES: ProviderType[] = [
  "openai",
  "anthropic",
  "nvidia",
  "google",
  "openrouter",
  "ollama",
  "lm_studio",
  "custom_openai_compatible",
];

export interface UpsertProviderInput {
  providerType: ProviderType;
  model: string;
  /** Plaintext key — encrypted before storage, never logged. */
  apiKey?: string | null;
  baseUrl?: string | null;
  params?: Record<string, unknown>;
  isDefault?: boolean;
}

function toConfig(row: Row): ProviderConfig {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    providerType: row["provider_type"] as ProviderType,
    model: row["model"] as string,
    apiKeyEncrypted: (row["api_key_encrypted"] as string | null) ?? null,
    baseUrl: (row["base_url"] as string | null) ?? null,
    params: JSON.parse((row["params"] as string) || "{}") as Record<string, unknown>,
    isDefault: (row["is_default"] as number) === 1,
  };
}

export class ProviderConfigService {
  private readonly log: ActionLogService;

  constructor(
    private readonly db: FlowDatabase,
    private readonly dataDir: string,
  ) {
    this.log = new ActionLogService(db);
  }

  listConfigs(workspaceId: string = DEFAULT_WORKSPACE_ID): ProviderConfig[] {
    const rows = this.db
      .prepare(`SELECT * FROM provider_configs WHERE workspace_id = ? ORDER BY is_default DESC, provider_type`)
      .all(workspaceId) as Row[];
    return rows.map(toConfig);
  }

  /** List safe for UI display — apiKeyEncrypted is presence, never value. */
  listConfigsForDisplay(workspaceId: string = DEFAULT_WORKSPACE_ID): Array<Omit<ProviderConfig, "apiKeyEncrypted"> & { hasApiKey: boolean }> {
    return this.listConfigs(workspaceId).map((c) => ({
      id: c.id,
      workspaceId: c.workspaceId,
      providerType: c.providerType,
      model: c.model,
      baseUrl: c.baseUrl,
      params: c.params,
      isDefault: c.isDefault,
      hasApiKey: c.apiKeyEncrypted !== null,
    }));
  }

  getDefault(): ProviderConfig | null {
    const row = this.db
      .prepare(`SELECT * FROM provider_configs WHERE workspace_id = ? AND is_default = 1`)
      .get(DEFAULT_WORKSPACE_ID) as Row | undefined;
    return row ? toConfig(row) : null;
  }

  upsertConfig(input: UpsertProviderInput, ctx: MutationContext): ProviderConfig {
    if (!PROVIDER_TYPES.includes(input.providerType)) {
      throw new FlowOSError(
        "VALIDATION",
        `Unknown provider type "${input.providerType}".`,
        `Use one of: ${PROVIDER_TYPES.join(", ")}.`,
      );
    }
    if (!input.model.trim()) {
      throw new FlowOSError("VALIDATION", "Model identifier cannot be empty.", "Enter the model id (e.g. claude-sonnet-4-5) and try again.");
    }
    const localTypes: ProviderType[] = ["ollama", "lm_studio", "custom_openai_compatible"];
    if (localTypes.includes(input.providerType) && !input.baseUrl) {
      throw new FlowOSError(
        "VALIDATION",
        `Local provider "${input.providerType}" needs a base URL.`,
        "Enter the base URL (e.g. http://localhost:11434) and try again.",
      );
    }
    const existing = this.db
      .prepare(`SELECT * FROM provider_configs WHERE workspace_id = ? AND provider_type = ? AND model = ?`)
      .get(DEFAULT_WORKSPACE_ID, input.providerType, input.model.trim()) as Row | undefined;
    const apiKeyEncrypted =
      input.apiKey === undefined
        ? (existing?.["api_key_encrypted"] as string | null | undefined) ?? null
        : input.apiKey === null || input.apiKey === ""
          ? null
          : encryptSecret(input.apiKey, this.dataDir);
    const params = input.params ?? (existing ? (JSON.parse((existing["params"] as string) || "{}") as Record<string, unknown>) : {});
    const mergedBaseUrl =
      input.baseUrl === undefined ? ((existing?.["base_url"] as string | null | undefined) ?? null) : input.baseUrl;
    const mergedIsDefault = input.isDefault ?? (existing ? (existing["is_default"] as number) === 1 : false);
    if (ctx.dryRun) {
      // No commit, no log — would-be config only (confirm-gate previews).
      if (existing) {
        return toConfig({
          ...existing,
          api_key_encrypted: apiKeyEncrypted,
          base_url: mergedBaseUrl,
          params: JSON.stringify(params),
          is_default: mergedIsDefault ? 1 : 0,
        });
      }
      return {
        id: randomUUID(),
        workspaceId: DEFAULT_WORKSPACE_ID,
        providerType: input.providerType,
        model: input.model.trim(),
        apiKeyEncrypted,
        baseUrl: mergedBaseUrl,
        params,
        isDefault: mergedIsDefault,
      };
    }
    if (input.isDefault) {
      this.db.prepare(`UPDATE provider_configs SET is_default = 0 WHERE workspace_id = ?`).run(DEFAULT_WORKSPACE_ID);
    }
    if (existing) {
      this.db
        .prepare(
          `UPDATE provider_configs SET api_key_encrypted = ?, base_url = ?, params = ?, is_default = ?
           WHERE id = ?`,
        )
        .run(
          apiKeyEncrypted,
          input.baseUrl === undefined ? (existing["base_url"] as string | null) : input.baseUrl,
          JSON.stringify(params),
          (input.isDefault ?? ((existing["is_default"] as number) === 1)) ? 1 : 0,
          existing["id"] as string,
        );
      const updated = readRawOne(this.db, "provider_configs", existing["id"] as string) as Row;
      this.log.record({
        actor: ctx.actor,
        sessionId: ctx.sessionId,
        toolOrOperation: "updateProviderConfig",
        targetType: "provider",
        targetIds: [existing["id"] as string],
        beforeState: [existing],
        afterState: [updated],
        groupId: ctx.groupId,
      });
      return toConfig(updated);
    }
    const id = randomUUID();
    this.db
      .prepare(
        `INSERT INTO provider_configs
         (id, workspace_id, provider_type, model, api_key_encrypted, base_url, params, is_default)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        id, DEFAULT_WORKSPACE_ID, input.providerType, input.model.trim(),
        apiKeyEncrypted, input.baseUrl ?? null, JSON.stringify(params),
        input.isDefault ? 1 : 0,
      );
    const after = readRawOne(this.db, "provider_configs", id) as Row;
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "createProviderConfig",
      targetType: "provider",
      targetIds: [id],
      beforeState: [null],
      afterState: [after],
      groupId: ctx.groupId,
    });
    return toConfig(after);
  }

  previewUpsert(input: UpsertProviderInput, ctx: MutationContext): PreviewResult<ProviderConfig> {
    const beforeRow = this.db
      .prepare(`SELECT * FROM provider_configs WHERE workspace_id = ? AND provider_type = ? AND model = ?`)
      .get(DEFAULT_WORKSPACE_ID, input.providerType, input.model.trim()) as Row | undefined;
    const result = this.upsertConfig(input, { ...ctx, dryRun: true });
    return { committed: false, before: [beforeRow ?? null], after: [result], result };
  }

  deleteConfig(id: string, ctx: MutationContext): { id: string } {
    const existing = readRawOne(this.db, "provider_configs", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Provider config ${id} not found.`, "Refresh Settings and try again.");
    }
    if (ctx.dryRun) {
      return { id };
    }
    this.db.prepare(`DELETE FROM provider_configs WHERE id = ?`).run(id);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "deleteProviderConfig",
      targetType: "provider",
      targetIds: [id],
      beforeState: [existing],
      afterState: [null],
      groupId: ctx.groupId,
    });
    return { id };
  }

  previewDeleteConfig(id: string, ctx: MutationContext): PreviewResult<{ id: string }> {
    const beforeRow = readRawOne(this.db, "provider_configs", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Provider config ${id} not found.`, "Refresh Settings and try again.");
    }
    return { committed: false, before: [beforeRow], after: [null], result: { id } };
  }

  /** Decrypt the key for send-time use only — caller must never log it. */
  revealApiKey(config: ProviderConfig): string | null {
    if (!config.apiKeyEncrypted) return null;
    return decryptSecret(config.apiKeyEncrypted, this.dataDir);
  }
}
