// Tool executor tests: propose/execute separation, approval binding,
// blocked/cancelled zero-row contract, composite grouping.
import { beforeEach, describe, expect, it } from "vitest";
import { closeDatabase, openDatabase, type FlowDatabase } from "../src/db/database.js";
import { FlowOSError } from "../src/security/secrets.js";
import { ActionLogService } from "../src/services/actionLog.js";
import { CalendarService } from "../src/services/calendar.js";
import { NoteService } from "../src/services/notes.js";
import { PreferenceService } from "../src/services/preferences.js";
import { TaskService } from "../src/services/tasks.js";
import { UndoService } from "../src/services/undo.js";
import { ToolExecutor, type Approval } from "../src/tools/executor.js";

let db: FlowDatabase;
let log: ActionLogService;
let exec: ToolExecutor;

const CTX = { sessionId: "sess-test" };

beforeEach(() => {
  if (db) {
    try {
      closeDatabase(db);
    } catch {
      // first run
    }
  }
  db = openDatabase(":memory:");
  const tasks = new TaskService(db);
  log = new ActionLogService(db);
  exec = new ToolExecutor({
    tasks,
    calendar: new CalendarService(db),
    notes: new NoteService(db),
    prefs: new PreferenceService(db),
    log,
  });
});

function approveAs(p: { inputFingerprint: string; groupId: string | null; affectedIds: string[] }): Approval {
  return { tier: "confirm", inputFingerprint: p.inputFingerprint, groupId: p.groupId ?? "unused", affectedIds: p.affectedIds };
}

describe("propose/execute separation", () => {
  it("auto-tier executes immediately and logs exactly one row", () => {
    const before = log.listRecent(1000).length;
    const proposal = exec.propose("createTask", { title: "auto-created" }, CTX);
    expect(proposal.kind).toBe("ready");
    expect(proposal.tier).toBe("auto");
    expect(log.listRecent(1000).length).toBe(before); // propose wrote nothing
    const res = exec.executeApproved("createTask", { title: "auto-created" }, CTX);
    expect(res.executed).toBe(true);
    expect(log.listRecent(1000).length).toBe(before + 1);
  });

  it("confirm-tier refuses to execute without approval and writes nothing", () => {
    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const before = log.listRecent(1000).length;
    const proposal = exec.propose("updateTask", { ids: [a.id, b.id], patch: { status: "done" } }, CTX);
    expect(proposal.kind).toBe("confirm");
    expect(() => exec.executeApproved("updateTask", { ids: [a.id, b.id], patch: { status: "done" } }, CTX)).toThrowError(
      FlowOSError,
    );
    expect(log.listRecent(1000).length).toBe(before);
    expect(tasks.getTask(a.id)?.status).toBe("todo");
  });

  it("approved confirm plan executes once under its group id", () => {
    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const input = { ids: [a.id, b.id], patch: { status: "done" } };
    const proposal = exec.propose("updateTask", input, CTX);
    expect(proposal.kind).toBe("confirm");
    expect(proposal.groupId).toBeTruthy();
    const res = exec.executeApproved("updateTask", input, CTX, approveAs(proposal));
    expect(res.executed).toBe(true);
    expect(res.groupId).toBe(proposal.groupId);
    expect(tasks.getTask(a.id)?.status).toBe("done");
    expect(tasks.getTask(b.id)?.status).toBe("done");
    // Both entries share the group -> undo as one unit.
    const undo = new UndoService(db);
    const groupEntries = log.listByGroup(proposal.groupId as string);
    expect(groupEntries).toHaveLength(2);
    undo.undoEntry(groupEntries[0]!.id, "manual");
    expect(tasks.getTask(a.id)?.status).toBe("todo");
    expect(tasks.getTask(b.id)?.status).toBe("todo");
  });

  it("approval for edited input is rejected (fingerprint mismatch)", () => {
    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const proposal = exec.propose("updateTask", { ids: [a.id, b.id], patch: { status: "done" } }, CTX);
    const edited = { ids: [a.id, b.id], patch: { status: "doing" } };
    expect(() => exec.executeApproved("updateTask", edited, CTX, approveAs(proposal))).toThrowError(FlowOSError);
    expect(tasks.getTask(a.id)?.status).toBe("todo");
  });

  it("approval aborts when the id set drifted since proposal", () => {
    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "a", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "b", priority: "urgent" }, { actor: "manual" });
    const input = { filter: { priority: "urgent" }, patch: { status: "done" } };
    const proposal = exec.propose("updateTask", input, CTX);
    expect(proposal.kind).toBe("confirm");
    // Workspace changes between proposal and approval...
    tasks.createTask({ title: "c", priority: "urgent" }, { actor: "manual" });
    expect(() =>
      exec.executeApproved("updateTask", input, CTX, approveAs(proposal)),
    ).toThrowError(/no longer matches/);
    expect(tasks.listTasks({ status: "done" })).toHaveLength(0);
  });
});

describe("audit identity threading (toast Undo targets)", () => {
  it("executed results carry the exact log entry ids of their rows", () => {
    const res = exec.executeApproved("createTask", { title: "tracked" }, CTX);
    expect(res.logEntryIds).toHaveLength(1);
    const row = log.getById(res.logEntryIds[0] as string);
    expect(row?.toolOrOperation).toBe("createTask");
    expect(row?.targetIds).toHaveLength(1);

    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const input = { ids: [a.id, b.id], patch: { status: "done" } };
    const proposal = exec.propose("updateTask", input, CTX);
    const approved = exec.executeApproved("updateTask", input, CTX, approveAs(proposal));
    expect(approved.logEntryIds).toHaveLength(2);
    for (const id of approved.logEntryIds) {
      expect(log.getById(id)?.toolOrOperation).toBe("updateTask");
    }
    // And the ids are undo-capable entry points for toast Undo: undoing
    // one group member reverts the whole group.
    const undo = new UndoService(db);
    undo.undoEntry(approved.logEntryIds[0] as string, "manual");
    expect(tasks.getTask(a.id)?.status).toBe("todo");
    expect(tasks.getTask(b.id)?.status).toBe("todo");
  });
});

describe("blocked contract: zero mutation rows", () => {
  it("filter-based bulk delete is blocked with an itemized alternative and writes nothing", () => {
    const tasks = new TaskService(db);
    tasks.createTask({ title: "u1", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "u2", priority: "urgent" }, { actor: "manual" });
    const manualRows = log.listRecent(1000).length;
    const proposal = exec.propose("deleteTask", { filter: { priority: "urgent" } }, CTX);
    expect(proposal.kind).toBe("blocked");
    expect(proposal.tier).toBe("blocked");
    expect(proposal.itemizedAlternative?.kind).toBe("confirm");
    expect(proposal.itemizedAlternative?.affectedIds).toHaveLength(2);
    expect(proposal.itemizedAlternative?.preview).toHaveLength(2);
    // Blocked plan: zero NEW mutation rows (only the 2 manual creates exist).
    expect(log.listRecent(1000).length).toBe(manualRows);
    // And there is no execution path: executeApproved throws.
    expect(() => exec.executeApproved("deleteTask", { filter: { priority: "urgent" } }, CTX)).toThrowError(/blocked/i);
    expect(log.listRecent(1000).length).toBe(manualRows);
    // The itemized alternative IS executable through the normal approval flow.
    const itemized = proposal.itemizedAlternative!;
    const res = exec.executeApproved(
      "deleteTask",
      { ids: itemized.affectedIds },
      CTX,
      { tier: "confirm", inputFingerprint: itemized.inputFingerprint, groupId: itemized.groupId ?? "g", affectedIds: itemized.affectedIds },
    );
    expect(res.executed).toBe(true);
    expect(tasks.listTasks()).toHaveLength(0);
  });

  it("itemized alternative carries an executable input that reproduces it verbatim", () => {
    const tasks = new TaskService(db);
    tasks.createTask({ title: "k1", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "k2", priority: "urgent" }, { actor: "manual" });
    const blocked = exec.propose("deleteTask", { filter: { priority: "urgent" } }, CTX);
    const itemized = blocked.itemizedAlternative!;
    expect(itemized.executableInput).toEqual({ ids: itemized.affectedIds });
    // Submitting the echoed input reproduces the same confirm plan — the UI
    // never reconstructs tool inputs from affected ids itself.
    const reproposed = exec.propose("deleteTask", itemized.executableInput, CTX);
    expect(reproposed.kind).toBe("confirm");
    expect(reproposed.affectedIds).toEqual(itemized.affectedIds);
  });

  it("cancelled confirm plan (never approved) leaves zero rows and unchanged state", () => {
    const tasks = new TaskService(db);
    const a = tasks.createTask({ title: "keep me" }, { actor: "manual" });
    const before = log.listRecent(1000).length;
    const proposal = exec.propose("deleteTask", { ids: [a.id] }, CTX);
    expect(proposal.kind).toBe("confirm");
    // User hits Cancel: executeApproved is simply never called.
    expect(log.listRecent(1000).length).toBe(before);
    expect(tasks.getTask(a.id)).not.toBeNull();
  });
});

describe("filter resolution drives the tier (literal vs resolved)", () => {
  it("'all urgent tasks' with one match is auto; with three is confirm", () => {
    const tasks = new TaskService(db);
    const solo = tasks.createTask({ title: "solo", priority: "urgent" }, { actor: "manual" });
    const one = exec.propose("updateTask", { filter: { priority: "urgent" }, patch: { status: "doing" } }, CTX);
    expect(one.kind).toBe("ready");
    expect(one.tier).toBe("auto");
    expect(one.affectedIds).toEqual([solo.id]);

    tasks.createTask({ title: "second", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "third", priority: "urgent" }, { actor: "manual" });
    const many = exec.propose("updateTask", { filter: { priority: "urgent" }, patch: { status: "doing" } }, CTX);
    expect(many.kind).toBe("confirm");
    expect(many.tier).toBe("confirm");
    expect(many.affectedIds).toHaveLength(3);
  });

  it("filter matching nothing is an explicit empty outcome, not an error or a guess", () => {
    const proposal = exec.propose("updateTask", { filter: { priority: "urgent" }, patch: { status: "done" } }, CTX);
    expect(proposal.kind).toBe("empty");
    expect(proposal.affectedIds).toHaveLength(0);
    const res = exec.executeApproved("updateTask", { filter: { priority: "urgent" }, patch: { status: "done" } }, CTX);
    expect(res.executed).toBe(false);
  });

  it("malformed calls are rejected with a correctable error and zero rows", () => {
    const before = log.listRecent(1000).length;
    expect(() => exec.propose("updateTask", { patch: { status: "done" } }, CTX)).toThrowError(/missing target selector/);
    expect(() => exec.propose("updateTask", { ids: ["x"], patch: {} }, CTX)).toThrowError(/non-empty object/);
    expect(() => exec.propose("deleteTask", { filter: { priority: "eventually" } }, CTX)).toThrowError(FlowOSError);
    expect(log.listRecent(1000).length).toBe(before);
  });

  it("update for a nonexistent id fails gracefully (no hallucinated writes)", () => {
    const before = log.listRecent(1000).length;
    expect(() => exec.propose("updateTask", { ids: ["ghost-id"], patch: { status: "done" } }, CTX)).toThrowError(/not found/);
    expect(log.listRecent(1000).length).toBe(before);
  });
});

describe("composites", () => {
  it("reschedule cascades to flexible conflicts as one grouped unit", () => {
    const cal = new CalendarService(db);
    const day = "2026-09-17";
    const maths = cal.createEvent({ title: "Maths", startAt: `${day}T17:00:00`, endAt: `${day}T18:00:00` }, { actor: "manual" });
    const reading = cal.createEvent(
      { title: "Reading", startAt: `${day}T19:00:00`, endAt: `${day}T19:30:00`, isFlexible: true },
      { actor: "manual" },
    );
    const input = { eventId: maths.id, newStartAt: `${day}T19:00:00`, newEndAt: `${day}T20:00:00` };
    const proposal = exec.propose("reschedule", input, CTX);
    expect(proposal.kind).toBe("confirm");
    expect(proposal.affectedIds).toEqual(expect.arrayContaining([maths.id, reading.id]));
    const before = log.listRecent(1000).length;
    const res = exec.executeApproved("reschedule", input, CTX, approveAs(proposal));
    expect(res.executed).toBe(true);
    expect(Date.parse(cal.getEvent(maths.id)!.startAt)).toBe(Date.parse(`${day}T19:00:00`));
    expect(Date.parse(cal.getEvent(reading.id)!.startAt)).toBe(Date.parse(`${day}T20:00:00`));
    expect(log.listRecent(1000).length).toBe(before + 2);
    // Grouped: single undo restores both.
    const undo = new UndoService(db);
    const group = log.listByGroup(res.groupId as string);
    expect(group).toHaveLength(2);
    undo.undoEntry(group[0]!.id, "manual");
    expect(Date.parse(cal.getEvent(maths.id)!.startAt)).toBe(Date.parse(`${day}T17:00:00`));
    expect(Date.parse(cal.getEvent(reading.id)!.startAt)).toBe(Date.parse(`${day}T19:00:00`));
  });

  it("reschedule warns about fixed conflicts without moving them", () => {
    const cal = new CalendarService(db);
    const day = "2026-09-18";
    const a = cal.createEvent({ title: "A", startAt: `${day}T17:00:00`, endAt: `${day}T18:00:00` }, { actor: "manual" });
    cal.createEvent({ title: "Fixed", startAt: `${day}T19:00:00`, endAt: `${day}T20:00:00` }, { actor: "manual" });
    const proposal = exec.propose("reschedule", { eventId: a.id, newStartAt: `${day}T19:00:00`, newEndAt: `${day}T20:00:00` }, CTX);
    expect(proposal.kind).toBe("confirm");
    expect(proposal.affectedIds).toEqual([a.id]);
    expect(proposal.message).toMatch(/NOT moved/);
  });
});
