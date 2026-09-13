// Thread persistence (docs/09 §8): conversation state lives in the backend.
// Every runTurn appends one TurnRecord (input, plan steps, tool calls and
// their results, summary, outcome) keyed by sessionId. The frontend renders
// history; it never owns it. Stored as JSON in agent_sessions.messages.
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { Surface } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { nowIso } from "../security/secrets.js";
import type { Proposal } from "../tools/executor.js";
import type { ToolName } from "../tools/definitions.js";

export interface TurnStepRecord {
  tool: ToolName;
  kind: string;
  tier: string;
  affectedIds: string[];
  /** Compact result (ids/titles/counts) — full snapshots stay in action_log. */
  resultSummary: unknown;
  /** Audit identity of the committed ops (toast Undo / history links target these). */
  logEntryIds: string[];
}

export interface TurnRecord {
  timestamp: string;
  instruction: string;
  surface: Surface;
  steps: TurnStepRecord[];
  status: string;
  summary: string;
  groupId: string | null;
  errorCode: string | null;
  preferencesUsedIds: string[];
  /** Full proposals for re-rendering historical diff cards read-only. */
  proposals: Proposal[] | null;
}

export interface SessionInfo {
  id: string;
  surface: Surface;
  startedAt: string;
  turnCount: number;
}

type Row = Record<string, unknown>;

export class SessionService {
  constructor(
    private readonly db: FlowDatabase,
    private readonly onCorrupt?: (detail: { sessionId: string | null; where: string }) => void,
  ) {}

  /** Get history, creating the session row on first use. */
  ensureSession(id: string | undefined, surface: Surface, workspaceId: string = DEFAULT_WORKSPACE_ID): { id: string; history: TurnRecord[] } {
    const sessionId = id ?? randomUUID();
    const existing = this.db
      .prepare(`SELECT * FROM agent_sessions WHERE id = ?`)
      .get(sessionId) as Row | undefined;
    if (existing) {
      return { id: sessionId, history: this.parseTurns(existing["messages"] as string, sessionId, "ensure") };
    }
    this.db
      .prepare(
        `INSERT INTO agent_sessions (id, workspace_id, surface, started_at, messages)
         VALUES (?, ?, ?, ?, ?)`,
      )
      .run(sessionId, workspaceId, surface, nowIso(), "[]");
    return { id: sessionId, history: [] };
  }

  appendTurn(id: string, turn: TurnRecord): TurnRecord[] {
    const existing = this.db.prepare(`SELECT * FROM agent_sessions WHERE id = ?`).get(id) as Row | undefined;
    const history = existing ? this.parseTurns(existing["messages"] as string, id, "append") : [];
    history.push(turn);
    if (existing) {
      this.db.prepare(`UPDATE agent_sessions SET messages = ? WHERE id = ?`).run(JSON.stringify(history), id);
    }
    return history;
  }

  getHistory(id: string): TurnRecord[] | null {
    const existing = this.db.prepare(`SELECT * FROM agent_sessions WHERE id = ?`).get(id) as Row | undefined;
    return existing ? this.parseTurns(existing["messages"] as string, id, "read") : null;
  }

  listSessions(workspaceId: string = DEFAULT_WORKSPACE_ID): SessionInfo[] {
    const rows = this.db
      .prepare(`SELECT id, surface, started_at, messages FROM agent_sessions WHERE workspace_id = ? ORDER BY started_at`)
      .all(workspaceId) as Row[];
    return rows.map((r) => ({
      id: r["id"] as string,
      surface: r["surface"] as Surface,
      startedAt: r["started_at"] as string,
      turnCount: this.parseTurns(r["messages"] as string, r["id"] as string, "list").length,
    }));
  }

  /**
   * Corrupt session JSON degrades LOUDLY: the caller is notified (default:
   * console.warn) with the session id and operation, and an empty history is
   * returned so one bad row can never break the thread view. The corrupt row
   * itself is left untouched for forensics (never silently overwritten).
   */
  private parseTurns(raw: string, sessionId: string | null, where: string): TurnRecord[] {
    try {
      const parsed = JSON.parse(raw) as unknown;
      if (!Array.isArray(parsed)) {
        this.flagCorrupt(sessionId, where, "messages JSON is not an array");
        return [];
      }
      return parsed as TurnRecord[];
    } catch (err) {
      this.flagCorrupt(sessionId, where, err instanceof Error ? err.message : String(err));
      return [];
    }
  }

  private flagCorrupt(sessionId: string | null, where: string, cause: string): void {
    const warn = this.onCorrupt ?? ((detail: { sessionId: string | null; where: string }) => console.warn(
      `[FlowOS] corrupt session history (session=${detail.sessionId ?? "n/a"}, op=${detail.where}): ${cause}. Returning empty history; row left untouched.`,
    ));
    warn({ sessionId, where });
  }
}
