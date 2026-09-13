// Golden scenario mechanics suite (docs/21-agent-evaluation-testing-strategy.md).
// This file IS the doc-21 §2 checklist, executed — not reinvented. Each test
// maps to a numbered scenario and asserts mechanics: tool sequence, tier,
// final state, safety. Plan/explanation QUALITY needs human review (docs/21
// §6) and live-model runs (docs/21 §5); those run outside this file.
import { beforeEach, describe, expect, it } from "vitest";
import { classifyPlan } from "../src/agent/risk.js";
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
let tasks: TaskService;
let calendar: CalendarService;
let notes: NoteService;

const CTX = { sessionId: "golden" };

function approve(p: { inputFingerprint: string; groupId: string | null; affectedIds: string[] }): Approval {
  return { tier: "confirm", inputFingerprint: p.inputFingerprint, groupId: p.groupId ?? "unused", affectedIds: p.affectedIds };
}

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
  notes = new NoteService(db);
  log = new ActionLogService(db);
  exec = new ToolExecutor({ tasks, calendar, notes, prefs: new PreferenceService(db), log });
});

describe("doc-21 golden scenarios (mechanics)", () => {
  it("1. cascading reschedule: plan, confirm tier, grouped final state", () => {
    const day = "2026-09-20";
    const maths = calendar.createEvent({ title: "Maths study", startAt: `${day}T17:00:00`, endAt: `${day}T18:00:00` }, { actor: "manual" });
    calendar.createEvent(
      { title: "Reading block", startAt: `${day}T19:00:00`, endAt: `${day}T19:30:00`, isFlexible: true },
      { actor: "manual" },
    );
    const input = { eventId: maths.id, newStartAt: `${day}T19:00:00`, newEndAt: `${day}T20:00:00` };
    const proposal = exec.propose("reschedule", input, CTX);
    expect(proposal.tier).toBe("confirm"); // risk classification correctness
    expect(proposal.affectedIds).toHaveLength(2); // cascading effect identified
    const res = exec.executeApproved("reschedule", input, CTX, approve(proposal));
    expect(res.executed).toBe(true);
    expect(log.listByGroup(res.groupId as string)).toHaveLength(2); // one grouped action
  });

  it("2. bulk triage: 'move all urgent tasks to tomorrow morning' is confirm-tier", () => {
    for (const title of ["u1", "u2", "u3"]) {
      tasks.createTask({ title, priority: "urgent" }, { actor: "manual" });
    }
    const input = { filter: { priority: "urgent" }, patch: { dueAt: "2026-09-21T09:00:00" } };
    const proposal = exec.propose("updateTask", input, CTX);
    expect(proposal.tier).toBe("confirm");
    expect(proposal.affectedIds).toHaveLength(3);
    exec.executeApproved("updateTask", input, CTX, approve(proposal));
    const expected = Date.parse("2026-09-21T09:00:00");
    expect(tasks.listTasks({ priority: "urgent" }).every((t) => t.dueAt !== null && Date.parse(t.dueAt) === expected)).toBe(true);
  });

  it("3. 'make tomorrow less busy' proposes a lighter schedule for review", () => {
    const day = "2026-09-21";
    calendar.createEvent({ title: "Gym", startAt: `${day}T18:00:00`, endAt: `${day}T19:00:00`, isFlexible: true }, { actor: "manual" });
    calendar.createEvent({ title: "Dentist", startAt: `${day}T10:00:00`, endAt: `${day}T11:00:00` }, { actor: "manual" });
    const input = { date: day };
    const proposal = exec.propose("optimizeSchedule", input, CTX);
    expect(proposal.tier).toBe("confirm");
    expect(proposal.affectedIds).toHaveLength(1); // only the flexible one moves
    const res = exec.executeApproved("optimizeSchedule", input, CTX, approve(proposal));
    expect(res.executed).toBe(true);
  });

  it("4. constraint rescheduling: fixed college block respected, flexible gym moved", () => {
    const day = "2026-09-22";
    calendar.createEvent({ title: "College", startAt: `${day}T09:00:00`, endAt: `${day}T16:00:00` }, { actor: "manual" });
    const gym = calendar.createEvent(
      { title: "Gym", startAt: `${day}T10:00:00`, endAt: `${day}T11:00:00`, isFlexible: true },
      { actor: "manual" },
    );
    const input = { eventId: gym.id, newStartAt: `${day}T17:00:00`, newEndAt: `${day}T18:00:00` };
    const proposal = exec.propose("reschedule", input, CTX);
    expect(proposal.tier).toBe("confirm");
    exec.executeApproved("reschedule", input, CTX, approve(proposal));
    expect(Date.parse(calendar.getEvent(gym.id)!.startAt)).toBe(Date.parse(`${day}T17:00:00`));
    expect(Date.parse(calendar.getEvent(gym.id)!.endAt)).toBe(Date.parse(`${day}T18:00:00`));
  });

  it("5. cross-module synthesis: search reads, then bulk creation reviews as one plan", () => {
    notes.createNote({ title: "TTS plan", content: "TTS project: prototype, dataset, eval" }, { actor: "manual" });
    notes.createNote({ title: "TTS papers", content: "TTS project voice cloning notes" }, { actor: "manual" });
    const found = exec.propose("globalSearch", { query: "TTS project" }, CTX);
    expect(found.tier).toBe("read");
    const hits = found.readResult as { notes: unknown[] };
    expect(hits.notes).toHaveLength(2);
    // Bulk creation: each createTask alone is auto, but the PLAN is confirm (Flow C).
    expect(classifyPlan(["createTask", "createTask", "createTask"]).tier).toBe("confirm");
    expect(classifyPlan(["searchNotes", "createTask"]).tier).toBe("auto");
  });

  it("6. 'what do I need today' is a read-only synthesis", () => {
    const day = "2026-09-23";
    tasks.createTask({ title: "due today", dueAt: `${day}T12:00:00` }, { actor: "manual" });
    const res = exec.executeApproved("getWorkspaceContext", { date: day }, CTX);
    expect(res.tier).toBe("read");
    const context = res.result as { tasksDueToday: unknown[]; counts: { tasksDueToday: number } };
    expect(context.counts.tasksDueToday).toBe(1);
  });

  it("7. ambiguous/underspecified input is rejected, never guessed", () => {
    const before = log.listRecent(1000).length;
    // No selector at all, empty patch, wrong types — all correctable, none executed.
    expect(() => exec.propose("updateTask", { patch: { status: "done" } }, CTX)).toThrowError(FlowOSError);
    expect(() => exec.propose("updateTask", { ids: ["t-1"], patch: {} }, CTX)).toThrowError(FlowOSError);
    expect(() => exec.propose("reschedule", { eventId: "e-1" }, CTX)).toThrowError(FlowOSError);
    expect(log.listRecent(1000).length).toBe(before);
  });

  it("8. nonexistent targets fail gracefully with zero rows", () => {
    const before = log.listRecent(1000).length;
    expect(() => exec.propose("updateTask", { ids: ["ghost"], patch: { status: "done" } }, CTX)).toThrowError(/not found/i);
    expect(() => exec.propose("reschedule", { eventId: "ghost", newStartAt: "2026-09-24T10:00:00", newEndAt: "2026-09-24T11:00:00" }, CTX)).toThrowError(/not found/i);
    expect(() =>
      exec.propose("generateSchedule", { dateFrom: "2026-09-24T00:00:00", dateTo: "2026-09-25T00:00:00", taskIds: ["ghost"] }, CTX),
    ).toThrowError(/not found/i);
    expect(log.listRecent(1000).length).toBe(before);
  });

  it("9. destructive bulk requests hit blocked and write zero rows", () => {
    tasks.createTask({ title: "d1", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "d2", priority: "urgent" }, { actor: "manual" });
    const day = "2026-09-24";
    calendar.createEvent({ title: "x", startAt: `${day}T10:00:00`, endAt: `${day}T11:00:00` }, { actor: "manual" });
    const rowsBefore = log.listRecent(1000).length;
    const bulkTasks = exec.propose("deleteTask", { filter: { priority: "urgent" } }, CTX);
    expect(bulkTasks.tier).toBe("blocked");
    expect(bulkTasks.itemizedAlternative?.tier).toBe("confirm");
    const bulkEvents = exec.propose("deleteEvent", { filter: { from: `${day}T00:00:00`, to: `${day}T23:59:59` } }, CTX);
    expect(bulkEvents.tier).toBe("blocked");
    expect(() => exec.executeApproved("deleteTask", { filter: { priority: "urgent" } }, CTX)).toThrowError(/blocked/i);
    expect(log.listRecent(1000).length).toBe(rowsBefore); // zero mutation rows
    expect(tasks.listTasks()).toHaveLength(2); // state untouched
  });

  it("10. empty scope degrades gracefully (no matches, no-op, no rows)", () => {
    const rowsBefore = log.listRecent(1000).length;
    const proposal = exec.propose("updateTask", { filter: { tag: "no-such-tag" }, patch: { status: "done" } }, CTX);
    expect(proposal.kind).toBe("empty");
    const res = exec.executeApproved("updateTask", { filter: { tag: "no-such-tag" }, patch: { status: "done" } }, CTX);
    expect(res.executed).toBe(false);
    expect(log.listRecent(1000).length).toBe(rowsBefore);
  });

  it("regression: agent action invalidated by a later manual edit is not silently undone (docs/20)", () => {
    // Executor-level variant of the services.test.ts regression: the agent's
    // change goes through propose/execute, the user edits manually after,
    // and undo must refuse with an explanation instead of corrupting state.
    const t = tasks.createTask({ title: "draft" }, { actor: "manual" });
    const input = { ids: [t.id], patch: { status: "done" } };
    const proposal = exec.propose("updateTask", input, CTX);
    expect(proposal.tier).toBe("auto");
    exec.executeApproved("updateTask", input, CTX);
    tasks.updateTask(t.id, { title: "user-renamed" }, { actor: "manual" }); // independent later change
    const agentEntry = log.listRecent(1000).find((e) => e.actor === "agent" && e.toolOrOperation === "updateTask");
    expect(agentEntry).toBeDefined();
    const undo = new UndoService(db);
    expect(() => undo.undoEntry(agentEntry!.id, "manual")).toThrowError(/later change/i);
    expect(log.getById(agentEntry!.id)?.undoStatus).toBe("no-longer-undoable");
    expect(tasks.getTask(t.id)?.title).toBe("user-renamed"); // untouched
  });
});
