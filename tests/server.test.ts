// HTTP endpoint tests: the app is bound to an ephemeral port against an
// isolated in-memory database (createApp has no listen side effect, so no
// mocks and no port conflicts). Covers reads, manual writes, validation
// shapes, and manual/agent actor parity in the audit log (docs/12 §1).
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createServer, type Server } from "node:http";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";

let base = "";
let server: Server;

beforeAll(async () => {
  const dir = mkdtempSync(join(tmpdir(), "flowos-apptest-"));
  const app = createApp({ dbPath: ":memory:", dataDir: dir, publicDir: join(dir, "public-missing") });
  server = createServer((req, res) => app.handle(req, res));
  await new Promise<void>((resolve) => server.listen(0, resolve));
  const addr = server.address();
  const port = typeof addr === "object" && addr ? addr.port : 0;
  base = `http://localhost:${port}`;
});

afterAll(async () => {
  await new Promise<void>((resolve, reject) => server.close((e) => (e ? reject(e) : resolve())));
});

async function call(method: string, path: string, body?: unknown): Promise<{ status: number; json: unknown }> {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? (JSON.parse(text) as unknown) : null;
  } catch {
    parsed = text;
  }
  return { status: res.status, json: parsed };
}

describe("tasks endpoints", () => {
  it("creates, filters, updates, deletes with proper statuses", async () => {
    const bad = await call("POST", "/api/tasks", { title: "   " });
    expect(bad.status).toBe(400);

    const created = await call("POST", "/api/tasks", { title: "Write up", priority: "urgent" });
    expect(created.status).toBe(201);
    const id = (created.json as { task: { id: string } }).task.id;

    const filtered = await call("GET", "/api/tasks?priority=urgent");
    expect(((filtered.json as { tasks: unknown[] }).tasks)).toHaveLength(1);

    const patched = await call("PATCH", `/api/tasks/${id}`, { status: "done" });
    expect(patched.status).toBe(200);
    expect((patched.json as { task: { status: string } }).task.status).toBe("done");

    const missing = await call("PATCH", "/api/tasks/ghost", { status: "done" });
    expect(missing.status).toBe(404);

    const deleted = await call("DELETE", `/api/tasks/${id}`);
    expect(deleted.status).toBe(200);
    const gone = await call("DELETE", `/api/tasks/${id}`);
    expect(gone.status).toBe(404);
  });
});

describe("events endpoints", () => {
  it("requires ranges, validates, and round-trips", async () => {
    expect((await call("GET", "/api/events")).status).toBe(400);
    expect((await call("GET", "/api/events?from=nope&to=nope")).status).toBe(400);

    const bad = await call("POST", "/api/events", { title: "x", startAt: "2026-09-20T12:00:00", endAt: "2026-09-20T11:00:00" });
    expect(bad.status).toBe(400);

    const created = await call("POST", "/api/events", { title: "Lunch", startAt: "2026-09-20T12:00:00", endAt: "2026-09-20T13:00:00", isFlexible: true });
    expect(created.status).toBe(201);
    const id = (created.json as { event: { id: string } }).event.id;

    const listed = await call("GET", "/api/events?from=2026-09-20T00:00:00&to=2026-09-20T23:59:59");
    expect(((listed.json as { events: unknown[] }).events)).toHaveLength(1);

    const moved = await call("PATCH", `/api/events/${id}`, { startAt: "2026-09-20T13:00:00", endAt: "2026-09-20T14:00:00" });
    expect(moved.status).toBe(200);

    expect((await call("DELETE", `/api/events/${id}`)).status).toBe(200);
    expect((await call("DELETE", `/api/events/${id}`)).status).toBe(404);
  });
});

describe("notes endpoints", () => {
  it("creates, reads full + summaries, updates, deletes", async () => {
    const created = await call("POST", "/api/notes", { title: "Idea", content: "# hello", tags: ["x"] });
    expect(created.status).toBe(201);
    const id = (created.json as { note: { id: string } }).note.id;

    const list = await call("GET", "/api/notes");
    const rows = (list.json as { notes: Array<{ id: string; content?: string }> }).notes;
    expect(rows).toHaveLength(1);
    expect(rows[0]?.content).toBeUndefined(); // summaries stay light

    const full = await call("GET", `/api/notes/${id}`);
    expect(((full.json as { note: { content: string } }).note.content)).toBe("# hello");
    expect((await call("GET", "/api/notes/ghost")).status).toBe(404);

    expect((await call("PATCH", `/api/notes/${id}`, { content: "# edited" })).status).toBe(200);
    expect((await call("DELETE", `/api/notes/${id}`)).status).toBe(200);
    expect((await call("DELETE", `/api/notes/${id}`)).status).toBe(404);
  });
});

describe("turn/history/log/undo/preferences/providers", () => {
  it("round-trips a turn and records thread history", async () => {
    const turn = await call("POST", "/api/turn", {
      context: { surface: "home" },
      instruction: "file it",
      plan: { steps: [{ tool: "createTask", input: { title: "via turn" } }] },
      sessionId: "http-sess",
    });
    expect(turn.status).toBe(200);
    expect((turn.json as { status: string }).status).toBe("executed");

    const hist = await call("GET", "/api/history?sessionId=http-sess");
    expect(((hist.json as { turns: unknown[] }).turns)).toHaveLength(1);
    expect((await call("GET", "/api/history")).status).toBe(400);

    const prefs = await call("GET", "/api/preferences");
    expect(((prefs.json as { preferences: unknown[] }).preferences)).toHaveLength(0);

    const providers = await call("GET", "/api/providers");
    expect(((providers.json as { configs: unknown[] }).configs)).toHaveLength(0);

    const log = await call("GET", "/api/log?limit=10");
    expect(((log.json as { entries: unknown[] }).entries.length)).toBeGreaterThan(0);

    expect((await call("POST", "/api/turn", { nope: 1 })).status).toBe(400);
    expect((await call("POST", "/api/undo", {})).status).toBe(400);
    expect((await call("GET", "/api/nope")).status).toBe(404);
  });
});

describe("actor parity (docs/12 §1)", () => {
  it("manual and agent edits share one log structure, differing only in actor", async () => {
    await call("POST", "/api/tasks", { title: "parity" });
    await call("POST", "/api/turn", {
      context: { surface: "home" },
      instruction: "file",
      plan: { steps: [{ tool: "createTask", input: { title: "parity-agent" } }] },
      sessionId: "parity-sess",
    });
    const log = (await call("GET", "/api/log?limit=10")).json as {
      entries: Array<Record<string, unknown>>;
    };
    const manual = log.entries.find((e) => e["actor"] === "manual" && e["toolOrOperation"] === "createTask");
    const agent = log.entries.find((e) => e["actor"] === "agent" && e["toolOrOperation"] === "createTask");
    expect(manual).toBeDefined();
    expect(agent).toBeDefined();
    // Identical audit structure — actor is metadata, not a parallel system.
    expect(Object.keys(manual!).sort()).toEqual(Object.keys(agent!).sort());
    expect(manual!["undoStatus"]).toBe("available");
    expect(agent!["undoStatus"]).toBe("available");
    expect(manual!["beforeState"]).toEqual([null]);
    expect(agent!["beforeState"]).toEqual([null]);
  });
});

describe("provider CRUD + validate", () => {
  it("create, list, validate, delete a provider config", async () => {
    const created = await call("POST", "/api/providers", {
      providerType: "anthropic",
      model: "claude-sonnet-4-5",
      apiKey: "test-key-123",
      isDefault: true,
    });
    expect(created.status).toBe(201);
    const cfg = (created.json as { config: { id: string; providerType: string; model: string; hasApiKey: boolean; isDefault: boolean } }).config;
    expect(cfg.providerType).toBe("anthropic");
    expect(cfg.model).toBe("claude-sonnet-4-5");
    expect(cfg.hasApiKey).toBe(true);
    expect(cfg.isDefault).toBe(true);

    const list = await call("GET", "/api/providers");
    const configs = (list.json as { configs: Array<{ id: string }> }).configs;
    expect(configs.some((c) => c.id === cfg.id)).toBe(true);

    const val = await call("POST", `/api/providers/${cfg.id}/validate`);
    const valBody = val.json as { ok: boolean; error?: { code: string; message: string } };
    expect(typeof valBody.ok).toBe("boolean");
    if (!valBody.ok) {
      expect(valBody.error).toBeDefined();
      expect(typeof valBody.error!.message).toBe("string");
    }

    const del = await call("DELETE", `/api/providers/${cfg.id}`);
    expect(del.status).toBe(200);
    expect((del.json as { id: string }).id).toBe(cfg.id);

    const afterDel = await call("GET", "/api/providers");
    expect((afterDel.json as { configs: Array<{ id: string }> }).configs.some((c) => c.id === cfg.id)).toBe(false);
  });

  it("rejects invalid provider type", async () => {
    const res = await call("POST", "/api/providers", { providerType: "invalid", model: "test" });
    expect(res.status).toBe(400);
    expect((res.json as { error?: { code: string } }).error?.code).toBe("VALIDATION");
  });

  it("rejects empty model", async () => {
    const res = await call("POST", "/api/providers", { providerType: "anthropic", model: "" });
    expect(res.status).toBe(400);
  });
});

describe("preference edit + delete", () => {
  it("create, edit, delete a preference", async () => {
    await call("POST", "/api/turn", {
      context: { surface: "home" },
      instruction: "save pref",
      plan: { steps: [{ tool: "saveLearnedPreference", input: { statement: "User prefers dark mode" } }] },
    });
    const list = await call("GET", "/api/preferences");
    const prefs = (list.json as { preferences: Array<{ id: string; statement: string }> }).preferences;
    expect(prefs.length).toBeGreaterThan(0);
    const pref = prefs[prefs.length - 1]!;

    const edited = await call("PATCH", `/api/preferences/${pref.id}`, { statement: "User prefers light mode" });
    expect(edited.status).toBe(200);
    expect((edited.json as { preference: { statement: string } }).preference.statement).toBe("User prefers light mode");

    const del = await call("DELETE", `/api/preferences/${pref.id}`);
    expect(del.status).toBe(200);
    expect((del.json as { id: string }).id).toBe(pref.id);

    const afterDel = await call("GET", "/api/preferences");
    expect((afterDel.json as { preferences: Array<{ id: string }> }).preferences.some((p) => p.id === pref.id)).toBe(false);
  });

  it("rejects empty statement on edit", async () => {
    await call("POST", "/api/turn", {
      context: { surface: "home" },
      instruction: "save pref",
      plan: { steps: [{ tool: "saveLearnedPreference", input: { statement: "Temp pref" } }] },
    });
    const list = await call("GET", "/api/preferences");
    const prefs = (list.json as { preferences: Array<{ id: string }> }).preferences;
    const pref = prefs[prefs.length - 1]!;
    const res = await call("PATCH", `/api/preferences/${pref.id}`, { statement: "" });
    expect(res.status).toBe(400);
  });
});
