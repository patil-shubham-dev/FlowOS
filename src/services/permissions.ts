// Permission override service (doc-11 §4).
// Users may raise friction freely (auto → confirm), lower only within
// safe bounds (blocked tools can never go below confirm in MVP).
// The code-enforced floor in risk.ts is the true gate — overrides
// are advisory to the planner prompt, not to the risk classifier.
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { RiskTier } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError } from "../security/secrets.js";
import { getToolDefinition, type ToolName, ALL_TOOL_NAMES } from "../tools/definitions.js";

const RANK: Record<RiskTier, number> = { read: 0, auto: 1, confirm: 2, blocked: 3 };

/** Tools whose floor is "confirm" or "blocked" — cannot be lowered below confirm. */
const LOWERABLE_FLOORS: Set<ToolName> = new Set(
  ALL_TOOL_NAMES.filter((name) => RANK[getToolDefinition(name).baseTier] >= RANK["confirm"]),
);

export interface PermissionOverride {
  id: string;
  toolName: ToolName;
  overrideTier: RiskTier;
  createdAt: string;
  updatedAt: string;
}

interface Row {
  id: string;
  tool_name: string;
  override_tier: string;
  created_at: string;
  updated_at: string;
}

function toOverride(row: Row): PermissionOverride {
  return {
    id: row.id,
    toolName: row.tool_name as ToolName,
    overrideTier: row.override_tier as RiskTier,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export class PermissionOverrideService {
  constructor(private readonly db: FlowDatabase) {}

  listOverrides(workspaceId: string = DEFAULT_WORKSPACE_ID): PermissionOverride[] {
    const rows = this.db
      .prepare(`SELECT * FROM permission_overrides WHERE workspace_id = ? ORDER BY tool_name`)
      .all(workspaceId) as Row[];
    return rows.map(toOverride);
  }

  /**
   * Get override for a specific tool. Returns the override tier if set,
   * otherwise null (falls back to the tool's base tier in risk.ts).
   */
  getOverride(toolName: string, workspaceId: string = DEFAULT_WORKSPACE_ID): RiskTier | null {
    const row = this.db
      .prepare(`SELECT override_tier FROM permission_overrides WHERE workspace_id = ? AND tool_name = ?`)
      .get(workspaceId, toolName) as { override_tier: string } | undefined;
    return row ? (row.override_tier as RiskTier) : null;
  }

  /**
   * Set an override for a tool. Enforces doc-11 §4 bounds:
   * - Can raise friction freely (auto → confirm)
   * - Can lower friction only within safe bounds (blocked tools stay ≥ confirm)
   * - read-tier tools cannot be overridden (they're not mutations)
   */
  setOverride(toolName: string, overrideTier: RiskTier, workspaceId: string = DEFAULT_WORKSPACE_ID): PermissionOverride {
    if (!ALL_TOOL_NAMES.includes(toolName as ToolName)) {
      throw new FlowOSError("VALIDATION", `Unknown tool "${toolName}".`, "Use one of the registered tool names.");
    }
    const def = getToolDefinition(toolName as ToolName);
    if (!def.mutating) {
      throw new FlowOSError("VALIDATION", `Tool "${toolName}" is read-only — overrides only apply to mutating tools.`, "Read tools are always automatic.");
    }
    if (overrideTier === "read") {
      throw new FlowOSError("VALIDATION", `Cannot override "${toolName}" to read tier — read tier is for non-mutating tools only.`, "Use auto, confirm, or blocked.");
    }
    // doc-11 §4: blocked tools cannot be lowered below confirm in MVP.
    if (def.baseTier === "blocked" && RANK[overrideTier] < RANK["confirm"]) {
      throw new FlowOSError(
        "VALIDATION",
        `Tool "${toolName}" is blocked-by-default and cannot be lowered below confirm-tier in MVP.`,
        "This tool's safety floor is enforced by code. You can raise its friction, but not lower it.",
      );
    }
    const now = new Date().toISOString();
    const id = randomUUID();
    this.db
      .prepare(
        `INSERT INTO permission_overrides (id, workspace_id, tool_name, override_tier, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT(workspace_id, tool_name) DO UPDATE SET override_tier = excluded.override_tier, updated_at = excluded.updated_at`,
      )
      .run(id, workspaceId, toolName, overrideTier, now, now);
    const row = this.db
      .prepare(`SELECT * FROM permission_overrides WHERE workspace_id = ? AND tool_name = ?`)
      .get(workspaceId, toolName) as Row;
    return toOverride(row);
  }

  /** Remove an override — tool reverts to its code-enforced base tier. */
  clearOverride(toolName: string, workspaceId: string = DEFAULT_WORKSPACE_ID): void {
    this.db
      .prepare(`DELETE FROM permission_overrides WHERE workspace_id = ? AND tool_name = ?`)
      .run(workspaceId, toolName);
  }

  /** Get all overrides as a flat map for the risk classifier. */
  getOverrideMap(workspaceId: string = DEFAULT_WORKSPACE_ID): Record<string, RiskTier> {
    const overrides = this.listOverrides(workspaceId);
    const map: Record<string, RiskTier> = {};
    for (const o of overrides) {
      map[o.toolName] = o.overrideTier;
    }
    return map;
  }
}
