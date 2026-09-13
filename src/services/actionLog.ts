// Action log + undo (docs/12-audit-undo-rollback-strategy.md).
// Undo is a property of the data layer: every mutation — manual or agent —
// passes through the services and lands here with before/after snapshots.
// Strategy: snapshot restore for all tools (uniform, auditable). Grouped
// cascades share a groupId and undo as one unit (verdict session 4:
// whole-group default, expand-to-inspect via listByGroup).
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { ActionLogEntry, Actor, UndoStatus } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";

export interface RecordActionInput {
  actor: Actor;
  sessionId?: string | undefined;
  toolOrOperation: string;
  targetType: string;
  targetIds: string[];
  beforeState: unknown;
  afterState: unknown;
  groupId?: string | undefined;
  workspaceId?: string | undefined;
}

function rowToEntry(row: Record<string, unknown>): ActionLogEntry {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    timestamp: row["timestamp"] as string,
    actor: row["actor"] as Actor,
    sessionId: (row["session_id"] as string | null) ?? null,
    toolOrOperation: row["tool_or_operation"] as string,
    targetType: row["target_type"] as string,
    targetIds: JSON.parse((row["target_ids"] as string) || "[]") as string[],
    beforeState: row["before_state"] ? (JSON.parse(row["before_state"] as string) as unknown) : null,
    afterState: row["after_state"] ? (JSON.parse(row["after_state"] as string) as unknown) : null,
    groupId: (row["group_id"] as string | null) ?? null,
    undoStatus: row["undo_status"] as UndoStatus,
    undoReason: (row["undo_reason"] as string | null) ?? null,
  };
}

export class ActionLogService {
  constructor(private readonly db: FlowDatabase) {}

  record(input: RecordActionInput): ActionLogEntry {
    const entry: ActionLogEntry = {
      id: randomUUID(),
      workspaceId: input.workspaceId ?? DEFAULT_WORKSPACE_ID,
      timestamp: nowIso(),
      actor: input.actor,
      sessionId: input.sessionId ?? null,
      toolOrOperation: input.toolOrOperation,
      targetType: input.targetType,
      targetIds: input.targetIds,
      beforeState: input.beforeState ?? null,
      afterState: input.afterState ?? null,
      groupId: input.groupId ?? null,
      undoStatus: "available",
      undoReason: null,
    };
    this.db
      .prepare(
        `INSERT INTO action_log
         (id, workspace_id, timestamp, actor, session_id, tool_or_operation,
          target_type, target_ids, before_state, after_state, group_id,
          undo_status, undo_reason)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        entry.id,
        entry.workspaceId,
        entry.timestamp,
        entry.actor,
        entry.sessionId,
        entry.toolOrOperation,
        entry.targetType,
        JSON.stringify(entry.targetIds),
        entry.beforeState === null ? null : JSON.stringify(entry.beforeState),
        entry.afterState === null ? null : JSON.stringify(entry.afterState),
        entry.groupId,
        entry.undoStatus,
        entry.undoReason,
      );
    return entry;
  }

  getById(id: string): ActionLogEntry | null {
    const row = this.db.prepare(`SELECT * FROM action_log WHERE id = ?`).get(id) as
      | Record<string, unknown>
      | undefined;
    return row ? rowToEntry(row) : null;
  }

  listRecent(limit = 50, workspaceId: string = DEFAULT_WORKSPACE_ID): ActionLogEntry[] {
    const rows = this.db
      .prepare(`SELECT * FROM action_log WHERE workspace_id = ? ORDER BY timestamp DESC LIMIT ?`)
      .all(workspaceId, limit) as Record<string, unknown>[];
    return rows.map(rowToEntry);
  }

  listByGroup(groupId: string): ActionLogEntry[] {
    const rows = this.db
      .prepare(`SELECT * FROM action_log WHERE group_id = ? ORDER BY timestamp ASC`)
      .all(groupId) as Record<string, unknown>[];
    return rows.map(rowToEntry);
  }

  /**
   * Latest mutation row for a session+operation (rowid order — immune to
   * same-millisecond timestamp ties). Used by the executor to attach the
   * audit identity of each committed op to step records, so UI affordances
   * (toast Undo) target the exact rows. Single-user local: no race.
   */
  findLatest(sessionId: string, toolOrOperation: string): ActionLogEntry | null {
    const row = this.db
      .prepare(
        `SELECT * FROM action_log
         WHERE session_id = ? AND tool_or_operation = ?
         ORDER BY rowid DESC LIMIT 1`,
      )
      .get(sessionId, toolOrOperation) as Record<string, unknown> | undefined;
    return row ? rowToEntry(row) : null;
  }

  markStatus(id: string, status: UndoStatus, reason: string | null): void {
    const res = this.db
      .prepare(`UPDATE action_log SET undo_status = ?, undo_reason = ? WHERE id = ?`)
      .run(status, reason, id);
    if (res.changes === 0) {
      throw new FlowOSError("NOT_FOUND", `Action log entry ${id} not found.`, "Refresh the Action History view and try again.");
    }
  }
}
