// Code-enforced risk classification (docs/11-permission-confirmation-model.md §2).
//
// NON-NEGOTIABLES (locked in, tested):
// 1. Tier is computed from RESOLVED scope — concrete ids after filters run —
//    never from the literal request text. "All urgent tasks" resolving to one
//    task is auto-tier; resolving to twelve is confirm-tier.
// 2. Per-tool floors (docs/10 base tiers): scope escalates past a floor, it
//    can never de-escalate below it. deleteEvent is never auto, at any scope.
// 3. The LLM's own risk claim is not an input to this function at all —
//    there is no parameter for it, so it cannot be trusted by accident.
// 4. User overrides (doc-11 §4) raise friction freely, lower only within
//    safe bounds. The floor in this file is the true gate — overrides are
//    advisory to the planner prompt, not to this classifier.
import { getToolDefinition, type ToolName } from "../tools/definitions.js";
import type { RiskTier } from "../model/types.js";

const RANK: Record<RiskTier, number> = { read: 0, auto: 1, confirm: 2, blocked: 3 };

function atLeast(floor: RiskTier, tier: RiskTier): RiskTier {
  return RANK[tier] >= RANK[floor] ? tier : floor;
}

export interface ClassificationInput {
  tool: ToolName;
  /** Concrete target ids AFTER filter resolution. Empty = nothing matched. */
  resolvedIds: string[];
  /** Single-item updateEvent that would create a conflict or cascade. */
  createsConflictOrCascade?: boolean;
  /** User-configured overrides (doc-11 §4). Advisory — floor still enforced. */
  overrides?: Record<string, RiskTier> | undefined;
}

export interface Classification {
  tier: RiskTier;
  /** Why, in one line — surfaced in debug view + confirm cards. */
  reason: string;
}

/**
 * Pure function: tool identity + resolved scope -> tier.
 * Read tools ignore scope. Mutating auto-base tools escalate to confirm
 * past one item. Confirm-base tools never come down.
 * User overrides (doc-11 §4) can raise friction but never below the
 * tool's base tier floor.
 */
export function classifyRisk(input: ClassificationInput): Classification {
  const def = getToolDefinition(input.tool);
  const count = input.resolvedIds.length;

  if (!def.mutating) {
    return { tier: "read", reason: `${def.name} is read-only.` };
  }

  // Determine the effective floor: max of base tier and any user override.
  // Overrides can raise friction (auto → confirm) but never lower below base.
  let effectiveFloor = def.baseTier;
  const override = input.overrides?.[input.tool];
  if (override && RANK[override] > RANK[effectiveFloor]) {
    effectiveFloor = override;
  }

  if (count > 1) {
    return {
      tier: atLeast(effectiveFloor, "confirm"),
      reason: `${def.name} resolves to ${count} items (multi-item is confirm-tier or higher).`,
    };
  }

  if (input.tool === "updateEvent" && input.createsConflictOrCascade === true) {
    return {
      tier: atLeast(effectiveFloor, "confirm"),
      reason: "updateEvent would create a conflict or cascade — confirm-tier.",
    };
  }

  return { tier: effectiveFloor, reason: `${def.name} at minimum scope sits at its ${effectiveFloor} floor.` };
}

/**
 * Plan-level tier (docs/05 Flow C): a plan is only as low-risk as its
 * riskiest member, and MORE than one mutating call is confirm-tier even when
 * every member is auto alone (bulk creation still gets reviewed as one).
 * The runtime gates multi-call plans on this before executing call #1.
 */
export function classifyPlan(tools: ToolName[]): Classification {
  if (tools.length === 0) {
    return { tier: "read", reason: "Empty plan — nothing to do." };
  }
  let worst: RiskTier = "read";
  let mutating = 0;
  for (const tool of tools) {
    const base = getToolDefinition(tool).baseTier;
    if (RANK[base] > RANK[worst]) worst = base;
    if (getToolDefinition(tool).mutating) mutating += 1;
  }
  if (mutating > 1 && RANK[worst] < RANK["confirm"]) {
    worst = "confirm";
  }
  const reason =
    mutating > 1 && worst === "confirm" && tools.every((t) => getToolDefinition(t).baseTier !== "confirm")
      ? `Plan holds ${mutating} mutating calls — bulk action reviews as one confirm-tier plan.`
      : `Plan tier follows its riskiest member (${worst}).`;
  return { tier: worst, reason };
}

/**
 * Filter-based bulk delete ("delete all matching X") is blocked-by-default
 * (docs/10 §2 footnote): there is deliberately no executable path for it.
 * The agent must offer the resolved itemized list as confirm-tier instead —
 * the executor attaches that list to the blocked outcome (verdict #5).
 */
export function classifyFilterBulkDelete(tool: "deleteTask" | "deleteEvent"): Classification {
  return {
    tier: "blocked",
    reason:
      `${tool} with a filter selector is blocked-by-default: bulk destructive actions ` +
      `never execute as one call. Break into the itemized confirm-tier list instead.`,
  };
}
