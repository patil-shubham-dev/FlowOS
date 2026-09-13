// Verifier tests (docs/09 §6, docs/20 "verification mismatch").
// The failure-mode tests sabotage committed state (raw SQL = a lying tool or
// a race) and assert the verifier reports failure with id-level detail —
// never a false success. Same standing as the undo-unavailable regression.
import { beforeEach, describe, expect, it } from "vitest";
import { Verifier } from "../src/agent/verify.js";
import { closeDatabase, openDatabase, type FlowDatabase } from "../src/db/database.js";
import { FlowOSError } from "../src/security/secrets.js";
import { ActionLogService } from "../src/services/actionLog.js";
import { CalendarService } from "../src/services/calendar.js";
import { NoteService } from "../src/services/notes.js";
import { PreferenceService } from "../src/services/preferences.js";
import { TaskService } from "../src/services/tasks.js";
import { ToolExecutor } from "../src/tools/executor.js";

let db: FlowDatabase;
let verifier: Verifier;
let exec: ToolExecutor;
let tasks: TaskService;
let calendar: CalendarService;

const CTX = { sessionId: "verify-test" };

beforeEach(() => {
  if (db) {
    try {
      closeDatabase(db);
    } catch {
      // first run
    }
  }
  db = openDatabase(":memory:");
  tasks = new TaskService(db);
  calendar = new CalendarService(db);
  const notes = new NoteService(db);
  const prefs = new PreferenceService(db);
  exec = new ToolExecutor({ tasks, calendar, notes, prefs, log: new ActionLogService(db) });
  verifier = new Verifier({ tasks, calendar, notes, prefs });
});

describe("verifier success paths", () => {
  it("confirms an update whose re-fetched state matches intent", () => {
    const t = tasks.createTask({ title: "v" }, { actor: "manual" });
    const proposal = exec.propose("updateTask", { ids: [t.id], patch: { status: "done" } }, CTX);
    exec.executeApproved("updateTask", { ids: [t.id], patch: { status: "done" } }, CTX);
    const res = verifier.verify(proposal, new Date().toISOString());
    expect(res.ok).toBe(true);
    expect(res.checked).toBe(1);
  });

  it("confirms a delete whose row is gone", () => {
    const t = tasks.createTask({ title: "doomed" }, { actor: "manual" });
    const proposal = exec.propose("deleteTask", { ids: [t.id] }, CTX);
    exec.executeApproved(
      "deleteTask",
      { ids: [t.id] },
      CTX,
      { tier: "confirm", inputFingerprint: proposal.inputFingerprint, groupId: proposal.groupId ?? "g", affectedIds: proposal.affectedIds },
    );
    expect(verifier.verify(proposal, new Date().toISOString()).ok).toBe(true);
  });
});

describe("verifier failure mode (sabotaged state)", () => {
  it("tool reports success but state diverged -> VERIFICATION_MISMATCH naming the id", () => {
    const t = tasks.createTask({ title: "v" }, { actor: "manual" });
    const input = { ids: [t.id], patch: { status: "done" } };
    const proposal = exec.propose("updateTask", input, CTX);
    exec.executeApproved("updateTask", input, CTX);
    // Sabotage: something rewrites the row after the tool "succeeded".
    db.prepare(`UPDATE tasks SET status = 'todo', title = 'tampered' WHERE id = ?`).run(t.id);
    let caught: unknown = null;
    try {
      verifier.verify(proposal, new Date().toISOString());
    } catch (err) {
      caught = err;
    }
    expect(caught).toBeInstanceOf(FlowOSError);
    expect((caught as FlowOSError).code).toBe("VERIFICATION_MISMATCH");
    expect((caught as FlowOSError).message).toContain(t.id);
  });

  it("delete that did not actually delete is reported, not confirmed", () => {
    const t = tasks.createTask({ title: "stubborn" }, { actor: "manual" });
    const input = { ids: [t.id] };
    const proposal = exec.propose("deleteTask", input, CTX);
    // Simulate a tool that claimed success without deleting: restore the row.
    exec.executeApproved(
      "deleteTask",
      input,
      CTX,
      { tier: "confirm", inputFingerprint: proposal.inputFingerprint, groupId: proposal.groupId ?? "g", affectedIds: proposal.affectedIds },
    );
    db.prepare(
      `INSERT INTO tasks (id, workspace_id, title, status, priority, created_at, updated_at, source)
       VALUES (?, 'default', 'stubborn', 'todo', 'normal', '2026-09-01T00:00:00.000Z', '2026-09-01T00:00:00.000Z', 'manual')`,
    ).run(t.id);
    expect(() => verifier.verify(proposal, new Date().toISOString())).toThrowError(/still exists/);
  });

  it("generateSchedule with a missing linked event fails verification", () => {
    const day = "2026-09-26";
    const t = tasks.createTask({ title: "study", estimatedMinutes: 30 }, { actor: "manual" });
    const input = { dateFrom: `${day}T00:00:00`, dateTo: `${day}T23:59:59`, taskIds: [t.id] };
    const proposal = exec.propose("generateSchedule", input, CTX);
    expect(proposal.kind).toBe("confirm");
    const executedAt = new Date().toISOString();
    const res = exec.executeApproved(
      "generateSchedule",
      input,
      CTX,
      { tier: "confirm", inputFingerprint: proposal.inputFingerprint, groupId: proposal.groupId ?? "g", affectedIds: proposal.affectedIds },
    );
    expect(verifier.verify(proposal, executedAt).ok).toBe(true);
    // Sabotage: wipe the created events afterwards.
    for (const created of res.result as Array<{ id: string }>) {
      db.prepare(`DELETE FROM events WHERE id = ?`).run(created.id);
    }
    expect(() => verifier.verify(proposal, new Date().toISOString())).toThrowError(/no scheduled event/);
  });
});
