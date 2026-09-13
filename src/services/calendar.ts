// CalendarService — events, conflict detection, free-time queries.
// Overlaps are detected, never silently created by the agent path: the tool
// layer escalates conflict-creating moves to confirm-tier (docs/10 §3).
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { CalendarEvent, MutationContext, PreviewResult } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";
import { parseIso, readRawAligned, readRawOne } from "./rows.js";

type Row = Record<string, unknown>;

export interface CreateEventInput {
  title: string;
  startAt: string;
  endAt: string;
  isFlexible?: boolean;
  linkedTaskId?: string | null;
  workspaceId?: string;
}

export interface UpdateEventPatch {
  title?: string;
  startAt?: string;
  endAt?: string;
  isFlexible?: boolean;
  linkedTaskId?: string | null;
}

export interface EventRange {
  from: string;
  to: string;
  workspaceId?: string;
}

export interface FreeSlot {
  startAt: string;
  endAt: string;
}

function toEvent(row: Row): CalendarEvent {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    title: row["title"] as string,
    startAt: row["start_at"] as string,
    endAt: row["end_at"] as string,
    isFlexible: (row["is_flexible"] as number) === 1,
    linkedTaskId: (row["linked_task_id"] as string | null) ?? null,
    createdAt: row["created_at"] as string,
    updatedAt: row["updated_at"] as string,
    source: row["source"] as CalendarEvent["source"],
  };
}

function overlaps(aStart: string, aEnd: string, bStart: string, bEnd: string): boolean {
  return aStart < bEnd && bStart < aEnd;
}

export class CalendarService {
  private readonly log: ActionLogService;

  constructor(private readonly db: FlowDatabase) {
    this.log = new ActionLogService(db);
  }

  getEvent(id: string): CalendarEvent | null {
    const row = readRawOne(this.db, "events", id);
    return row ? toEvent(row) : null;
  }

  listEvents(range: EventRange): CalendarEvent[] {
    // Normalize bounds to UTC ISO: naive local strings and stored UTC
    // strings do not compare correctly as raw text.
    const from = parseIso(range.from);
    const to = parseIso(range.to);
    if (!from || !to || from >= to) {
      throw new FlowOSError(
        "VALIDATION",
        "Event range is invalid (need parseable from < to).",
        "Pass an ISO-8601 range with from earlier than to.",
      );
    }
    const ws = range.workspaceId ?? DEFAULT_WORKSPACE_ID;
    const rows = this.db
      .prepare(
        `SELECT * FROM events
         WHERE workspace_id = ? AND start_at < ? AND end_at > ?
         ORDER BY start_at`,
      )
      .all(ws, to, from) as Row[];
    return rows.map(toEvent);
  }

  createEvent(input: CreateEventInput, ctx: MutationContext): CalendarEvent {
    if (typeof input.title !== "string" || !input.title.trim()) {
      throw new FlowOSError("VALIDATION", "Event title cannot be empty.", "Give the event a short title and try again.");
    }
    const title = input.title.trim();
    const { startAt, endAt } = this.checkedRange(input.startAt, input.endAt);
    if (input.linkedTaskId !== undefined && input.linkedTaskId !== null) {
      this.requireTask(input.linkedTaskId);
    }
    const now = nowIso();
    const event: CalendarEvent = {
      id: randomUUID(),
      workspaceId: input.workspaceId ?? DEFAULT_WORKSPACE_ID,
      title,
      startAt,
      endAt,
      isFlexible: input.isFlexible ?? false,
      linkedTaskId: input.linkedTaskId ?? null,
      createdAt: now,
      updatedAt: now,
      source: ctx.actor,
    };
    if (ctx.dryRun) {
      return event;
    }
    this.db
      .prepare(
        `INSERT INTO events
         (id, workspace_id, title, start_at, end_at, is_flexible,
          linked_task_id, created_at, updated_at, source)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        event.id, event.workspaceId, event.title, event.startAt, event.endAt,
        event.isFlexible ? 1 : 0, event.linkedTaskId,
        event.createdAt, event.updatedAt, event.source,
      );
    const after = readRawAligned(this.db, "events", [event.id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "createEvent",
      targetType: "event",
      targetIds: [event.id],
      beforeState: [null],
      afterState: after,
      groupId: ctx.groupId,
    });
    return event;
  }

  updateEvent(id: string, patch: UpdateEventPatch, ctx: MutationContext): CalendarEvent {
    const existing = this.getEvent(id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Event ${id} not found.`, "Refresh the calendar — it may have been deleted already.");
    }
    const title = patch.title === undefined ? existing.title : patch.title.trim();
    if (!title) {
      throw new FlowOSError("VALIDATION", "Event title cannot be empty.", "Give the event a short title and try again.");
    }
    const { startAt, endAt } = this.checkedRange(
      patch.startAt ?? existing.startAt,
      patch.endAt ?? existing.endAt,
    );
    const linkedTaskId = patch.linkedTaskId === undefined ? existing.linkedTaskId : patch.linkedTaskId;
    if (linkedTaskId !== null) {
      this.requireTask(linkedTaskId);
    }
    const updated: CalendarEvent = {
      ...existing,
      title,
      startAt,
      endAt,
      isFlexible: patch.isFlexible ?? existing.isFlexible,
      linkedTaskId,
      updatedAt: nowIso(),
      source: ctx.actor,
    };
    const before = readRawAligned(this.db, "events", [id]);
    if (ctx.dryRun) {
      return updated;
    }
    this.db
      .prepare(
        `UPDATE events SET title = ?, start_at = ?, end_at = ?, is_flexible = ?,
         linked_task_id = ?, updated_at = ?, source = ? WHERE id = ?`,
      )
      .run(
        updated.title, updated.startAt, updated.endAt,
        updated.isFlexible ? 1 : 0, updated.linkedTaskId,
        updated.updatedAt, updated.source, id,
      );
    const after = readRawAligned(this.db, "events", [id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "updateEvent",
      targetType: "event",
      targetIds: [id],
      beforeState: before,
      afterState: after,
      groupId: ctx.groupId,
    });
    return updated;
  }

  previewCreate(input: CreateEventInput, ctx: MutationContext): PreviewResult<CalendarEvent> {
    const result = this.createEvent(input, { ...ctx, dryRun: true });
    return { committed: false, before: [null], after: [result], result };
  }

  previewUpdate(id: string, patch: UpdateEventPatch, ctx: MutationContext): PreviewResult<CalendarEvent> {
    const beforeRow = readRawOne(this.db, "events", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Event ${id} not found.`, "Refresh the calendar — it may have been deleted already.");
    }
    const result = this.updateEvent(id, patch, { ...ctx, dryRun: true });
    return { committed: false, before: [beforeRow], after: [result], result };
  }

  deleteEvent(id: string, ctx: MutationContext): { id: string } {    const existing = readRawOne(this.db, "events", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Event ${id} not found.`, "Refresh the calendar — it may already be deleted.");
    }
    if (ctx.dryRun) {
      return { id };
    }
    this.db.prepare(`DELETE FROM events WHERE id = ?`).run(id);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "deleteEvent",
      targetType: "event",
      targetIds: [id],
      beforeState: [existing],
      afterState: [null],
      groupId: ctx.groupId,
    });
    return { id };
  }

  previewDelete(id: string, ctx: MutationContext): PreviewResult<{ id: string }> {
    const beforeRow = readRawOne(this.db, "events", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Event ${id} not found.`, "Refresh the calendar — it may already be deleted.");
    }
    return { committed: false, before: [beforeRow], after: [null], result: { id } };
  }

  /** Overlapping pairs within a range (docs/07 FR-2.3). Read-only. */
  detectConflicts(range: EventRange): Array<{ a: CalendarEvent; b: CalendarEvent }> {
    const events = this.listEvents(range);
    const conflicts: Array<{ a: CalendarEvent; b: CalendarEvent }> = [];
    for (let i = 0; i < events.length; i++) {
      const a = events[i];
      if (!a) continue;
      for (let j = i + 1; j < events.length; j++) {
        const b = events[j];
        if (!b) continue;
        if (overlaps(a.startAt, a.endAt, b.startAt, b.endAt)) {
          conflicts.push({ a, b });
        }
      }
    }
    return conflicts;
  }

  /** Free slots of at least durationMinutes within [from, to] (docs/07 FR-2.4). */
  findFreeTime(range: EventRange, durationMinutes: number): FreeSlot[] {
    if (!Number.isFinite(durationMinutes) || durationMinutes <= 0) {
      throw new FlowOSError(
        "VALIDATION",
        "Duration must be a positive number of minutes.",
        "Pass a duration like 30 or 60 and try again.",
      );
    }
    const fromMs = Date.parse(range.from);
    const toMs = Date.parse(range.to);
    if (Number.isNaN(fromMs) || Number.isNaN(toMs) || fromMs >= toMs) {
      throw new FlowOSError(
        "VALIDATION",
        "Free-time range is invalid (need from < to).",
        "Pass an ISO-8601 range with from earlier than to.",
      );
    }
    const busy = this.listEvents(range)
      .map((e) => ({ start: Math.max(Date.parse(e.startAt), fromMs), end: Math.min(Date.parse(e.endAt), toMs) }))
      .filter((b) => b.end > b.start)
      .sort((x, y) => x.start - y.start);
    const merged: Array<{ start: number; end: number }> = [];
    for (const slot of busy) {
      const last = merged[merged.length - 1];
      if (last && slot.start <= last.end) {
        last.end = Math.max(last.end, slot.end);
      } else {
        merged.push({ ...slot });
      }
    }
    const minMs = durationMinutes * 60_000;
    const free: FreeSlot[] = [];
    let cursor = fromMs;
    for (const block of merged) {
      if (block.start - cursor >= minMs) {
        free.push({ startAt: new Date(cursor).toISOString(), endAt: new Date(block.start).toISOString() });
      }
      cursor = Math.max(cursor, block.end);
    }
    if (toMs - cursor >= minMs) {
      free.push({ startAt: new Date(cursor).toISOString(), endAt: new Date(toMs).toISOString() });
    }
    return free;
  }

  private checkedRange(startRaw: string, endRaw: string): { startAt: string; endAt: string } {
    const startAt = parseIso(startRaw);
    const endAt = parseIso(endRaw);
    if (!startAt || !endAt) {
      throw new FlowOSError(
        "VALIDATION",
        "Event needs a valid start and end date/time.",
        "Use ISO-8601 date/times (e.g. 2026-09-13T19:00:00) and try again.",
      );
    }
    if (startAt >= endAt) {
      throw new FlowOSError("VALIDATION", "Event end must be after its start.", "Swap or adjust the times and try again.");
    }
    return { startAt, endAt };
  }

  private requireTask(taskId: string): void {
    const row = readRawOne(this.db, "tasks", taskId);
    if (!row) {
      throw new FlowOSError(
        "VALIDATION",
        `Linked task ${taskId} does not exist — the link was not created.`,
        "Link to an existing task, or create the task first.",
      );
    }
  }
}
