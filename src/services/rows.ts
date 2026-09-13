// Shared row helpers: raw snapshot reads (aligned to id order) plus
// snake_case <-> camelCase mapping. Services record snapshots as raw rows
// so the undo engine compares identical shapes (docs/12 §2).
import type { FlowDatabase } from "../db/database.js";

export function readRawAligned(db: FlowDatabase, table: string, ids: string[]): unknown[] {
  if (ids.length === 0) return [];
  const placeholders = ids.map(() => "?").join(",");
  const rows = db.prepare(`SELECT * FROM ${table} WHERE id IN (${placeholders})`).all(...ids) as Array<
    Record<string, unknown>
  >;
  const byId = new Map(rows.map((r) => [r["id"] as string, r]));
  return ids.map((id) => (byId.has(id) ? byId.get(id) : null));
}

export function readRawOne(db: FlowDatabase, table: string, id: string): Record<string, unknown> | null {
  const row = db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(id) as Record<string, unknown> | undefined;
  return row ?? null;
}

export function parseIso(value: string | null | undefined): string | null {
  if (value === null || value === undefined || value === "") return null;
  const ms = Date.parse(value);
  if (Number.isNaN(ms)) return null;
  return new Date(ms).toISOString();
}
