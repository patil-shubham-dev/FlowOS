// Preview contract tests: EVERY mutating service method honors dryRun
// (no commit, no ActionLog row) and exposes a preview* wrapper returning
// { committed: false, before, after }. The risk classifier + confirm gate
// depend on this uniformly — a service added later without preview support
// must fail here, not in production.
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { closeDatabase, openDatabase, type FlowDatabase } from "../src/db/database.js";
import { ActionLogService } from "../src/services/actionLog.js";
import { CalendarService } from "../src/services/calendar.js";
import { NoteService } from "../src/services/notes.js";
import { PreferenceService } from "../src/services/preferences.js";
import { ProviderConfigService } from "../src/services/providers.js";
import { TaskService } from "../src/services/tasks.js";

let db: FlowDatabase;
let dataDir: string;
let log: ActionLogService;

beforeEach(() => {
  if (db) {
    try {
      closeDatabase(db);
    } catch {
      // first run
    }
  }
  db = openDatabase(":memory:");
  dataDir = mkdtempSync(join(tmpdir(), "flowos-preview-"));
  log = new ActionLogService(db);
});

function logCount(): number {
  return log.listRecent(1000).length;
}

describe("preview contract", () => {
  it("tasks: dryRun + previews commit nothing and log nothing", () => {
    const svc = new TaskService(db);
    const base = logCount();
    const created = svc.createTask({ title: "t" }, { actor: "agent", dryRun: true });
    expect(created.title).toBe("t");
    const real = svc.createTask({ title: "t" }, { actor: "agent" });
    const upd = svc.updateTask(real.id, { status: "done" }, { actor: "agent", dryRun: true });
    expect(upd.status).toBe("done");
    expect(svc.getTask(real.id)?.status).toBe("todo");
    expect(svc.deleteTask(real.id, { actor: "agent", dryRun: true })).toEqual({ id: real.id });
    expect(svc.getTask(real.id)).not.toBeNull();

    const p1 = svc.previewCreate({ title: "p" }, { actor: "agent" });
    const p2 = svc.previewUpdate(real.id, { title: "p2" }, { actor: "agent" });
    const p3 = svc.previewDelete(real.id, { actor: "agent" });
    for (const p of [p1, p2, p3]) {
      expect(p.committed).toBe(false);
      expect(p.before).toBeDefined();
      expect(p.after).toBeDefined();
    }
    expect(p1.before).toEqual([null]);
    expect(p3.after).toEqual([null]);
    expect(svc.listTasks()).toHaveLength(1);
    // Only the one real create wrote a log row.
    expect(logCount() - base).toBe(1);
  });

  it("calendar: dryRun + previews commit nothing and log nothing", () => {
    const svc = new CalendarService(db);
    const base = logCount();
    const day = "2026-09-16";
    const draft = svc.createEvent({ title: "e", startAt: `${day}T10:00:00`, endAt: `${day}T11:00:00` }, { actor: "agent", dryRun: true });
    expect(draft.title).toBe("e");
    const real = svc.createEvent({ title: "e", startAt: `${day}T10:00:00`, endAt: `${day}T11:00:00` }, { actor: "agent" });
    const p1 = svc.previewCreate({ title: "p", startAt: `${day}T12:00:00`, endAt: `${day}T13:00:00` }, { actor: "agent" });
    const p2 = svc.previewUpdate(real.id, { title: "moved" }, { actor: "agent" });
    const p3 = svc.previewDelete(real.id, { actor: "agent" });
    for (const p of [p1, p2, p3]) {
      expect(p.committed).toBe(false);
      expect(p.before).toBeDefined();
      expect(p.after).toBeDefined();
    }
    expect(p3.after).toEqual([null]);
    expect(svc.listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` })).toHaveLength(1);
    expect(logCount() - base).toBe(1);
  });

  it("notes: dryRun + previews (incl. link) commit nothing and log nothing", () => {
    const notes = new NoteService(db);
    const tasks = new TaskService(db);
    const base = logCount();
    const t = tasks.createTask({ title: "t" }, { actor: "manual" });
    const n = notes.createNote({ title: "n", content: "body" }, { actor: "agent" });
    const p1 = notes.previewCreate({ title: "p" }, { actor: "agent" });
    const p2 = notes.previewUpdate(n.id, { content: "changed" }, { actor: "agent" });
    const p3 = notes.previewDelete(n.id, { actor: "agent" });
    const p4 = notes.previewLinkNoteToTask(n.id, t.id, { actor: "agent" });
    for (const p of [p1, p2, p3, p4]) {
      expect(p.committed).toBe(false);
      expect(p.before).toBeDefined();
      expect(p.after).toBeDefined();
    }
    expect(notes.getNote(n.id)?.linkedTaskIds).toEqual([]);
    expect(logCount() - base).toBe(2); // real task create + real note create
  });

  it("preferences: dryRun + previews commit nothing and log nothing", () => {
    const svc = new PreferenceService(db);
    const base = logCount();
    const p1 = svc.previewSaveLearnedPreference("likes mornings", { actor: "agent" });
    expect(p1.committed).toBe(false);
    expect(p1.before).toEqual([null]);
    const real = svc.saveLearnedPreference("likes mornings", { actor: "agent", sessionId: "s" });
    const p2 = svc.previewDeleteLearnedPreference(real.id, { actor: "agent" });
    expect(p2.committed).toBe(false);
    expect(p2.after).toEqual([null]);
    expect(svc.listPreferences()).toHaveLength(1);
    expect(logCount() - base).toBe(1);
  });

  it("provider configs: dryRun + previews commit nothing and log nothing", () => {
    const svc = new ProviderConfigService(db, dataDir);
    const base = logCount();
    const p1 = svc.previewUpsert(
      { providerType: "anthropic", model: "m", apiKey: "k" },
      { actor: "manual" },
    );
    expect(p1.committed).toBe(false);
    expect(p1.before).toEqual([null]);
    expect(svc.listConfigs()).toHaveLength(0);
    const real = svc.upsertConfig({ providerType: "anthropic", model: "m", apiKey: "k" }, { actor: "manual" });
    const p2 = svc.previewDeleteConfig(real.id, { actor: "manual" });
    expect(p2.committed).toBe(false);
    expect(p2.after).toEqual([null]);
    expect(svc.listConfigs()).toHaveLength(1);
    expect(logCount() - base).toBe(1);
  });
});
