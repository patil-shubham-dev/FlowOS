// Foundation tests: services, action log, undo engine, secrets.
// These pin the doc-12/data-layer contract everything above depends on.
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, beforeEach } from "vitest";
import { closeDatabase, openDatabase, type FlowDatabase } from "../src/db/database.js";
import { ActionLogService } from "../src/services/actionLog.js";
import { CalendarService } from "../src/services/calendar.js";
import { NoteService } from "../src/services/notes.js";
import { PreferenceService } from "../src/services/preferences.js";
import { ProviderConfigService } from "../src/services/providers.js";
import { TaskService } from "../src/services/tasks.js";
import { UndoService } from "../src/services/undo.js";
import { decryptSecret, encryptSecret, FlowOSError, redactSecrets } from "../src/security/secrets.js";
import type { MutationContext } from "../src/model/types.js";

const MANUAL: MutationContext = { actor: "manual" };
const AGENT: MutationContext = { actor: "agent", sessionId: "sess-1" };

let db: FlowDatabase;
let dataDir: string;

beforeEach(() => {
  if (db) {
    try {
      closeDatabase(db);
    } catch {
      // already closed on first run
    }
  }
  db = openDatabase(":memory:");
  dataDir = mkdtempSync(join(tmpdir(), "flowos-test-"));
});

function services() {
  return {
    tasks: new TaskService(db),
    calendar: new CalendarService(db),
    notes: new NoteService(db),
    prefs: new PreferenceService(db),
    providers: new ProviderConfigService(db, dataDir),
    log: new ActionLogService(db),
    undo: new UndoService(db),
  };
}

describe("tasks", () => {
  it("creates, filters, updates, and deletes with validation", () => {
    const { tasks } = services();
    const t = tasks.createTask({ title: "Write report", priority: "urgent", tag: "work" }, MANUAL);
    expect(t.id).toBeTruthy();
    expect(t.source).toBe("manual");
    expect(tasks.listTasks({ priority: "urgent" })).toHaveLength(1);
    expect(tasks.listTasks({ tag: "work" })).toHaveLength(1);
    expect(tasks.listTasks({ status: "done" })).toHaveLength(0);

    const updated = tasks.updateTask(t.id, { status: "done" }, AGENT);
    expect(updated.status).toBe("done");
    expect(updated.source).toBe("agent");

    expect(() => tasks.createTask({ title: "   " }, MANUAL)).toThrowError(FlowOSError);
    expect(() => tasks.createTask({ title: undefined as unknown as string }, MANUAL)).toThrowError(/title/i);
    expect(() => tasks.updateTask("missing", { status: "done" }, MANUAL)).toThrowError(FlowOSError);
    expect(() => tasks.createTask({ title: "x", dueAt: "not-a-date" }, MANUAL)).toThrowError(FlowOSError);

    tasks.deleteTask(t.id, MANUAL);
    expect(tasks.getTask(t.id)).toBeNull();
  });

  it("supports dry-run previews without committing or logging", () => {
    const { tasks, log } = services();
    const preview = tasks.previewCreate({ title: "Preview me" }, AGENT);
    expect(preview.committed).toBe(false);
    expect(tasks.listTasks()).toHaveLength(0);
    expect(log.listRecent()).toHaveLength(0);
  });
});

describe("calendar", () => {
  it("detects conflicts and finds free time", () => {
    const { calendar } = services();
    const day = "2026-09-14";
    calendar.createEvent({ title: "Maths", startAt: `${day}T19:00:00`, endAt: `${day}T20:00:00` }, MANUAL);
    calendar.createEvent({ title: "Reading", startAt: `${day}T19:30:00`, endAt: `${day}T20:30:00` }, MANUAL);
    const conflicts = calendar.detectConflicts({ from: `${day}T00:00:00`, to: `${day}T23:59:59` });
    expect(conflicts).toHaveLength(1);
    expect(conflicts[0]?.a.title).toBe("Maths");

    const free = calendar.findFreeTime({ from: `${day}T18:00:00`, to: `${day}T22:00:00` }, 30);
    expect(free).toHaveLength(2);
    // Compare as instants: service normalizes to UTC ISO strings.
    expect(Date.parse(free[0]!.startAt)).toBe(Date.parse(`${day}T18:00:00`));
    expect(Date.parse(free[0]!.endAt)).toBe(Date.parse(`${day}T19:00:00`));
    expect(Date.parse(free[1]!.startAt)).toBe(Date.parse(`${day}T20:30:00`));
    expect(Date.parse(free[1]!.endAt)).toBe(Date.parse(`${day}T22:00:00`));

    expect(() =>
      calendar.createEvent({ title: "Bad", startAt: `${day}T21:00:00`, endAt: `${day}T20:00:00` }, MANUAL),
    ).toThrowError(FlowOSError);
  });
});

describe("notes", () => {
  it("searches full-text and links tasks idempotently", () => {
    const { notes, tasks } = services();
    const t = tasks.createTask({ title: "TTS project" }, MANUAL);
    const n = notes.createNote({ title: "TTS ideas", content: "text to speech pipeline notes" }, MANUAL);
    expect(notes.searchNotes("speech")).toHaveLength(1);
    expect(notes.searchNotes("nothing-matching-xyz")).toHaveLength(0);

    const linked = notes.linkNoteToTask(n.id, t.id, AGENT);
    expect(linked.linkedTaskIds).toContain(t.id);
    const again = notes.linkNoteToTask(n.id, t.id, AGENT);
    expect(again.linkedTaskIds.filter((id) => id === t.id)).toHaveLength(1);
    expect(() => notes.linkNoteToTask(n.id, "ghost-task", AGENT)).toThrowError(FlowOSError);
  });
});

describe("preferences", () => {
  it("saves and deletes visible facts", () => {
    const { prefs } = services();
    const p = prefs.saveLearnedPreference("Study at 7pm", AGENT);
    expect(prefs.listPreferences()).toHaveLength(1);
    prefs.deleteLearnedPreference(p.id, MANUAL);
    expect(prefs.listPreferences()).toHaveLength(0);
  });
});

describe("provider configs", () => {
  it("encrypts keys at rest and round-trips them", () => {
    const { providers } = services();
    const cfg = providers.upsertConfig({ providerType: "anthropic", model: "claude-sonnet-4-5", apiKey: "sk-ant-test-123", isDefault: true }, MANUAL);
    expect(cfg.apiKeyEncrypted).not.toContain("sk-ant-test-123");
    expect(providers.revealApiKey(cfg)).toBe("sk-ant-test-123");
    expect(providers.getDefault()?.id).toBe(cfg.id);
    const display = providers.listConfigsForDisplay();
    expect(display[0]?.hasApiKey).toBe(true);
    expect("apiKeyEncrypted" in (display[0] as object)).toBe(false);
  });
});

describe("action log + undo", () => {
  it("logs every mutation and undoes an update", () => {
    const { tasks, log, undo } = services();
    const t = tasks.createTask({ title: "Original" }, AGENT);
    tasks.updateTask(t.id, { title: "Changed" }, { ...AGENT, groupId: "g1" });
    expect(log.listRecent().length).toBeGreaterThanOrEqual(2);

    const updateEntry = log.listRecent().find((e) => e.toolOrOperation === "updateTask");
    expect(updateEntry).toBeDefined();
    undo.undoEntry(updateEntry!.id, "manual");
    expect(tasks.getTask(t.id)?.title).toBe("Original");
    expect(log.getById(updateEntry!.id)?.undoStatus).toBe("undone");
  });

  it("undoes a delete by restoring the snapshot", () => {
    const { tasks, log, undo } = services();
    const t = tasks.createTask({ title: "Doomed" }, MANUAL);
    tasks.deleteTask(t.id, AGENT);
    expect(tasks.getTask(t.id)).toBeNull();
    const del = log.listRecent().find((e) => e.toolOrOperation === "deleteTask");
    undo.undoEntry(del!.id, "manual");
    expect(tasks.getTask(t.id)?.title).toBe("Doomed");
  });

  it("undoes a grouped cascade as one unit", () => {
    const { calendar, log, undo } = services();
    const day = "2026-09-15";
    const a = calendar.createEvent({ title: "A", startAt: `${day}T19:00:00`, endAt: `${day}T20:00:00`, isFlexible: true }, AGENT);
    const b = calendar.createEvent({ title: "B", startAt: `${day}T20:00:00`, endAt: `${day}T20:30:00`, isFlexible: true }, AGENT);
    calendar.updateEvent(a.id, { startAt: `${day}T19:30:00`, endAt: `${day}T20:30:00` }, { ...AGENT, groupId: "cascade-1" });
    calendar.updateEvent(b.id, { startAt: `${day}T20:30:00`, endAt: `${day}T21:00:00` }, { ...AGENT, groupId: "cascade-1" });

    const group = log.listByGroup("cascade-1");
    expect(group).toHaveLength(2);
    undo.undoEntry(group[0]!.id, "manual");
    expect(Date.parse(calendar.getEvent(a.id)!.startAt)).toBe(Date.parse(`${day}T19:00:00`));
    expect(Date.parse(calendar.getEvent(b.id)!.startAt)).toBe(Date.parse(`${day}T20:00:00`));
  });

  it("refuses undo with an explanation when state moved on", () => {
    const { tasks, log, undo } = services();
    const t = tasks.createTask({ title: "v1" }, AGENT);
    tasks.updateTask(t.id, { title: "v2" }, AGENT);
    tasks.updateTask(t.id, { title: "v3" }, MANUAL); // independent later change
    const updates = log.listRecent(10).filter((e) => e.toolOrOperation === "updateTask");
    const stale = updates[1]; // the v1 -> v2 entry
    const latest = updates[0]; // the v2 -> v3 entry
    expect(stale).toBeDefined();
    expect(latest).toBeDefined();
    expect(() => undo.undoEntry(stale!.id, "manual")).toThrowError(/later change/i);
    expect(log.getById(stale!.id)?.undoStatus).toBe("no-longer-undoable");
    // The latest entry still undoes cleanly (restores v2).
    undo.undoEntry(latest!.id, "manual");
    expect(tasks.getTask(t.id)?.title).toBe("v2");
  });
});

describe("secrets", () => {
  it("encrypts/decrypts and redacts credentials from log output", () => {
    const envelope = encryptSecret("sk-ant-secret-xyz", dataDir);
    expect(envelope).not.toContain("sk-ant-secret-xyz");
    expect(decryptSecret(envelope, dataDir)).toBe("sk-ant-secret-xyz");
    const redacted = redactSecrets({ apiKey: "sk-ant-secret-xyz", nested: { token: "abc" }, ok: "fine" });
    expect(JSON.stringify(redacted)).not.toContain("sk-ant-secret-xyz");
    expect((redacted as Record<string, unknown>)["ok"]).toBe("fine");
  });
});
