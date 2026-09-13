// Snapshot-restore undo engine (docs/12 §3).
// Every mutating service records before/after row snapshots; undo restores
// the before image. An entry becomes no-longer-undoable when current state
// no longer matches the recorded after image (docs/12 §5) — explained, never
// silent. Grouped entries undo together in reverse-chronological order.
import type { FlowDatabase } from "../db/database.js";
import type { Actor } from "../model/types.js";
import { FlowOSError } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";

type RowTable = "tasks" | "events" | "notes" | "preferences";

const TARGET_TABLES: Record<string, RowTable> = {
  task: "tasks",
  event: "events",
  note: "notes",
  preference: "preferences",
};

export interface UndoOutcome {
  undoneEntryIds: string[];
  groupId: string | null;
}

export class UndoService {
  private readonly log: ActionLogService;

  constructor(private readonly db: FlowDatabase) {
    this.log = new ActionLogService(db);
  }

  undoEntry(entryId: string, actor: Actor, sessionId?: string): UndoOutcome {
    const entry = this.log.getById(entryId);
    if (!entry) {
      throw new FlowOSError("NOT_FOUND", `Action ${entryId} not found in history.`, "Refresh the Action History view and try again.");
    }
    const targets = entry.groupId ? this.log.listByGroup(entry.groupId) : [entry];
    const ordered = [...targets].reverse();
    // Phase 1 (autocommit reads): validate every member BEFORE touching any
    // state, so a group with one invalid member fails atomically. Invalid
    // members are marked outside any data transaction — marking inside the
    // restore transaction would roll the mark back with the throw.
    for (const target of ordered) {
      const problem = this.validityProblem(target.id);
      if (problem) {
        this.log.markStatus(target.id, "no-longer-undoable", problem);
        throw new FlowOSError("UNDO_UNAVAILABLE", `Undo unavailable: ${problem}`, "Review the current state of the items and edit them manually if needed.");
      }
    }
    // Phase 2: all members valid — restore as one atomic unit.
    const restore = this.db.transaction(() => {
      for (const target of ordered) this.applyRestore(target.id);
    });
    try {
      restore();
    } catch (err) {
      if (err instanceof FlowOSError) throw err;
      throw new FlowOSError(
        "STORE",
        "Undo failed partway — no changes were applied.",
        "Retry the undo. If it keeps failing, inspect the Action History entry for details.",
        err instanceof Error ? err.message : String(err),
      );
    }
    const undoneIds = ordered.map((e) => e.id);
    // The undo itself is a logged action (audit trail never rewrites history).
    this.log.record({
      actor,
      sessionId,
      toolOrOperation: "undo",
      targetType: "action",
      targetIds: undoneIds,
      beforeState: { undoneEntryIds: undoneIds },
      afterState: { undoneEntryIds: undoneIds, status: "undone" },
      groupId: entry.groupId ?? undefined,
    });
    return { undoneEntryIds: undoneIds, groupId: entry.groupId };
  }

  /** Read-only validity probe. Returns a human reason when undo is not
   *  possible, or null when the entry can be restored (docs/12 §5). */
  private validityProblem(entryId: string): string | null {
    const entry = this.log.getById(entryId);
    if (!entry) {
      return `Action ${entryId} is no longer in history.`;
    }
    if (entry.toolOrOperation === "undo") {
      return "Undo actions themselves cannot be undone (this would rewrite the audit trail). Re-apply the original change manually if you need it back.";
    }
    if (entry.undoStatus === "undone") {
      return "This action was already undone.";
    }
    if (entry.undoStatus === "no-longer-undoable") {
      return entry.undoReason ?? "This action can no longer be undone.";
    }
    const table = TARGET_TABLES[entry.targetType];
    if (!table) {
      return `Actions of type "${entry.targetType}" have no restore path. Adjust the affected items manually.`;
    }
    const current = this.readRowsAligned(table, entry.targetIds);
    if (!statesEqual(current, entry.afterState)) {
      return "A later change modified the same items, so the recorded snapshot no longer applies.";
    }
    return null;
  }

  private applyRestore(entryId: string): void {
    const entry = this.log.getById(entryId);
    if (!entry) {
      throw new FlowOSError("NOT_FOUND", `Action ${entryId} not found in history.`, "Refresh the Action History view and try again.");
    }
    const table = TARGET_TABLES[entry.targetType];
    if (!table) {
      throw new FlowOSError(
        "UNDO_UNAVAILABLE",
        `Actions of type "${entry.targetType}" have no restore path.`,
        "Adjust the affected items manually.",
      );
    }
    // Re-verify inside the transaction (defense in depth — phase 1 already
    // passed, so a mismatch here means concurrent modification mid-undo).
    const current = this.readRowsAligned(table, entry.targetIds);
    if (!statesEqual(current, entry.afterState)) {
      throw new FlowOSError(
        "UNDO_UNAVAILABLE",
        "Undo unavailable: state changed during the undo itself.",
        "Refresh the Action History view and try again.",
      );
    }
    this.writeRows(table, entry.targetIds, entry.beforeState);
    this.log.markStatus(entry.id, "undone", null);
  }

  private readRows(table: RowTable, ids: string[]): unknown[] {
    if (ids.length === 0) return [];
    const placeholders = ids.map(() => "?").join(",");
    return this.db.prepare(`SELECT * FROM ${table} WHERE id IN (${placeholders}) ORDER BY id`).all(...ids);
  }

  /** Current rows aligned to ids order; ids with no row read as null,
   *  matching how services record before/after snapshots. */
  private readRowsAligned(table: RowTable, ids: string[]): unknown[] {
    const byId = new Map<string, unknown>();
    for (const row of this.readRows(table, ids)) {
      byId.set((row as Record<string, unknown>)["id"] as string, row);
    }
    return ids.map((id) => (byId.has(id) ? byId.get(id) : null));
  }

  /** Restore the before image: re-insert missing rows, update present ones,
   *  delete rows that did not exist before (were created by the action). */
  private writeRows(table: RowTable, ids: string[], beforeState: unknown): void {
    const before = (Array.isArray(beforeState) ? beforeState : []) as Array<Record<string, unknown> | null>;
    const beforeById = new Map<string, Record<string, unknown> | null>();
    ids.forEach((id, i) => beforeById.set(id, before[i] ?? null));
    for (const id of ids) {
      const snapshot = beforeById.get(id);
      const existing = this.db.prepare(`SELECT id FROM ${table} WHERE id = ?`).get(id);
      if (snapshot === null || snapshot === undefined) {
        if (existing) this.db.prepare(`DELETE FROM ${table} WHERE id = ?`).run(id);
        continue;
      }
      if (!existing) {
        const cols = Object.keys(snapshot);
        const placeholders = cols.map(() => "?").join(",");
        this.db
          .prepare(`INSERT INTO ${table} (${cols.join(",")}) VALUES (${placeholders})`)
          .run(...cols.map((c) => toDbValue(snapshot[c])));
        continue;
      }
      const cols = Object.keys(snapshot).filter((c) => c !== "id");
      const assignments = cols.map((c) => `${c} = ?`).join(",");
      this.db
        .prepare(`UPDATE ${table} SET ${assignments} WHERE id = ?`)
        .run(...cols.map((c) => toDbValue(snapshot[c])), id);
    }
  }
}

function toDbValue(value: unknown): unknown {
  if (value === undefined) return null;
  if (typeof value === "boolean") return value ? 1 : 0;
  if (typeof value === "object" && value !== null && !Array.isArray(value)) return JSON.stringify(value);
  return value;
}

/** Order-insensitive deep equality over JSON-compatible snapshots. */
export function statesEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(normalize(a)) === JSON.stringify(normalize(b));
}

function normalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return [...value].map(normalize).sort((x, y) => JSON.stringify(x).localeCompare(JSON.stringify(y)));
  }
  if (value !== null && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const key of Object.keys(value).sort()) {
      const v = (value as Record<string, unknown>)[key];
      // Row-reads return arrays/JSON strings where snapshots hold parsed
      // values — compare through a canonical string form.
      out[key] = typeof v === "string" && (v.startsWith("[") || v.startsWith("{")) ? tryParse(v) : normalize(v);
    }
    return out;
  }
  return value;
}

function tryParse(raw: string): unknown {
  try {
    return normalize(JSON.parse(raw) as unknown);
  } catch {
    return raw;
  }
}
