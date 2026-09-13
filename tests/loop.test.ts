// Orchestration loop tests: the doc-09 pipeline as one connected path,
// callable with no frontend. Covers both context shapes (scoped-push page
// calls, Home pull-based calls), multi-step plans, the confirm-gate round
// trip, blocked/empty outcomes, verification on the success path, preference
// citation, backend-owned thread history, and the top-level UI discriminant.
import { beforeEach, describe, expect, it } from "vitest";
import { AgentLoop, type ExplicitPlan, type PlanApproval } from "../src/agent/loop.js";
import { closeDatabase, openDatabase, type FlowDatabase } from "../src/db/database.js";
import { ActionLogService } from "../src/services/actionLog.js";
import { CalendarService } from "../src/services/calendar.js";
import { NoteService } from "../src/services/notes.js";
import { PreferenceService } from "../src/services/preferences.js";
import { TaskService } from "../src/services/tasks.js";
import { UndoService } from "../src/services/undo.js";

let db: FlowDatabase;
let loop: AgentLoop;
let log: ActionLogService;
let tasks: TaskService;
let calendar: CalendarService;
let prefs: PreferenceService;

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
  prefs = new PreferenceService(db);
  loop = new AgentLoop({ db, tasks, calendar, notes, prefs });
  log = new ActionLogService(db);
});

function echoApproval(out: { approvalTemplate: PlanApproval | null }): PlanApproval {
  const template = out.approvalTemplate;
  if (!template) throw new Error("expected an approval template");
  return template;
}

function single(tool: ExplicitPlan["steps"][number]["tool"], input: unknown, preferencesUsed?: string[]): ExplicitPlan {
  return { steps: [{ tool, input }], ...(preferencesUsed ? { preferencesUsed } : {}) };
}

describe("scoped-page calls (scoped-push context)", () => {
  it("auto-tier turn executes end to end with verification + summary + toast hint", () => {
    const t = tasks.createTask({ title: "scoped one", priority: "urgent" }, { actor: "manual" });
    const out = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [t.id], filters: { priority: "urgent" }, selectedId: t.id },
      instruction: "mark the selected task done",
      plan: single("updateTask", { ids: [t.id], patch: { status: "done" } }),
    });
    expect(out.status).toBe("executed");
    expect(out.ui).toBe("toast");
    expect(out.tier).toBe("auto");
    expect(out.verifications.every((v) => v.ok)).toBe(true);
    expect(out.error).toBeNull();
    expect(out.summary).toMatch(/updated 'scoped one' \(status: todo → done\)/);
    expect(out.summary).toMatch(/undo/i);
    expect(tasks.getTask(t.id)?.status).toBe("done");
  });

  it("confirm-tier turn pauses at the gate, then executes on approval echo", () => {
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const rowsBefore = log.listRecent(1000).length;
    const plan = single("updateTask", { ids: [a.id, b.id], patch: { status: "done" } });
    const pending = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [a.id, b.id] },
      instruction: "mark both done",
      plan,
    });
    expect(pending.status).toBe("needs-approval");
    expect(pending.ui).toBe("diff-card");
    expect(pending.proposals).toHaveLength(1);
    expect(pending.proposals[0]?.preview).toHaveLength(2);
    expect(log.listRecent(1000).length).toBe(rowsBefore); // gate wrote nothing

    const approved = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [a.id, b.id] },
      instruction: "mark both done",
      plan,
      approval: echoApproval(pending),
    });
    expect(approved.status).toBe("executed");
    expect(approved.ui).toBe("inline");
    expect(approved.verifications.every((v) => v.ok)).toBe(true);
    expect(approved.summary).toMatch(/grouped action/);
    expect(tasks.getTask(a.id)?.status).toBe("done");
  });

  it("blocked turn returns the itemized alternative with zero rows", () => {
    tasks.createTask({ title: "x1", priority: "urgent" }, { actor: "manual" });
    tasks.createTask({ title: "x2", priority: "urgent" }, { actor: "manual" });
    const rowsBefore = log.listRecent(1000).length;
    const out = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [] },
      instruction: "delete all urgent tasks",
      plan: single("deleteTask", { filter: { priority: "urgent" } }),
    });
    expect(out.status).toBe("blocked");
    expect(out.ui).toBe("blocked-card");
    expect(out.proposals[0]?.itemizedAlternative?.affectedIds).toHaveLength(2);
    expect(log.listRecent(1000).length).toBe(rowsBefore);
    expect(out.summary).toMatch(/item by item/);
  });

  it("malformed scope fails with a specific error, never a guess", () => {
    const out = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: "not-an-array" as unknown as string[] },
      instruction: "do something",
      plan: single("updateTask", { ids: ["t"], patch: { status: "done" } }),
    });
    expect(out.status).toBe("failed");
    expect(out.ui).toBe("message");
    expect(out.proposals).toHaveLength(0);
    expect(out.error?.code).toBe("MALFORMED_TOOL_CALL");
  });
});

describe("multi-step plans (the plan seam)", () => {
  it("read-then-create plan executes both steps with joined summaries", () => {
    const out = loop.runTurn({
      context: { surface: "home" },
      instruction: "check notes then file a task",
      plan: {
        steps: [
          { tool: "searchNotes", input: { query: "anything" } },
          { tool: "createTask", input: { title: "filed from plan" } },
        ],
      },
    });
    expect(out.status).toBe("executed");
    expect(out.ui).toBe("toast"); // auto mutating step present, no confirm member
    expect(out.proposals).toHaveLength(2);
    expect(out.verifications).toHaveLength(2);
    expect(tasks.listTasks().some((t) => t.title === "filed from plan")).toBe(true);
  });

  it("multi-create plan gates as one confirm even though each create is auto", () => {
    const plan: ExplicitPlan = {
      steps: [
        { tool: "createTask", input: { title: "p1" } },
        { tool: "createTask", input: { title: "p2" } },
      ],
    };
    const pending = loop.runTurn({ context: { surface: "home" }, instruction: "file two tasks", plan });
    expect(pending.status).toBe("needs-approval");
    expect(pending.tier).toBe("confirm");
    expect(pending.ui).toBe("diff-card");
    expect(tasks.listTasks()).toHaveLength(0);
    const done = loop.runTurn({ context: { surface: "home" }, instruction: "file two tasks", plan, approval: echoApproval(pending) });
    expect(done.status).toBe("executed");
    expect(done.groupId).toBeTruthy();
    // One plan group: single undo restores both creations.
    expect(log.listByGroup(done.groupId as string)).toHaveLength(2);
  });

  it("edited plan after approval is rejected (fingerprint mismatch)", () => {
    const a = tasks.createTask({ title: "a" }, { actor: "manual" });
    const pending = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [a.id] },
      instruction: "rename",
      plan: single("updateTask", { ids: [a.id], patch: { title: "renamed" } }),
    });
    // Single update is auto — force a confirm plan instead for this check.
    expect(pending.status).toBe("executed");
    const b = tasks.createTask({ title: "b" }, { actor: "manual" });
    const planB = single("updateTask", { ids: [a.id, b.id], patch: { title: "x" } });
    const pendingB = loop.runTurn({ context: { surface: "scoped:tasks", visibleIds: [a.id, b.id] }, instruction: "rename both", plan: planB });
    expect(pendingB.status).toBe("needs-approval");
    const edited = single("updateTask", { ids: [a.id, b.id], patch: { title: "DIFFERENT" } });
    const out = loop.runTurn({
      context: { surface: "scoped:tasks", visibleIds: [a.id, b.id] },
      instruction: "rename both",
      plan: edited,
      approval: echoApproval(pendingB),
    });
    expect(out.status).toBe("failed");
    // Part 1 renamed a -> 'renamed'; the edited (unapproved) plan changed nothing further.
    expect(tasks.getTask(a.id)?.title).toBe("renamed");
  });
});

describe("Home calls (pull-based context)", () => {
  it("read turn synthesizes from ambient context without preloading state", () => {
    const day = "2026-09-27";
    tasks.createTask({ title: "today thing", dueAt: `${day}T12:00:00` }, { actor: "manual" });
    const out = loop.runTurn({
      context: { surface: "home" },
      instruction: "what do I need to do today?",
      plan: single("getWorkspaceContext", { date: day }),
    });
    expect(out.status).toBe("executed");
    expect(out.ui).toBe("inline");
    expect(out.error).toBeNull();
  });

  it("preference consulted by the plan is cited in the summary and touched", () => {
    const pref = prefs.saveLearnedPreference("you usually study at 7pm", { actor: "manual" });
    expect(pref.lastUsedAt).toBeNull();
    const day = "2026-09-28";
    const maths = calendar.createEvent({ title: "Maths study", startAt: `${day}T17:00:00`, endAt: `${day}T18:00:00` }, { actor: "manual" });
    const plan = single(
      "reschedule",
      { eventId: maths.id, newStartAt: `${day}T19:00:00`, newEndAt: `${day}T20:00:00` },
      [pref.id],
    );
    const pending = loop.runTurn({
      context: { surface: "home" },
      instruction: "move maths to my usual study time",
      plan,
    });
    expect(pending.status).toBe("needs-approval");
    const done = loop.runTurn({
      context: { surface: "home" },
      instruction: "move maths to my usual study time",
      plan,
      approval: echoApproval(pending),
    });
    expect(done.status).toBe("executed");
    expect(done.verifications.every((v) => v.ok)).toBe(true);
    // FR-6.10/FR-6.7: the used preference is named explicitly in the report…
    expect(done.summary).toMatch(/Since you usually study at 7pm, I moved 'Maths study' to /);
    // …and its usage is recorded (lastUsedAt set by the loop, not silently).
    expect(prefs.getPreference(pref.id)?.lastUsedAt).not.toBeNull();
  });

  it("empty turn explains itself without writing rows", () => {
    const rowsBefore = log.listRecent(1000).length;
    const out = loop.runTurn({
      context: { surface: "home" },
      instruction: "move all urgent tasks",
      plan: single("updateTask", { filter: { priority: "urgent" }, patch: { status: "done" } }),
    });
    expect(out.status).toBe("empty");
    expect(out.ui).toBe("message");
    expect(log.listRecent(1000).length).toBe(rowsBefore);
    expect(out.summary).toMatch(/No tasks match/);
  });
});

describe("partial execution (docs/20 catalog entry)", () => {
  it("mid-plan failure halts with precise accounting AND the completed steps undo as one group", () => {
    // Self-invalidating plan: all steps validate at propose time, but step 3's
    // premise (X exists) is destroyed by step 2's execution -> PARTIAL_EXECUTION.
    const target = tasks.createTask({ title: "target" }, { actor: "manual" });
    const plan: ExplicitPlan = {
      steps: [
        { tool: "createTask", input: { title: "partial-one" } },
        { tool: "deleteTask", input: { ids: [target.id] } },
        { tool: "updateTask", input: { ids: [target.id], patch: { status: "done" } } },
      ],
    };
    const pending = loop.runTurn({ context: { surface: "home" }, instruction: "do all three", plan });
    // Three mutating calls floor the plan at confirm.
    expect(pending.status).toBe("needs-approval");
    const approval = echoApproval(pending);
    const out = loop.runTurn({ context: { surface: "home" }, instruction: "do all three", plan, approval });
    expect(out.status).toBe("failed");
    expect(out.error?.code).toBe("PARTIAL_EXECUTION");
    // Precise accounting: what completed, what didn't, rollback offer.
    expect(out.summary).toMatch(/completed before the failure/i);
    expect(out.summary).toMatch(/partial-one/);
    expect(out.summary).toMatch(/step 3/i);
    expect(out.summary).toMatch(/undo .* grouped action/i);
    expect(out.groupId).toBe(approval.groupId);
    // The completed steps REALLY committed…
    expect(tasks.listTasks().some((t) => t.title === "partial-one")).toBe(true);
    expect(tasks.getTask(target.id)).toBeNull();
    // …and the grouped-rollback offer is real: one undo reverts both.
    const entries = log.listByGroup(approval.groupId);
    expect(entries).toHaveLength(2);
    new UndoService(db).undoEntry(entries[0]!.id, "manual");
    expect(tasks.listTasks().some((t) => t.title === "partial-one")).toBe(false);
    expect(tasks.getTask(target.id)?.title).toBe("target");
  });

  it("first-step failure is a plain failure with nothing to roll back", () => {
    // Resolution drift between approval and execution fails INSIDE the loop
    // at step 1 with zero completed: plain failure, nothing to roll back.
    // (Content edits fail earlier at the plan seal — covered by the tampered
    // seal test below. Both leave zero side effects.)
    const target = tasks.createTask({ title: "t", priority: "urgent" }, { actor: "manual" });
    const plan2: ExplicitPlan = {
      steps: [
        { tool: "updateTask", input: { filter: { priority: "urgent" }, patch: { status: "done" } } },
        { tool: "createTask", input: { title: "never-reached" } },
      ],
    };
    const pending2 = loop.runTurn({ context: { surface: "home" }, instruction: "do both", plan: plan2 });
    expect(pending2.status).toBe("needs-approval");
    // Workspace drifts after approval: a new urgent task appears, so the
    // reviewed id set no longer matches the fresh resolution.
    tasks.createTask({ title: "late-arrival", priority: "urgent" }, { actor: "manual" });
    const out = loop.runTurn({ context: { surface: "home" }, instruction: "do both", plan: plan2, approval: echoApproval(pending2) });
    expect(out.status).toBe("failed");
    expect(out.error?.code).not.toBe("PARTIAL_EXECUTION");
    expect(out.summary).toMatch(/nothing completed/i);
    expect(out.summary).toMatch(/no changes need rolling back/i);
    expect(tasks.getTask(target.id)?.status).toBe("todo");
    expect(tasks.listTasks().some((t) => t.title === "never-reached")).toBe(false);
  });

  it("tampered plan seal fails closed with zero side effects", () => {
    const target = tasks.createTask({ title: "t" }, { actor: "manual" });
    const plan2: ExplicitPlan = {
      steps: [
        { tool: "deleteTask", input: { ids: [target.id] } },
        { tool: "createTask", input: { title: "never-reached" } },
      ],
    };
    const pending2 = loop.runTurn({ context: { surface: "home" }, instruction: "do both", plan: plan2 });
    expect(pending2.status).toBe("needs-approval");
    const badApproval = { ...echoApproval(pending2), planFingerprint: "tampered" };
    const out = loop.runTurn({ context: { surface: "home" }, instruction: "do both", plan: plan2, approval: badApproval });
    expect(out.status).toBe("failed");
    expect(out.error?.code).toBe("AMBIGUOUS_REQUEST");
    expect(tasks.getTask(target.id)?.title).toBe("t");
    expect(tasks.listTasks().some((t) => t.title === "never-reached")).toBe(false);
  });
});

describe("session corruption degrades loudly", () => {
  it("corrupt history JSON warns with session id, returns [], row untouched", async () => {
    const { SessionService } = await import("../src/services/sessions.js");
    db.prepare(
      `INSERT INTO agent_sessions (id, workspace_id, surface, started_at, messages)
       VALUES ('corrupt-1', 'default', 'home', '2026-09-12T00:00:00.000Z', 'not-json{{{')`,
    ).run();
    const seen: Array<{ sessionId: string | null; where: string }> = [];
    const svc = new SessionService(db, (detail) => seen.push(detail));
    expect(svc.getHistory("corrupt-1")).toEqual([]);
    expect(seen).toEqual([{ sessionId: "corrupt-1", where: "read" }]);
    // Row left untouched for forensics — still corrupt, still present.
    const raw = db.prepare(`SELECT messages FROM agent_sessions WHERE id = 'corrupt-1'`).get() as { messages: string };
    expect(raw.messages).toBe("not-json{{{");
  });
});

describe("thread persistence (backend owns history)", () => {
  it("two turns on one sessionId share visible history across loop instances", () => {
    const first = loop.runTurn({
      context: { surface: "home" },
      instruction: "file a task",
      plan: single("createTask", { title: "threaded" }),
      sessionId: "sess-abc",
    });
    expect(first.status).toBe("executed");
    expect(first.sessionId).toBe("sess-abc");

    const second = loop.runTurn({
      context: { surface: "home" },
      instruction: "what did I just do?",
      plan: single("getWorkspaceContext", {}),
      sessionId: "sess-abc",
    });
    expect(second.status).toBe("executed");

    // A FRESH loop over the same db sees the full thread — proof the
    // frontend is never the source of truth for thread state.
    const fresh = new AgentLoop({
      db,
      tasks: new TaskService(db),
      calendar: new CalendarService(db),
      notes: new NoteService(db),
      prefs: new PreferenceService(db),
    });
    const history = fresh.getHistory("sess-abc");
    expect(history).not.toBeNull();
    expect(history).toHaveLength(2);
    expect(history![0]?.instruction).toBe("file a task");
    expect(history![0]?.summary).toMatch(/created 'threaded'/);
    expect(history![0]?.steps[0]?.tool).toBe("createTask");
    expect(history![1]?.instruction).toBe("what did I just do?");
    expect(loop.getHistory("no-such-session")).toBeNull();
  });
});
