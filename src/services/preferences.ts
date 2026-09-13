// PreferenceService — explicit, user-visible memory facts only
// (docs/01 §5, docs/09 §7). Writes go through an explicit path and always
// appear distinctly in the audit trail; there is no silent side-channel.
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { MutationContext, Preference, PreviewResult } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";
import { readRawAligned, readRawOne } from "./rows.js";

type Row = Record<string, unknown>;

function toPreference(row: Row): Preference {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    statement: row["statement"] as string,
    sourceSessionId: (row["source_session_id"] as string | null) ?? null,
    createdAt: row["created_at"] as string,
    lastUsedAt: (row["last_used_at"] as string | null) ?? null,
  };
}

export class PreferenceService {
  private readonly log: ActionLogService;

  constructor(private readonly db: FlowDatabase) {
    this.log = new ActionLogService(db);
  }

  listPreferences(workspaceId: string = DEFAULT_WORKSPACE_ID): Preference[] {
    const rows = this.db
      .prepare(`SELECT * FROM preferences WHERE workspace_id = ? ORDER BY created_at`)
      .all(workspaceId) as Row[];
    return rows.map(toPreference);
  }

  getPreference(id: string): Preference | null {
    const row = readRawOne(this.db, "preferences", id);
    return row ? toPreference(row) : null;
  }

  saveLearnedPreference(statement: string, ctx: MutationContext): Preference {
    const clean = statement.trim();
    if (!clean) {
      throw new FlowOSError("VALIDATION", "Preference statement cannot be empty.", "State the preference in one sentence and try again.");
    }
    const pref: Preference = {
      id: randomUUID(),
      workspaceId: DEFAULT_WORKSPACE_ID,
      statement: clean,
      sourceSessionId: ctx.sessionId ?? null,
      createdAt: nowIso(),
      lastUsedAt: null,
    };
    if (ctx.dryRun) {
      return pref;
    }
    this.db
      .prepare(
        `INSERT INTO preferences (id, workspace_id, statement, source_session_id, created_at, last_used_at)
         VALUES (?, ?, ?, ?, ?, ?)`,
      )
      .run(pref.id, pref.workspaceId, pref.statement, pref.sourceSessionId, pref.createdAt, pref.lastUsedAt);
    const after = readRawAligned(this.db, "preferences", [pref.id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "saveLearnedPreference",
      targetType: "preference",
      targetIds: [pref.id],
      beforeState: [null],
      afterState: after,
      groupId: ctx.groupId,
    });
    return pref;
  }

  previewSaveLearnedPreference(statement: string, ctx: MutationContext): PreviewResult<Preference> {
    const result = this.saveLearnedPreference(statement, { ...ctx, dryRun: true });
    return { committed: false, before: [null], after: [result], result };
  }

  previewDeleteLearnedPreference(id: string, ctx: MutationContext): PreviewResult<{ id: string }> {
    const beforeRow = readRawOne(this.db, "preferences", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Preference ${id} not found.`, "Refresh the preferences list and try again.");
    }
    return { committed: false, before: [beforeRow], after: [null], result: { id } };
  }

  touchPreference(id: string): void {
    this.db.prepare(`UPDATE preferences SET last_used_at = ? WHERE id = ?`).run(nowIso(), id);
  }

  deleteLearnedPreference(id: string, ctx: MutationContext): { id: string } {
    const existing = readRawOne(this.db, "preferences", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Preference ${id} not found.`, "Refresh the preferences list and try again.");
    }
    if (ctx.dryRun) {
      return { id };
    }
    this.db.prepare(`DELETE FROM preferences WHERE id = ?`).run(id);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "deleteLearnedPreference",
      targetType: "preference",
      targetIds: [id],
      beforeState: [existing],
      afterState: [null],
      groupId: ctx.groupId,
    });
    return { id };
  }

  updatePreference(id: string, statement: string, ctx: MutationContext): Preference {
    const clean = statement.trim();
    if (!clean) {
      throw new FlowOSError("VALIDATION", "Preference statement cannot be empty.", "State the preference in one sentence and try again.");
    }
    const existing = readRawOne(this.db, "preferences", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Preference ${id} not found.`, "Refresh the preferences list and try again.");
    }
    if (ctx.dryRun) {
      return { ...toPreference(existing), statement: clean };
    }
    this.db.prepare(`UPDATE preferences SET statement = ? WHERE id = ?`).run(clean, id);
    const updated = readRawOne(this.db, "preferences", id) as Row;
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "updatePreference",
      targetType: "preference",
      targetIds: [id],
      beforeState: [existing],
      afterState: [updated],
      groupId: ctx.groupId,
    });
    return toPreference(updated);
  }
}
