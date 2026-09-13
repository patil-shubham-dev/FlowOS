// Tool executor (docs/09 §§4–5, docs/11 §3).
//
// Two-phase flow; the audit contract "this actually happened" holds because:
// - propose() NEVER mutates and NEVER logs. It validates, resolves filters
//   to concrete ids, classifies from the resolved scope, and builds previews
//   via dryRun. Cancelled or blocked plans therefore leave zero mutation rows.
// - executeApproved() re-validates, re-resolves, and re-classifies. Confirm
//   plans need an approval token whose fingerprint matches the exact input
//   (an Edit is a new propose, which re-classifies). Blocked plans have no
//   execution path at all — attempting one throws BLOCKED_ACTION.
// - Auto/read plans execute immediately; services write the log rows.
import { randomUUID } from "node:crypto";
import { classifyFilterBulkDelete, classifyRisk } from "../agent/risk.js";
import type { MutationContext, RiskTier } from "../model/types.js";
import { FlowOSError } from "../security/secrets.js";
import { ActionLogService } from "../services/actionLog.js";
import { CalendarService } from "../services/calendar.js";
import { NoteService } from "../services/notes.js";
import { PreferenceService } from "../services/preferences.js";
import type { TaskFilter } from "../services/tasks.js";
import { TaskService } from "../services/tasks.js";
import { getToolDefinition, type ToolName } from "./definitions.js";
import {
  isEventRangeShape,
  isTaskFilterShape,
  validateDeleteEvents,
  validateDeleteTasks,
  validateRecord,
  validateUpdateEvents,
  validateUpdateTasks,
  type Validation,
} from "./inputs.js";

export interface PreviewPair {
  before: unknown;
  after: unknown;
}

export type ProposalKind = "ready" | "confirm" | "blocked" | "empty";

export interface Proposal {
  kind: ProposalKind;
  tier: RiskTier;
  tool: ToolName;
  inputFingerprint: string;
  /** Assigned at propose time; executeApproved reuses it so grouped cascades undo as one unit. */
  groupId: string | null;
  preview: PreviewPair[];
  affectedIds: string[];
  /** Human explanation for empty/blocked outcomes (what/why/next). */
  message: string | null;
  /** Blocked only: the auto-proposed itemized confirm-tier plan (verdict #5). */
  itemizedAlternative: Proposal | null;
  /**
   * The exact input that reproduces the itemized alternative through the
   * normal propose gate (present only on itemizedAlternative proposals).
   * The UI submits it verbatim — never reconstructed from affected ids —
   * so no tool-specific client logic is needed to run the breakdown.
   */
  executableInput: unknown;
  /** Read tier only: the query result (propose executes reads inline). */
  readResult: unknown;
}

export interface Approval {
  tier: RiskTier;
  inputFingerprint: string;
  groupId: string;
  /** The exact id set the user reviewed. Execution aborts if resolution drifted. */
  affectedIds: string[];
}

function sameIdSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  const set = new Set(a);
  return b.every((id) => set.has(id));
}

export interface ExecutionResult {
  executed: boolean;
  tier: RiskTier;
  tool: ToolName;
  result: unknown;
  groupId: string | null;
  message: string | null;
  /** Audit identity of the committed ops (empty for reads/no-ops). */
  logEntryIds: string[];
}

export interface ExecutorServices {
  tasks: TaskService;
  calendar: CalendarService;
  notes: NoteService;
  prefs: PreferenceService;
  log: ActionLogService;
}

export interface AgentContext {
  sessionId?: string | undefined;
}

export function fingerprint(tool: ToolName, input: unknown): string {
  return `${tool}:${stableStringify(input)}`;
}
function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  if (value !== null && typeof value === "object") {
    const entries = Object.keys(value)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${stableStringify((value as Record<string, unknown>)[k])}`);
    return `{${entries.join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

/** Shape-checked by validators above; services re-validate values. */
function asInput<T>(value: Record<string, unknown>): T {
  return value as unknown as T;
}

function emptyProposal(tool: ToolName, input: unknown, message: string): Proposal {
  return {
    kind: "empty",
    tier: "read",
    tool,
    inputFingerprint: fingerprint(tool, input),
    groupId: null,
    preview: [],
    affectedIds: [],
    message,
    itemizedAlternative: null,
    executableInput: null,
    readResult: null,
  };
}

export class ToolExecutor {
  constructor(private readonly svc: ExecutorServices) {}

  /** Phase 1: validate -> resolve -> classify -> preview. No mutation, no log. */
  propose(tool: ToolName, input: unknown, ctx: AgentContext): Proposal {
    switch (tool) {
      case "searchTasks":
      case "findFreeTime":
      case "detectConflicts":
      case "searchNotes":
      case "getWorkspaceContext":
      case "globalSearch":
        return this.proposeRead(tool, input);
      case "createTask":
      case "updateTask":
      case "deleteTask":
        return this.proposeTaskTool(tool, input, ctx);
      case "createEvent":
      case "updateEvent":
      case "deleteEvent":
        return this.proposeEventTool(tool, input, ctx);
      case "createNote":
      case "updateNote":
      case "linkNoteToTask":
        return this.proposeNoteTool(tool, input, ctx);
      case "reschedule":
      case "optimizeSchedule":
      case "generateSchedule":
        return this.proposeComposite(tool, input, ctx);
      case "saveLearnedPreference":
      case "deleteLearnedPreference":
        return this.proposePreferenceTool(tool, input, ctx);
    }
  }

  /**
   * Phase 2: re-validate, re-resolve, re-classify, then execute per tier.
   * Confirm requires a matching approval; blocked always throws.
   */
  executeApproved(tool: ToolName, input: unknown, ctx: AgentContext, approval?: Approval): ExecutionResult {
    const proposal = this.propose(tool, input, ctx);
    if (proposal.kind === "blocked") {
      throw new FlowOSError(
        "BLOCKED_ACTION",
        proposal.message ?? "This action is blocked-by-default.",
        "Review the itemized confirm-tier breakdown instead — it lists exactly what would change.",
      );
    }
    if (proposal.kind === "empty") {
      return { executed: false, tier: "read", tool, result: null, groupId: null, message: proposal.message, logEntryIds: [] };
    }
    if (proposal.kind === "confirm") {
      if (!approval || approval.inputFingerprint !== proposal.inputFingerprint) {
        throw new FlowOSError(
          "AMBIGUOUS_REQUEST",
          "This plan needs explicit approval (or it was edited after approval — re-propose first).",
          "Approve the exact proposed plan, or edit it and review the new diff before approving.",
        );
      }
      // Id-set drift check: skipped for pure-creation proposals, whose
      // affected ids are propose-time placeholders (re-minted every propose —
      // comparing them always mismatches). Creations are bound by the exact
      // input fingerprint above instead.
      const isCreation = proposal.preview.length > 0 && proposal.preview.every((p) => {
        const before = Array.isArray(p.before) ? (p.before[0] ?? null) : p.before;
        return before === null;
      });
      if (!isCreation && !sameIdSet(approval.affectedIds, proposal.affectedIds)) {
        throw new FlowOSError(
          "VERIFICATION_MISMATCH",
          "The workspace changed between proposal and approval — the reviewed id set no longer matches.",
          "Re-propose the plan and approve the fresh diff.",
        );
      }
      const logEntryIds: string[] = [];
      const result = this.commit(tool, input, ctx, approval.groupId, proposal, logEntryIds);
      return { executed: true, tier: "confirm", tool, result, groupId: approval.groupId, message: null, logEntryIds };
    }
    // ready: auto mutates immediately; read returns the inline result.
    // An approval may still accompany ready proposals (multi-step plan with a
    // plan-level approval): its groupId joins the plan's undo unit.
    if (proposal.tier === "read") {
      return { executed: true, tier: "read", tool, result: proposal.readResult, groupId: null, message: null, logEntryIds: [] };
    }
    const autoEntryIds: string[] = [];
    const autoResult = this.commit(tool, input, ctx, approval?.groupId ?? null, null, autoEntryIds);
    return { executed: true, tier: "auto", tool, result: autoResult, groupId: approval?.groupId ?? null, message: null, logEntryIds: autoEntryIds };
  }

  // ---- reads (executed inline at propose; no mutation, no log) ----

  private proposeRead(tool: ToolName, input: unknown): Proposal {
    const readResult = this.runRead(tool, input);
    return {
      kind: "ready",
      tier: "read",
      tool,
      inputFingerprint: fingerprint(tool, input),
      groupId: null,
      preview: [],
      affectedIds: [],
      message: null,
      itemizedAlternative: null,
      executableInput: null,
      readResult,
    };
  }

  private runRead(tool: ToolName, input: unknown): unknown {
    switch (tool) {
      case "searchTasks": {
        const v = validateRecord(tool, input, "{ status?, priority?, tag?, dueFrom?, dueTo? }");
        if (!v.ok) throw v.error;
        // Unwrap if model wraps in { filter: { ... } }.
        const filterInput = (v.value.filter && typeof v.value.filter === "object") ? v.value.filter as Record<string, unknown> : v.value;
        if (!isTaskFilterShape(filterInput)) {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "searchTasks" got an invalid filter.`, "Pass a plain filter object (status/priority/tag/dueFrom/dueTo) and retry.");
        }
        return this.svc.tasks.listTasks(filterInput);
      }
      case "findFreeTime": {
        const v = validateRecord(tool, input, "{ from, to, durationMinutes }");
        if (!v.ok) throw v.error;
        const { from, to, durationMinutes } = v.value;
        if (typeof from !== "string" || typeof to !== "string" || typeof durationMinutes !== "number") {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "findFreeTime" needs { from, to, durationMinutes }.`, "Fix the arguments and retry.");
        }
        return this.svc.calendar.findFreeTime({ from, to }, durationMinutes);
      }
      case "detectConflicts": {
        const v = validateRecord(tool, input, "{ from, to }");
        if (!v.ok) throw v.error;
        if (!isEventRangeShape(v.value)) {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "detectConflicts" needs { from, to }.`, "Fix the arguments and retry.");
        }
        return this.svc.calendar.detectConflicts(v.value);
      }
      case "searchNotes": {
        const v = validateRecord(tool, input, "{ query }");
        if (!v.ok) throw v.error;
        if (typeof v.value["query"] !== "string") {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "searchNotes" needs { query }.`, "Fix the arguments and retry.");
        }
        return this.svc.notes.searchNotes(v.value["query"] as string);
      }
      case "getWorkspaceContext": {
        const v = validateRecord(tool, input, "{ date? }");
        if (!v.ok) throw v.error;
        const date = typeof v.value["date"] === "string" ? (v.value["date"] as string) : new Date().toISOString().slice(0, 10);
        return this.buildWorkspaceContext(date);
      }
      case "globalSearch": {
        const v = validateRecord(tool, input, "{ query }");
        if (!v.ok) throw v.error;
        if (typeof v.value["query"] !== "string" || !(v.value["query"] as string).trim()) {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "globalSearch" needs a non-empty { query }.`, "Fix the arguments and retry.");
        }
        return this.runGlobalSearch((v.value["query"] as string).trim());
      }
      default:
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Unknown read tool "${tool}".`, "Use one of the registered tool names.");
    }
  }

  private buildWorkspaceContext(date: string): Record<string, unknown> {
    const day = date.slice(0, 10);
    const tasksDue = this.svc.tasks.listTasks().filter((t) => t.dueAt !== null && t.dueAt.slice(0, 10) <= day && t.status !== "done");
    const events = this.svc.calendar.listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` });
    const upcoming = this.svc.calendar
      .listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` })
      .slice(0, 3);
    return {
      date: day,
      counts: {
        tasksOpen: this.svc.tasks.listTasks().filter((t) => t.status !== "done").length,
        tasksDueToday: tasksDue.length,
        eventsToday: events.length,
        notes: this.svc.notes.listNotes().length,
        preferences: this.svc.prefs.listPreferences().length,
      },
      tasksDueToday: tasksDue,
      upcomingEvents: upcoming,
    };
  }

  private runGlobalSearch(query: string): Record<string, unknown> {
    const q = query.toLowerCase();
    const tasks = this.svc.tasks
      .listTasks()
      .filter((t) => `${t.title} ${t.description ?? ""} ${t.tag ?? ""}`.toLowerCase().includes(q));
    const notes = this.svc.notes.searchNotes(query).map((h) => h.note);
    const now = new Date();
    const from = new Date(now.getTime() - 365 * 86_400_000).toISOString();
    const to = new Date(now.getTime() + 365 * 86_400_000).toISOString();
    const events = this.svc.calendar.listEvents({ from, to }).filter((e) => e.title.toLowerCase().includes(q));
    return { tasks, events, notes };
  }

  // ---- task tools ----

  private proposeTaskTool(tool: "createTask" | "updateTask" | "deleteTask", input: unknown, ctx: AgentContext): Proposal {
    const mctx: MutationContext = { actor: "agent", sessionId: ctx.sessionId, dryRun: true };
    if (tool === "createTask") {
      const v = validateRecord(tool, input, "{ title, description?, dueAt?, priority?, status?, tag?, estimatedMinutes? }");
      if (!v.ok) throw v.error;
      const created = this.svc.tasks.createTask(asInput<Parameters<TaskService["createTask"]>[0]>(v.value), mctx);
      return this.readyOrConfirm(tool, input, [{ before: null, after: created }], [created.id], null);
    }
    if (tool === "updateTask") {
      const v = validateUpdateTasks(input);
      if (!v.ok) throw v.error;
      const ids = this.resolveTaskIds(v.value);
      if (ids.length === 0) {
        return emptyProposal(tool, input, "No tasks match that scope — nothing to change. Adjust the filter or rephrase the request.");
      }
      const patch = v.value.patch as Parameters<TaskService["updateTask"]>[1];
      const preview = ids.map((id) => {
        const p = this.svc.tasks.previewUpdate(id, patch, { actor: "agent", sessionId: ctx.sessionId });
        return { before: p.before, after: p.after };
      });
      return this.readyOrConfirm(tool, input, preview, ids, ids.length > 1 ? randomUUID() : null);
    }
    // deleteTask: explicit ids -> confirm (itemized); filter -> blocked + itemized alternative.
    const v = validateDeleteTasks(input);
    if (!v.ok) throw v.error;
    if (v.value.filter !== undefined) {
      const ids = this.svc.tasks.listTasks(v.value.filter).map((t) => t.id);
      if (ids.length === 0) {
        return emptyProposal(tool, input, "No tasks match that scope — nothing to delete.");
      }
      return this.blockedWithItemized(tool, input, "task", ids);
    }
    const ids = v.value.ids ?? [];
    const preview = ids.map((id) => {
      const p = this.svc.tasks.previewDelete(id, { actor: "agent", sessionId: ctx.sessionId });
      return { before: p.before, after: p.after };
    });
    return this.readyOrConfirm(tool, input, preview, ids, ids.length > 1 ? randomUUID() : null);
  }

  // ---- event tools ----

  private proposeEventTool(tool: "createEvent" | "updateEvent" | "deleteEvent", input: unknown, ctx: AgentContext): Proposal {
    const mctx: MutationContext = { actor: "agent", sessionId: ctx.sessionId, dryRun: true };
    if (tool === "createEvent") {
      const v = validateRecord(tool, input, "{ title, startAt, endAt, isFlexible?, linkedTaskId? }");
      if (!v.ok) throw v.error;
      const created = this.svc.calendar.createEvent(asInput<Parameters<CalendarService["createEvent"]>[0]>(v.value), mctx);
      return this.readyOrConfirm(tool, input, [{ before: null, after: created }], [created.id], null);
    }
    if (tool === "updateEvent") {
      const v = validateUpdateEvents(input);
      if (!v.ok) throw v.error;
      const ids = this.resolveEventIds(v.value);
      if (ids.length === 0) {
        return emptyProposal(tool, input, "No events match that scope — nothing to change. Adjust the range or rephrase the request.");
      }
      const patch = v.value.patch as Parameters<CalendarService["updateEvent"]>[1];
      const preview = ids.map((id) => {
        const p = this.svc.calendar.previewUpdate(id, patch, { actor: "agent", sessionId: ctx.sessionId });
        return { before: p.before, after: p.after };
      });
      // Single-item conflict/cascade check from the dry-run image (resolved scope, not request text).
      let cascade = ids.length > 1;
      if (!cascade && ids.length === 1) {
        const wouldBe = this.svc.calendar.updateEvent(ids[0] as string, patch, { ...mctx });
        cascade = this.wouldConflict(ids[0] as string, wouldBe.startAt, wouldBe.endAt);
      }
      const classification = classifyRisk({ tool, resolvedIds: ids, createsConflictOrCascade: cascade });
      return this.toProposal(tool, input, classification.tier, preview, ids, ids.length > 1 || cascade ? randomUUID() : null);
    }
    const v = validateDeleteEvents(input);
    if (!v.ok) throw v.error;
    if (v.value.filter !== undefined) {
      const ids = this.svc.calendar.listEvents({ ...v.value.filter }).map((e) => e.id);
      if (ids.length === 0) {
        return emptyProposal(tool, input, "No events match that scope — nothing to delete.");
      }
      return this.blockedWithItemized(tool, input, "event", ids);
    }
    const ids = v.value.ids ?? [];
    const preview = ids.map((id) => {
      const p = this.svc.calendar.previewDelete(id, { actor: "agent", sessionId: ctx.sessionId });
      return { before: p.before, after: p.after };
    });
    return this.readyOrConfirm(tool, input, preview, ids, ids.length > 1 ? randomUUID() : null);
  }

  // ---- note tools ----

  private proposeNoteTool(tool: "createNote" | "updateNote" | "linkNoteToTask", input: unknown, ctx: AgentContext): Proposal {
    const mctx: MutationContext = { actor: "agent", sessionId: ctx.sessionId, dryRun: true };
    if (tool === "createNote") {
      const v = validateRecord(tool, input, "{ title, content?, tags?, linkedTaskIds? }");
      if (!v.ok) throw v.error;
      const created = this.svc.notes.createNote(asInput<Parameters<NoteService["createNote"]>[0]>(v.value), mctx);
      return this.readyOrConfirm(tool, input, [{ before: null, after: created }], [created.id], null);
    }
    if (tool === "updateNote") {
      const v = validateRecord(tool, input, "{ id, patch }");
      if (!v.ok) throw v.error;
      const { id, patch } = v.value;
      if (typeof id !== "string" || typeof patch !== "object" || patch === null) {
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "updateNote" needs { id, patch }.`, "Fix the arguments and retry.");
      }
      const p = this.svc.notes.previewUpdate(id, patch as Parameters<NoteService["updateNote"]>[1], {
        actor: "agent",
        sessionId: ctx.sessionId,
      });
      return this.readyOrConfirm(tool, input, [{ before: p.before, after: p.after }], [id], null);
    }
    const v = validateRecord(tool, input, "{ noteId, taskId }");
    if (!v.ok) throw v.error;
    const { noteId, taskId } = v.value;
    if (typeof noteId !== "string" || typeof taskId !== "string") {
      throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "linkNoteToTask" needs { noteId, taskId }.`, "Fix the arguments and retry.");
    }
    const p = this.svc.notes.previewLinkNoteToTask(noteId, taskId, { actor: "agent", sessionId: ctx.sessionId });
    return this.readyOrConfirm(tool, input, [{ before: p.before, after: p.after }], [noteId], null);
  }

  // ---- preference tools ----

  private proposePreferenceTool(tool: "saveLearnedPreference" | "deleteLearnedPreference", input: unknown, ctx: AgentContext): Proposal {
    if (tool === "saveLearnedPreference") {
      const v = validateRecord(tool, input, "{ statement }");
      if (!v.ok) throw v.error;
      if (typeof v.value["statement"] !== "string") {
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "saveLearnedPreference" needs { statement }.`, "Fix the arguments and retry.");
      }
      const p = this.svc.prefs.previewSaveLearnedPreference(v.value["statement"] as string, {
        actor: "agent",
        sessionId: ctx.sessionId,
      });
      return this.readyOrConfirm(tool, input, [{ before: p.before, after: p.after }], [], null);
    }
    const v = validateRecord(tool, input, "{ id }");
    if (!v.ok) throw v.error;
    if (typeof v.value["id"] !== "string") {
      throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "deleteLearnedPreference" needs { id }.`, "Fix the arguments and retry.");
    }
    const id = v.value["id"] as string;
    const p = this.svc.prefs.previewDeleteLearnedPreference(id, { actor: "agent", sessionId: ctx.sessionId });
    return this.readyOrConfirm(tool, input, [{ before: p.before, after: p.after }], [id], null);
  }

  // ---- composites (always confirm floor; one groupId per plan) ----

  private proposeComposite(tool: "reschedule" | "optimizeSchedule" | "generateSchedule", input: unknown, ctx: AgentContext): Proposal {
    const groupId = randomUUID();
    if (tool === "reschedule") {
      const v = validateRecord(tool, input, "{ eventId, newStartAt, newEndAt }");
      if (!v.ok) throw v.error;
      const { eventId, newStartAt, newEndAt } = v.value;
      if (typeof eventId !== "string" || typeof newStartAt !== "string" || typeof newEndAt !== "string") {
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "reschedule" needs { eventId, newStartAt, newEndAt }.`, "Fix the arguments and retry.");
      }
      return this.previewReschedule(input, eventId, newStartAt, newEndAt, ctx, groupId);
    }
    if (tool === "optimizeSchedule") {
      const v = validateRecord(tool, input, "{ date } (YYYY-MM-DD)");
      if (!v.ok) throw v.error;
      if (typeof v.value["date"] !== "string") {
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "optimizeSchedule" needs { date }.`, "Fix the arguments and retry.");
      }
      return this.previewOptimize(input, v.value["date"] as string, ctx, groupId);
    }
    const v = validateRecord(tool, input, "{ dateFrom, dateTo, taskIds? }");
    if (!v.ok) throw v.error;
    const { dateFrom, dateTo, taskIds } = v.value;
    if (typeof dateFrom !== "string" || typeof dateTo !== "string") {
      throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "generateSchedule" needs { dateFrom, dateTo }.`, "Fix the arguments and retry.");
    }
    if (taskIds !== undefined && !(Array.isArray(taskIds) && taskIds.every((e) => typeof e === "string"))) {
      throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "generateSchedule" taskIds must be a string array.`, "Fix the arguments and retry.");
    }
    return this.previewGenerate(input, dateFrom, dateTo, (taskIds as string[] | undefined) ?? null, ctx, groupId);
  }

  /**
   * Deterministic cascade: the moved event first, then flexible overlappers
   * shifted in start order (duration-preserving); fixed conflicts are listed
   * as unmoved warnings, never silently relocated. Shared by preview and
   * commit so the approved diff and the execution cannot drift apart.
   */
  private computeCascade(
    moved: { id: string; startAt: string; endAt: string },
    siblings: Array<{ id: string; title: string; startAt: string; endAt: string; isFlexible: boolean }>,
  ): { shifts: Array<{ id: string; startAt: string; endAt: string }>; unmoved: string[] } {
    const shifts: Array<{ id: string; startAt: string; endAt: string }> = [];
    const unmoved: string[] = [];
    let cursorMs = Date.parse(moved.endAt);
    for (const sib of siblings
      .filter((e) => e.id !== moved.id && e.startAt < moved.endAt && moved.startAt < e.endAt)
      .sort((a, b) => (a.startAt < b.startAt ? -1 : 1))) {
      if (!sib.isFlexible) {
        unmoved.push(sib.title);
        continue;
      }
      const durMs = Date.parse(sib.endAt) - Date.parse(sib.startAt);
      const startMs = Math.max(cursorMs, Date.parse(sib.startAt));
      shifts.push({ id: sib.id, startAt: new Date(startMs).toISOString(), endAt: new Date(startMs + durMs).toISOString() });
      cursorMs = startMs + durMs;
    }
    return { shifts, unmoved };
  }

  private previewReschedule(
    input: unknown, eventId: string, newStartAt: string, newEndAt: string, ctx: AgentContext, groupId: string,
  ): Proposal {
    const target = this.svc.calendar.getEvent(eventId);
    if (!target) {
      throw new FlowOSError("NOT_FOUND", `Event ${eventId} not found — nothing was rescheduled.`, "Search for the event first instead of guessing its id.");
    }
    const moved = this.svc.calendar.updateEvent(
      eventId,
      { startAt: newStartAt, endAt: newEndAt },
      { actor: "agent", sessionId: ctx.sessionId, dryRun: true },
    );
    const siblings = this.svc.calendar.listEvents({
      from: moved.startAt.slice(0, 10) + "T00:00:00",
      to: moved.endAt.slice(0, 10) + "T23:59:59",
    });
    const { shifts, unmoved } = this.computeCascade(moved, siblings);
    const preview: PreviewPair[] = [{ before: target, after: moved }];
    const affectedIds = [eventId];
    for (const shift of shifts) {
      const current = this.svc.calendar.getEvent(shift.id);
      preview.push({ before: current, after: { ...current, ...shift } });
      affectedIds.push(shift.id);
    }
    const classification = classifyRisk({ tool: "reschedule", resolvedIds: affectedIds });
    const proposal = this.toProposal("reschedule", input, classification.tier, preview, affectedIds, groupId);
    proposal.message =
      unmoved.length > 0
        ? `Note: ${unmoved.length} conflicting fixed event(s) (${unmoved.join(", ")}) were NOT moved — review them after approving.`
        : null;
    return proposal;
  }

  private previewOptimize(input: unknown, date: string, ctx: AgentContext, groupId: string): Proposal {
    const day = date.slice(0, 10);
    const events = this.svc.calendar.listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` });
    const flexible = events.filter((e) => e.isFlexible).sort((a, b) => (a.startAt < b.startAt ? -1 : 1));
    if (flexible.length === 0) {
      return emptyProposal(
        "optimizeSchedule",
        input,
        events.length === 0
          ? `No events on ${day} — nothing to lighten.`
          : `All ${events.length} event(s) on ${day} are fixed — nothing the agent may defer. Mark events flexible or move one manually.`,
      );
    }
    const nextDay = new Date(Date.parse(`${day}T00:00:00`) + 86_400_000).toISOString().slice(0, 10);
    const free = this.svc.calendar.findFreeTime({ from: `${nextDay}T00:00:00`, to: `${nextDay}T23:59:59` }, 15);
    const preview: PreviewPair[] = [];
    const affectedIds: string[] = [];
    const unplaced: string[] = [];
    const slots = [...free];
    for (const event of flexible) {
      const durMin = Math.round((Date.parse(event.endAt) - Date.parse(event.startAt)) / 60_000);
      const slotIdx = slots.findIndex(
        (s) => Date.parse(s.endAt) - Date.parse(s.startAt) >= durMin * 60_000,
      );
      if (slotIdx === -1) {
        unplaced.push(event.title);
        continue;
      }
      const slot = slots.splice(slotIdx, 1)[0] as { startAt: string; endAt: string };
      const moved = {
        ...event,
        startAt: slot.startAt,
        endAt: new Date(Date.parse(slot.startAt) + durMin * 60_000).toISOString(),
      };
      preview.push({ before: event, after: moved });
      affectedIds.push(event.id);
    }
    if (preview.length === 0) {
      return emptyProposal("optimizeSchedule", input, `Flexible events exist on ${day} but tomorrow has no room (${unplaced.join(", ")}). Try a wider window.`);
    }
    const classification = classifyRisk({ tool: "optimizeSchedule", resolvedIds: affectedIds });
    const proposal = this.toProposal("optimizeSchedule", input, classification.tier, preview, affectedIds, groupId);
    if (unplaced.length > 0) {
      proposal.message = `Could not place: ${unplaced.join(", ")} (no fitting free time tomorrow).`;
    }
    return proposal;
  }

  private previewGenerate(
    input: unknown, dateFrom: string, dateTo: string, taskIds: string[] | null, ctx: AgentContext, groupId: string,
  ): Proposal {
    const all = this.svc.tasks.listTasks().filter((t) => t.status !== "done");
    const wanted = taskIds ? new Set(taskIds) : null;
    const rank: Record<string, number> = { urgent: 0, normal: 1, low: 2 };
    const candidates = all
      .filter((t) => (wanted ? wanted.has(t.id) : true))
      .sort((a, b) => (rank[a.priority] ?? 1) - (rank[b.priority] ?? 1) || (a.dueAt ?? "~").localeCompare(b.dueAt ?? "~"));
    if (taskIds) {
      const missing = taskIds.filter((id) => !all.some((t) => t.id === id));
      if (missing.length > 0) {
        throw new FlowOSError("NOT_FOUND", `Task(s) not found: ${missing.join(", ")} — nothing was scheduled.`, "Search for the tasks first instead of guessing ids.");
      }
    }
    if (candidates.length === 0) {
      return emptyProposal("generateSchedule", input, "No open tasks to schedule — nothing to do.");
    }
    const free = this.svc.calendar.findFreeTime({ from: dateFrom, to: dateTo }, 15);
    const slots = [...free];
    const preview: PreviewPair[] = [];
    const affectedIds: string[] = [];
    const unscheduled: string[] = [];
    for (const task of candidates) {
      const durMin = task.estimatedMinutes ?? 30;
      const slotIdx = slots.findIndex((s) => Date.parse(s.endAt) - Date.parse(s.startAt) >= durMin * 60_000);
      if (slotIdx === -1) {
        unscheduled.push(task.title);
        continue;
      }
      const slot = slots.splice(slotIdx, 1)[0] as { startAt: string; endAt: string };
      const startAt = slot.startAt;
      const endAt = new Date(Date.parse(startAt) + durMin * 60_000).toISOString();
      const draft = this.svc.calendar.createEvent(
        { title: task.title, startAt, endAt, linkedTaskId: task.id },
        { actor: "agent", sessionId: ctx.sessionId, dryRun: true },
      );
      preview.push({ before: null, after: draft });
      affectedIds.push(task.id);
    }
    if (preview.length === 0) {
      return emptyProposal("generateSchedule", input, `No free time fits any task (${unscheduled.join(", ")}). Widen the window or free up the calendar first.`);
    }
    const classification = classifyRisk({ tool: "generateSchedule", resolvedIds: affectedIds });
    const proposal = this.toProposal("generateSchedule", input, classification.tier, preview, affectedIds, groupId);
    if (unscheduled.length > 0) {
      proposal.message = `Did not fit: ${unscheduled.join(", ")}.`;
    }
    return proposal;
  }

  // ---- shared helpers ----

  private resolveTaskIds(sel: { ids?: string[]; filter?: TaskFilter }): string[] {
    if (sel.ids) return sel.ids;
    if (sel.filter) return this.svc.tasks.listTasks(sel.filter).map((t) => t.id);
    return [];
  }

  private resolveEventIds(sel: { ids?: string[]; filter?: { from: string; to: string } }): string[] {
    if (sel.ids) return sel.ids;
    if (sel.filter) return this.svc.calendar.listEvents({ ...sel.filter }).map((e) => e.id);
    return [];
  }

  private wouldConflict(excludeId: string, startAt: string, endAt: string): boolean {
    const day = startAt.slice(0, 10);
    return this.svc.calendar
      .listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` })
      .some((e) => e.id !== excludeId && e.startAt < endAt && startAt < e.endAt);
  }

  private readyOrConfirm(tool: ToolName, input: unknown, preview: PreviewPair[], affectedIds: string[], groupId: string | null): Proposal {
    const classification = classifyRisk({ tool, resolvedIds: affectedIds });
    return this.toProposal(tool, input, classification.tier, preview, affectedIds, groupId ?? (classification.tier === "confirm" ? randomUUID() : null));
  }

  private toProposal(tool: ToolName, input: unknown, tier: RiskTier, preview: PreviewPair[], affectedIds: string[], groupId: string | null): Proposal {
    return {
      kind: tier === "confirm" ? "confirm" : "ready",
      tier,
      tool,
      inputFingerprint: fingerprint(tool, input),
      groupId,
      preview,
      affectedIds,
      message: null,
      itemizedAlternative: null,
      executableInput: null,
      readResult: null,
    };
  }

  private blockedWithItemized(tool: "deleteTask" | "deleteEvent", input: unknown, targetType: "task" | "event", ids: string[]): Proposal {
    const classification = classifyFilterBulkDelete(tool);
    // The itemized breakdown is built WITHOUT committing (dryRun previews) —
    // the blocked plan itself writes zero mutation rows (verdict: user never asks twice).
    const itemizedPreview: PreviewPair[] = ids.map((id) => {
      const p =
        targetType === "task"
          ? this.svc.tasks.previewDelete(id, { actor: "agent" })
          : this.svc.calendar.previewDelete(id, { actor: "agent" });
      return { before: p.before, after: p.after };
    });
    const itemized: Proposal = {
      kind: "confirm",
      tier: "confirm",
      tool,
      inputFingerprint: fingerprint(tool, { ids }),
      groupId: randomUUID(),
      preview: itemizedPreview,
      affectedIds: ids,
      message: null,
      itemizedAlternative: null,
      executableInput: { ids },
      readResult: null,
    };
    return {
      kind: "blocked",
      tier: classification.tier,
      tool,
      inputFingerprint: fingerprint(tool, input),
      groupId: null,
      preview: [],
      affectedIds: ids,
      message:
        `${classification.reason} Matched ${ids.length} ${targetType}(s). ` +
        `Review the itemized breakdown below — approving it deletes exactly the listed items, nothing else.`,
      itemizedAlternative: itemized,
      executableInput: null,
      readResult: null,
    };
  }

  // ---- commit (phase 2; only reachable for ready/approved-confirm) ----
  // Approved-confirm commits reuse the APPROVED proposal's preview (already
  // id-set-checked), so execution cannot drift from the reviewed diff.

  private commit(tool: ToolName, input: unknown, ctx: AgentContext, groupId: string | null, approved: Proposal | null, idsOut: string[]): unknown {
    const mctx: MutationContext = { actor: "agent", sessionId: ctx.sessionId, ...(groupId ? { groupId } : {}) };
    switch (tool) {
      case "createTask": {
        const v = this.requireValid(validateRecord(tool, input, "{ title, … }"));
        return this.tracked(idsOut, mctx.sessionId, "createTask", () =>
          this.svc.tasks.createTask(asInput<Parameters<TaskService["createTask"]>[0]>(v), mctx));
      }
      case "updateTask": {
        const v = this.requireValid(validateUpdateTasks(input));
        const ids = this.resolveTaskIds(v);
        const patch = v.patch as Parameters<TaskService["updateTask"]>[1];
        return ids.map((id) => this.tracked(idsOut, mctx.sessionId, "updateTask", () => this.svc.tasks.updateTask(id, patch, mctx)));
      }
      case "deleteTask": {
        const v = this.requireValid(validateDeleteTasks(input));
        if (v.filter !== undefined) {
          throw new FlowOSError("BLOCKED_ACTION", "Filter-based bulk delete cannot execute.", "Use the itemized confirm-tier breakdown instead.");
        }
        return (v.ids ?? []).map((id) => this.tracked(idsOut, mctx.sessionId, "deleteTask", () => this.svc.tasks.deleteTask(id, mctx)));
      }
      case "createEvent": {
        const v = this.requireValid(validateRecord(tool, input, "{ title, startAt, endAt, … }"));
        return this.tracked(idsOut, mctx.sessionId, "createEvent", () =>
          this.svc.calendar.createEvent(asInput<Parameters<CalendarService["createEvent"]>[0]>(v), mctx));
      }
      case "updateEvent": {
        const v = this.requireValid(validateUpdateEvents(input));
        const ids = this.resolveEventIds(v);
        const patch = v.patch as Parameters<CalendarService["updateEvent"]>[1];
        return ids.map((id) => this.tracked(idsOut, mctx.sessionId, "updateEvent", () => this.svc.calendar.updateEvent(id, patch, mctx)));
      }
      case "deleteEvent": {
        const v = this.requireValid(validateDeleteEvents(input));
        if (v.filter !== undefined) {
          throw new FlowOSError("BLOCKED_ACTION", "Filter-based bulk delete cannot execute.", "Use the itemized confirm-tier breakdown instead.");
        }
        return (v.ids ?? []).map((id) => this.tracked(idsOut, mctx.sessionId, "deleteEvent", () => this.svc.calendar.deleteEvent(id, mctx)));
      }
      case "createNote": {
        const v = this.requireValid(validateRecord(tool, input, "{ title, … }"));
        return this.tracked(idsOut, mctx.sessionId, "createNote", () =>
          this.svc.notes.createNote(asInput<Parameters<NoteService["createNote"]>[0]>(v), mctx));
      }
      case "updateNote": {
        const v = this.requireValid(validateRecord(tool, input, "{ id, patch }"));
        const { id, patch } = v;
        if (typeof id !== "string") throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "updateNote" needs { id, patch }.`, "Fix the arguments and retry.");
        return this.tracked(idsOut, mctx.sessionId, "updateNote", () =>
          this.svc.notes.updateNote(id, (patch ?? {}) as Parameters<NoteService["updateNote"]>[1], mctx));
      }
      case "linkNoteToTask": {
        const v = this.requireValid(validateRecord(tool, input, "{ noteId, taskId }"));
        const { noteId, taskId } = v;
        if (typeof noteId !== "string" || typeof taskId !== "string") {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "linkNoteToTask" needs { noteId, taskId }.`, "Fix the arguments and retry.");
        }
        return this.tracked(idsOut, mctx.sessionId, "linkNoteToTask", () => this.svc.notes.linkNoteToTask(noteId, taskId, mctx));
      }
      case "reschedule": {
        const v = this.requireValid(validateRecord(tool, input, "{ eventId, newStartAt, newEndAt }"));
        return this.commitReschedule(v as { eventId: string; newStartAt: string; newEndAt: string }, mctx, idsOut);
      }
      case "optimizeSchedule": {
        if (!approved || approved.kind !== "confirm") {
          throw new FlowOSError("STORE", "Optimize plan changed between approval and execution.", "Re-propose the plan and approve the fresh diff.");
        }
        return this.commitMoves(approved, mctx, idsOut);
      }
      case "generateSchedule": {
        if (!approved || approved.kind !== "confirm") {
          throw new FlowOSError("STORE", "Schedule plan changed between approval and execution.", "Re-propose the plan and approve the fresh diff.");
        }
        return this.commitCreations(approved, mctx, idsOut);
      }
      case "saveLearnedPreference": {
        const v = this.requireValid(validateRecord(tool, input, "{ statement }"));
        if (typeof v["statement"] !== "string") {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "saveLearnedPreference" needs { statement }.`, "Fix the arguments and retry.");
        }
        return this.tracked(idsOut, mctx.sessionId, "saveLearnedPreference", () =>
          this.svc.prefs.saveLearnedPreference(v["statement"] as string, mctx));
      }
      case "deleteLearnedPreference": {
        const v = this.requireValid(validateRecord(tool, input, "{ id }"));
        if (typeof v["id"] !== "string") {
          throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "deleteLearnedPreference" needs { id }.`, "Fix the arguments and retry.");
        }
        return this.tracked(idsOut, mctx.sessionId, "deleteLearnedPreference", () =>
          this.svc.prefs.deleteLearnedPreference(v["id"] as string, mctx));
      }
      default:
        throw new FlowOSError("MALFORMED_TOOL_CALL", `Tool "${tool}" is read-only or unknown; nothing to commit.`, "Propose reads instead of executing them.");
    }
  }

  /**
   * Run one mutating service call, then attach its audit row id (looked up by
   * session+operation, rowid-ordered) so step records — and therefore UI
   * affordances like toast Undo — target exact rows, never "the latest whatever".
   */
  private tracked<T>(idsOut: string[], sessionId: string | undefined, op: string, fn: () => T): T {
    const value = fn();
    if (sessionId) {
      const entry = this.svc.log.findLatest(sessionId, op);
      if (entry) idsOut.push(entry.id);
    }
    return value;
  }

  private commitReschedule(plan: { eventId: string; newStartAt: string; newEndAt: string }, mctx: MutationContext, idsOut: string[]): unknown {
    // Same deterministic algorithm as preview (shared computeCascade), one groupId.
    const moved = this.tracked(idsOut, mctx.sessionId, "updateEvent", () =>
      this.svc.calendar.updateEvent(plan.eventId, { startAt: plan.newStartAt, endAt: plan.newEndAt }, mctx));
    const siblings = this.svc.calendar.listEvents({
      from: moved.startAt.slice(0, 10) + "T00:00:00",
      to: moved.endAt.slice(0, 10) + "T23:59:59",
    });
    const { shifts } = this.computeCascade(moved, siblings);
    const results: unknown[] = [moved];
    for (const shift of shifts) {
      results.push(this.tracked(idsOut, mctx.sessionId, "updateEvent", () =>
        this.svc.calendar.updateEvent(shift.id, { startAt: shift.startAt, endAt: shift.endAt }, mctx)));
    }
    return results;
  }

  private commitMoves(proposal: Proposal, mctx: MutationContext, idsOut: string[]): unknown {
    // after-images carry full would-be event objects; commit by id.
    const results: unknown[] = [];
    for (const pair of proposal.preview) {
      const after = pair.after as { id: string; title: string; startAt: string; endAt: string } | null;
      if (!after) continue;
      results.push(
        this.tracked(idsOut, mctx.sessionId, "updateEvent", () =>
          this.svc.calendar.updateEvent(after.id, { startAt: after.startAt, endAt: after.endAt }, mctx)),
      );
    }
    return results;
  }

  private commitCreations(proposal: Proposal, mctx: MutationContext, idsOut: string[]): unknown {
    const results: unknown[] = [];
    for (const pair of proposal.preview) {
      const after = pair.after as {
        title: string; startAt: string; endAt: string; linkedTaskId: string | null;
      } | null;
      if (!after) continue;
      results.push(
        this.tracked(idsOut, mctx.sessionId, "createEvent", () =>
          this.svc.calendar.createEvent(
            { title: after.title, startAt: after.startAt, endAt: after.endAt, linkedTaskId: after.linkedTaskId },
            mctx,
          )),
      );
    }
    return results;
  }

  private requireValid<T>(v: Validation<T>): T {
    if (!v.ok) throw v.error;
    return v.value;
  }
}

export function describeTool(tool: ToolName): { name: ToolName; description: string; tier: RiskTier } {
  const def = getToolDefinition(tool);
  return { name: def.name, description: def.description, tier: def.baseTier };
}
