// Orchestration loop (docs/09 §1) as a backend entry point — callable
// independent of any frontend.
//
// PLAN SEAM (locked): LoopInput.plan = { steps: [{ tool, input }],
// preferencesUsed? }. A future LLM planner populates steps with a sequence
// of intended tool calls plus the preference ids it consulted — no shape
// change needed. Single-step plans are one-element arrays.
//
// Per-turn flow: context assembly -> propose every step -> plan-level tier
// (worst resolved member; >1 mutating call is confirm even if all auto) ->
// confirm gate (whole plan held) -> execute sequentially under ONE plan
// groupId (single undo unit) with per-step verify -> audit (services write
// during execute) -> per-step summaries joined -> session append -> response.
//
// Confirm-gate flow across the wire: a confirm-tier plan WITHOUT approval
// returns { status: "needs-approval", proposals } — the UI renders diff
// cards and calls back with `approval` (the echoed approvalTemplate).
// Nothing executes and zero rows are written until then. A plan with an
// empty step or a blocked member halts before ANY execution (no partial
// plans from unmet premises); mid-plan failures halt with PARTIAL_EXECUTION
// accounting (completed vs pending named precisely, doc-20).
import { randomUUID } from "node:crypto";
import { classifyPlan } from "./risk.js";
import type { Preference, RiskTier, Surface } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";
import type { FlowDatabase } from "../db/database.js";
import { ActionLogService } from "../services/actionLog.js";
import type { CalendarService } from "../services/calendar.js";
import type { NoteService } from "../services/notes.js";
import type { PreferenceService } from "../services/preferences.js";
import { SessionService, type TurnRecord, type TurnStepRecord } from "../services/sessions.js";
import type { TaskService } from "../services/tasks.js";
import { ToolExecutor, fingerprint, type Proposal } from "../tools/executor.js";
import type { ToolName } from "../tools/definitions.js";
import { assembleContext, type ContextInput } from "./context.js";
import { composeSummary } from "./summary.js";
import { Verifier, type VerifyResult } from "./verify.js";

export interface LoopServices {
  db: FlowDatabase;
  tasks: TaskService;
  calendar: CalendarService;
  notes: NoteService;
  prefs: PreferenceService;
}

export interface PlanStep {
  tool: ToolName;
  input: unknown;
}

export interface ExplicitPlan {
  /** Ordered tool calls. One step = today's single-call plans, unchanged. */
  steps: PlanStep[];
  /** Preference ids the plan consulted (planner-declared; cited in summary). */
  preferencesUsed?: string[] | undefined;
}

export interface LoopInput {
  /** Scoped-push shape (page) or pull-based shape (Home). */
  context: ContextInput;
  /** The user's words (recorded for planner/debug; not parsed today). */
  instruction: string;
  /** Explicit plan — the current stand-in for the LLM planner. */
  plan: ExplicitPlan;
  /** Present when the user approved a confirm-tier plan (echo approvalTemplate). */
  approval?: PlanApproval | undefined;
  sessionId?: string | undefined;
}

/** Approval covers the whole plan; per-step entries bind reviewed id sets. */
export interface PlanApproval {
  tier: RiskTier;
  planFingerprint: string;
  groupId: string;
  steps: Array<{ inputFingerprint: string; affectedIds: string[] }>;
}

export type LoopStatus = "executed" | "needs-approval" | "blocked" | "empty" | "failed";

/** Top-level UI discriminant (design.md §5.5/§5.6): the frontend switches on
 *  `ui` (+ `status`) alone — never on tier/tool internals.
 *  diff-card = confirm card w/ Approve/Edit/Cancel; blocked-card = Blocked
 *  variant w/ itemized plan; toast = auto-executed notice w/ Undo;
 *  inline = read results / applied summaries in-thread; message = empty/failed. */
export type UiHint = "diff-card" | "blocked-card" | "toast" | "inline" | "message";

export interface LoopOutput {
  status: LoopStatus;
  sessionId: string;
  /** Worst resolved tier across the plan (null when no plan ran). */
  tier: RiskTier | null;
  ui: UiHint;
  /** One proposal per step, in plan order (diff-card source). */
  proposals: Proposal[];
  planFingerprint: string | null;
  /** Echo back as `approval` to execute an approved plan. */
  approvalTemplate: PlanApproval | null;
  verifications: VerifyResult[];
  summary: string;
  preferencesUsed: Preference[];
  groupId: string | null;
  error: { code: string; message: string; next: string } | null;
  /** Per-step compact records (tool, tier, ids, result ids/titles). */
  stepRecords: TurnStepRecord[];
}

const RANK: Record<RiskTier, number> = { read: 0, auto: 1, confirm: 2, blocked: 3 };

export function planFingerprint(plan: ExplicitPlan): string {
  return `plan:${stablePlan(plan)}`;
}

function stablePlan(plan: ExplicitPlan): string {
  return JSON.stringify(plan.steps.map((s) => fingerprint(s.tool, s.input)));
}

function toErrorShape(err: unknown): { code: string; message: string; next: string } {
  if (err instanceof FlowOSError) {
    return { code: err.code, message: err.message, next: err.next };
  }
  const message = err instanceof Error ? err.message : String(err);
  return { code: "STORE", message: `Unexpected failure: ${message}`, next: "Retry the request; if it persists, check the debug view." };
}

function isValidSurface(surface: unknown): surface is Surface {
  return surface === "home" || surface === "scoped:tasks" || surface === "scoped:calendar" || surface === "scoped:notes";
}

export class AgentLoop {
  private readonly exec: ToolExecutor;
  private readonly verifier: Verifier;
  private readonly sessions: SessionService;

  constructor(private readonly svc: LoopServices) {
    const log = new ActionLogService(svc.db);
    this.exec = new ToolExecutor({ tasks: svc.tasks, calendar: svc.calendar, notes: svc.notes, prefs: svc.prefs, log });
    this.verifier = new Verifier({ tasks: svc.tasks, calendar: svc.calendar, notes: svc.notes, prefs: svc.prefs });
    this.sessions = new SessionService(svc.db);
  }

  /** Backend-owned thread history (docs/09 §8). The UI renders it; never owns it. */
  getHistory(sessionId: string): TurnRecord[] | null {
    return this.sessions.getHistory(sessionId);
  }

  runTurn(input: LoopInput): LoopOutput {
    if (!input || !isValidSurface((input.context as { surface?: unknown } | null)?.surface)) {
      return {
        status: "failed", sessionId: input?.sessionId ?? "unknown", tier: null, ui: "message",
        proposals: [], planFingerprint: null, approvalTemplate: null, verifications: [],
        summary: "That request arrived without a valid surface. Attach { surface, visibleIds?, selectedId? } and retry.",
        preferencesUsed: [], groupId: null,
        stepRecords: [],
        error: { code: "MALFORMED_TOOL_CALL", message: "Missing or unknown surface.", next: "Use home, scoped:tasks, scoped:calendar, or scoped:notes." },
      };
    }
    const ensured = this.sessions.ensureSession(input.sessionId, input.context.surface);
    const sessionId = ensured.id;
    const agentCtx = { sessionId };
    let output: LoopOutput;
    try {
      output = this.executeTurn(input, sessionId, agentCtx);
    } catch (err) {
      const shape = toErrorShape(err);
      output = {
        status: "failed", sessionId, tier: null, ui: "message",
        proposals: [], planFingerprint: planFingerprintSafe(input.plan), approvalTemplate: null, verifications: [],
        summary: `${shape.message} ${shape.next}`,
        preferencesUsed: [], groupId: null, stepRecords: [], error: shape,
      };
    }
    this.sessions.appendTurn(sessionId, toTurnRecord(input, output));
    return { ...output, sessionId };
  }

  private executeTurn(input: LoopInput, sessionId: string, agentCtx: { sessionId: string }): LoopOutput {
    // 1. Context assembly (validates scope shape; loads prefs + ambient).
    const assembled = assembleContext(
      {
        prefs: this.svc.prefs,
        counts: () => ({
          tasksOpen: this.svc.tasks.listTasks().filter((t) => t.status !== "done").length,
          eventsToday: this.svc.calendar
            .listEvents({ from: new Date().toISOString().slice(0, 10) + "T00:00:00", to: new Date().toISOString().slice(0, 10) + "T23:59:59" })
            .length,
          notes: this.svc.notes.listNotes().length,
        }),
      },
      input.context,
    );

    // 2. Preferences resolved from the assembled context (usage recorded).
    const preferencesUsed: Preference[] = [];
    for (const id of input.plan.preferencesUsed ?? []) {
      const found = assembled.preferences.find((p) => p.id === id);
      if (found) {
        this.svc.prefs.touchPreference(id);
        preferencesUsed.push(found);
      }
    }

    // 3. Propose every step (validate -> resolve -> classify -> dry-run).
    const steps = input.plan.steps ?? [];
    if (steps.length === 0) {
      return {
        status: "empty", sessionId, tier: "read", ui: "message",
        proposals: [], planFingerprint: planFingerprint(input.plan), approvalTemplate: null, verifications: [],
        summary: "The plan holds no steps — nothing to do.",
        preferencesUsed, groupId: null, stepRecords: [], error: null,
      };
    }
    const proposals = steps.map((s) => this.exec.propose(s.tool, s.input, agentCtx));
    const fp = planFingerprint(input.plan);

    // 4. Plan-level tier: worst resolved member; >1 mutating call floors at confirm.
    let tier: RiskTier = classifyPlan(steps.map((s) => s.tool)).tier;
    for (const p of proposals) {
      if (RANK[p.tier] > RANK[tier]) tier = p.tier;
    }

    // 5a. Blocked member halts the plan before ANY execution.
    const blockedIdx = proposals.findIndex((p) => p.kind === "blocked");
    if (blockedIdx !== -1) {
      const blocked = proposals[blockedIdx] as Proposal;
      return {
        status: "blocked", sessionId, tier: "blocked", ui: "blocked-card",
        proposals, planFingerprint: fp, approvalTemplate: null, verifications: [],
        summary: composeSummary({ proposal: blocked, committed: false, groupId: null, verification: null, preferencesUsed }),
        preferencesUsed, groupId: null, stepRecords: [], error: null,
      };
    }

    // 5b. Empty step halts the plan (unmet premise; zero executions).
    const emptyMember = proposals.find((p) => p.kind === "empty");
    if (emptyMember) {
      return {
        status: "empty", sessionId, tier: "read", ui: "message",
        proposals, planFingerprint: fp, approvalTemplate: null, verifications: [],
        summary: composeSummary({ proposal: emptyMember, committed: false, groupId: null, verification: null, preferencesUsed }),
        preferencesUsed, groupId: null, stepRecords: [], error: null,
      };
    }

    // 5c. Confirm gate: the whole plan is held until approval echoes back.
    // The plan group id is minted here so approval and execution share one
    // undo unit; per-proposal group ids from dry-run previews are superseded.
    const planGroupId = randomUUID();
    if (tier === "confirm" && !input.approval) {
      const template: PlanApproval = {
        tier, planFingerprint: fp, groupId: planGroupId,
        steps: proposals.map((p) => ({ inputFingerprint: p.inputFingerprint, affectedIds: p.affectedIds })),
      };
      return {
        status: "needs-approval", sessionId, tier, ui: "diff-card",
        proposals, planFingerprint: fp, approvalTemplate: template, verifications: [],
        summary: `This plan needs your approval before anything changes (${proposals.reduce((n, p) => n + p.affectedIds.length, 0)} item(s) across ${proposals.length} step(s)).`,
        preferencesUsed, groupId: null, stepRecords: [], error: null,
      };
    }

    return this.executePlan(input, sessionId, agentCtx, proposals, fp, tier, preferencesUsed, input.approval);
  }

  /** Shared by fresh auto/read plans and approved confirm plans. */
  private executePlan(
    input: LoopInput,
    sessionId: string,
    agentCtx: { sessionId: string },
    proposals: Proposal[],
    fp: string,
    tier: RiskTier,
    preferencesUsed: Preference[],
    approval: PlanApproval | undefined,
  ): LoopOutput {
    // Plan-level seal: the echoed template must describe this exact plan.
    // Per-step binding inside the loop additionally pins each step's id set.
    if (approval && approval.planFingerprint !== fp) {
      throw new FlowOSError(
        "AMBIGUOUS_REQUEST",
        "This plan needs explicit approval (or it was edited after approval — re-propose first).",
        "Approve the exact proposed plan, or edit it and review the new diff before approving.",
      );
    }
    if (approval && approval.steps.length !== proposals.length) {
      throw new FlowOSError(
        "VERIFICATION_MISMATCH",
        "The workspace changed between proposal and approval — the plan shape no longer matches.",
        "Re-propose the plan and approve the fresh diff.",
      );
    }
    const executedAt = nowIso();
    const groupId = approval?.groupId ?? null;
    const verifications: VerifyResult[] = [];
    const stepSummaries: string[] = [];
    const stepRecords: TurnStepRecord[] = [];
    let cited = false;

    for (let i = 0; i < proposals.length; i++) {
      const proposal = proposals[i] as Proposal;
      const step = (input.plan.steps as { tool: ToolName; input: unknown }[])[i] as { tool: ToolName; input: unknown };
      try {
        // Plan-level binding: each step's current fingerprint must match the
        // approved template (catches edits smuggled into auto steps, which
        // carry no per-step gate of their own).
        if (approval && approval.steps[i]?.inputFingerprint !== fingerprint(step.tool, step.input)) {
          throw new FlowOSError(
            "AMBIGUOUS_REQUEST",
            `Plan step ${i + 1} changed after approval — re-propose first.`,
            "Approve the exact proposed plan, or edit it and review the new diff before approving.",
          );
        }
        const stepApproval = approval
          ? { tier: proposal.tier, inputFingerprint: approval.steps[i]?.inputFingerprint ?? "", groupId: groupId as string, affectedIds: approval.steps[i]?.affectedIds ?? [] }
          : undefined;
        const executed = this.exec.executeApproved(step.tool, step.input, agentCtx, stepApproval);
        if (!executed.executed) {
          throw new FlowOSError("STORE", `Step ${i + 1} (${step.tool}) unexpectedly did not execute.`, "Re-propose the plan and try again.");
        }
        const verification = this.verifier.verify(proposal, executedAt, collectCreatedIds(executed.result));
        verifications.push(verification);
        stepSummaries.push(
          composeSummary({
            proposal, committed: true, groupId, verification,
            preferencesUsed: !cited ? preferencesUsed : [],
          }),
        );
        cited = cited || preferencesUsed.length > 0;
        stepRecords.push({
          tool: step.tool, kind: proposal.kind, tier: proposal.tier,
          affectedIds: proposal.affectedIds, resultSummary: summarizeResult(executed.result),
          logEntryIds: executed.logEntryIds,
        });
      } catch (err) {
        const shape = toErrorShape(err);
        const doneNote =
          stepSummaries.length > 0
            ? `Completed before the failure: ${stepSummaries.filter((s) => s.length > 0).join(" ")} `
            : "Nothing completed before the failure. ";
        const rollbackNote =
          groupId && stepSummaries.length > 0
            ? "You can undo the completed steps as one grouped action from Action History."
            : "No changes need rolling back.";
        // Return (don't throw): the partial outcome carries its group id so
        // the UI can offer one-click grouped rollback of what did complete.
        const code = (stepSummaries.length > 0 ? "PARTIAL_EXECUTION" : shape.code) as FlowOSError["code"];
        return {
          status: "failed", sessionId, tier, ui: "message" as const,
          proposals, planFingerprint: fp, approvalTemplate: null, verifications,
          summary: `${doneNote}Step ${i + 1} (${step.tool}) failed: ${shape.message} ${shape.next} ${rollbackNote}`,
          preferencesUsed, groupId, stepRecords,
          error: { code, message: `${doneNote}Step ${i + 1} (${step.tool}) failed: ${shape.message}`, next: `${shape.next} ${rollbackNote}` },
        };
      }
    }

    return {
      status: "executed", sessionId, tier,
      ui: tier === "confirm" ? "inline" : proposals.some((p) => p.tier === "auto") ? "toast" : "inline",
      proposals, planFingerprint: fp, approvalTemplate: null, verifications,
      summary: stepSummaries.filter((s) => s.length > 0).join(" "),
      preferencesUsed, groupId, error: null,
      stepRecords,
    };
  }
}

function planFingerprintSafe(plan: ExplicitPlan | undefined): string | null {
  try {
    return plan ? planFingerprint(plan) : null;
  } catch {
    return null;
  }
}

function toTurnRecord(input: LoopInput, output: LoopOutput): TurnRecord {
  const steps: TurnStepRecord[] =
    output.stepRecords.length > 0
      ? output.stepRecords
      : output.proposals.map((p) => ({
          tool: p.tool, kind: p.kind, tier: p.tier,
          affectedIds: p.affectedIds, resultSummary: null, logEntryIds: [],
        }));
  return {
    timestamp: nowIso(),
    instruction: input.instruction,
    surface: input.context.surface,
    steps,
    status: output.status,
    summary: output.summary,
    groupId: output.groupId,
    errorCode: output.error?.code ?? null,
    preferencesUsedIds: output.preferencesUsed.map((p) => p.id),
    proposals: output.proposals.length > 0 ? output.proposals : null,
  };
}

/** Real ids for creation pairs (propose-time dry-run ids are placeholders). */
function collectCreatedIds(result: unknown): string[] {
  if (Array.isArray(result)) {
    return result.flatMap((r) => collectCreatedIds(r));
  }
  if (typeof result === "object" && result !== null && "id" in result && typeof (result as { id: unknown }).id === "string") {
    return [(result as { id: string }).id];
  }
  return [];
}

function summarizeResult(result: unknown): unknown {
  if (Array.isArray(result)) {
    return result.map((r) => summarizeResult(r));
  }
  if (typeof result === "object" && result !== null) {
    const r = result as Record<string, unknown>;
    if (typeof r["id"] === "string" && typeof r["title"] === "string") {
      return { id: r["id"], title: r["title"] };
    }
    if (typeof r["id"] === "string") return { id: r["id"] };
  }
  return null;
}
