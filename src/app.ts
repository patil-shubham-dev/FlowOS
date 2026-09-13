// FlowOS HTTP application: JSON API over AgentLoop + services, static hosting
// for the built web UI. Stdlib only (node:http) — no framework dependency.
// Created via createApp() (no listen side effect) so tests can bind it to an
// ephemeral port against an isolated database. server.ts only wires env + listen.
//
// Manual UI writes (POST/PATCH/DELETE below) call the SAME service methods
// the agent tools call, with actor "manual" — one service layer, verified by
// the actor-parity check (docs/12 §1). Manual deletes use a plain client-side
// confirm; doc-11 risk tiers govern agent actions, not manual ones.
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { type IncomingMessage, type ServerResponse } from "node:http";
import { join, normalize, extname } from "node:path";
import { AgentLoop } from "./agent/loop.js";
import { planTurn, type PlannerContext } from "./agent/planner.js";
import { assembleContext } from "./agent/context.js";
import { openDatabase, type FlowDatabase } from "./db/database.js";
import { ActionLogService } from "./services/actionLog.js";
import { CalendarService } from "./services/calendar.js";
import { NoteService } from "./services/notes.js";
import { PreferenceService } from "./services/preferences.js";
import { PermissionOverrideService } from "./services/permissions.js";
import { ProviderConfigService, PROVIDER_TYPES, type UpsertProviderInput } from "./services/providers.js";
import { TaskService } from "./services/tasks.js";
import { UndoService } from "./services/undo.js";
import { FlowOSError } from "./security/secrets.js";
import { validateStoredConfig } from "./providers/validate.js";

const BODY_LIMIT = 1_000_000; // 1MB JSON cap

export interface AppOptions {
  dbPath: string;
  dataDir: string;
  publicDir: string;
}

export interface FlowApp {
  handle: (req: IncomingMessage, res: ServerResponse) => void;
  db: FlowDatabase;
}

const MIME: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const text = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Content-Length": Buffer.byteLength(text) });
  res.end(text);
}

function sendFlowError(res: ServerResponse, err: FlowOSError): void {
  sendJson(res, err.code === "NOT_FOUND" ? 404 : 400, { ok: false, error: { code: err.code, message: err.message, next: err.next } });
}

function readBody(req: IncomingMessage): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    req.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size > BODY_LIMIT) {
        reject(new FlowOSError("VALIDATION", "Request body too large.", "Keep requests under 1MB."));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        resolve(chunks.length === 0 ? {} : (JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown));
      } catch {
        reject(new FlowOSError("VALIDATION", "Request body is not valid JSON.", "Send a JSON object and retry."));
      }
    });
    req.on("error", (err: Error) => reject(err));
  });
}

function localDate(offsetDays = 0): string {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function asRecord(body: unknown): Record<string, unknown> {
  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    throw new FlowOSError("VALIDATION", "Request body must be a JSON object.", "Send an object and retry.");
  }
  return body as Record<string, unknown>;
}

/** Shape-checked at the boundary; services validate values (defense in depth). */
function asInput<T>(value: Record<string, unknown>): T {
  return value as unknown as T;
}

export function createApp(opts: AppOptions): FlowApp {
  const db = openDatabase(opts.dbPath);
  const tasks = new TaskService(db);
  const calendar = new CalendarService(db);
  const notes = new NoteService(db);
  const prefs = new PreferenceService(db);
  const providers = new ProviderConfigService(db, opts.dataDir);
  const perms = new PermissionOverrideService(db);
  const log = new ActionLogService(db);
  const undo = new UndoService(db);
  const loop = new AgentLoop({ db, tasks, calendar, notes, prefs });
  const PUBLIC_DIR = opts.publicDir;

  async function handleApi(req: IncomingMessage, res: ServerResponse, url: URL): Promise<boolean> {
    const path = url.pathname;
    // GET /api/overview?date=YYYY-MM-DD — ambient cards (service reads, no turn).
    if (req.method === "GET" && path === "/api/overview") {
      const day = (url.searchParams.get("date") ?? localDate()).slice(0, 10);
      const openTasks = tasks.listTasks().filter((t) => t.status !== "done");
      const dueToday = openTasks.filter((t) => t.dueAt !== null && t.dueAt.slice(0, 10) <= day);
      const todaysEvents = calendar.listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` });
      const upcoming = calendar
        .listEvents({ from: `${day}T00:00:00`, to: `${day}T23:59:59` })
        .slice(0, 3);
      sendJson(res, 200, {
        date: day,
        counts: { tasksOpen: openTasks.length, tasksDueToday: dueToday.length, eventsToday: todaysEvents.length, notes: notes.listNotes().length },
        tasksDueToday: dueToday.slice(0, 5).map((t) => ({ id: t.id, title: t.title, dueAt: t.dueAt, priority: t.priority, status: t.status })),
        upcomingEvents: upcoming.map((e) => ({ id: e.id, title: e.title, startAt: e.startAt, endAt: e.endAt })),
        todaysEvents: todaysEvents.map((e) => ({ id: e.id, title: e.title, startAt: e.startAt, endAt: e.endAt, isFlexible: e.isFlexible })),
      });
      return true;
    }
    // GET /api/providers — display-safe configs (presence, never values).
    if (req.method === "GET" && path === "/api/providers") {
      const configs = providers.listConfigsForDisplay();
      sendJson(res, 200, { configs, defaultId: configs.find((c) => c.isDefault)?.id ?? null });
      return true;
    }
    // POST /api/turn — the AgentLoop contract (LoopInput -> LoopOutput).
    // When plan is provided, executes it directly. When absent, calls the
    // planner (LLM) to produce a plan from the instruction + context.
    if (req.method === "POST" && path === "/api/turn") {
      const body = asRecord(await readBody(req));
      if (typeof body["context"] !== "object" || typeof body["instruction"] !== "string") {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Body needs { context, instruction, plan? }.", next: "Send a LoopInput object." } });
        return true;
      }
      // If plan is provided, execute directly (existing path — pills, explicit plans).
      if (typeof body["plan"] === "object" && body["plan"] !== null) {
        const output = loop.runTurn(body as unknown as Parameters<AgentLoop["runTurn"]>[0]);
        sendJson(res, 200, output);
        return true;
      }
      // No plan — route through the planner (free text → LLM → plan → execute).
      try {
        const assembled = assembleContext(
          {
            prefs,
            counts: () => ({
              tasksOpen: tasks.listTasks().filter((t) => t.status !== "done").length,
              eventsToday: calendar
                .listEvents({ from: new Date().toISOString().slice(0, 10) + "T00:00:00", to: new Date().toISOString().slice(0, 10) + "T23:59:59" })
                .length,
              notes: notes.listNotes().length,
            }),
          },
          body["context"] as Parameters<typeof assembleContext>[1],
        );
        const plannerCtx: PlannerContext = {
          surface: assembled.surface,
          scopeIds: assembled.scopeIds,
          selectedId: assembled.selectedId,
          filters: assembled.filters,
          preferences: assembled.preferences,
          ambient: assembled.ambient,
        };
        const result = await planTurn(providers, plannerCtx, body["instruction"] as string);
        if (result.error) {
          sendJson(res, 200, {
            status: "failed",
            sessionId: body["sessionId"] ?? "unknown",
            tier: null,
            ui: "message",
            proposals: [],
            planFingerprint: null,
            approvalTemplate: null,
            verifications: [],
            summary: `${result.error.message} ${result.error.next}`,
            preferencesUsed: [],
            groupId: null,
            stepRecords: [],
            error: { code: result.error.code, message: result.error.message, next: result.error.next },
          });
          return true;
        }
        if (!result.plan || result.plan.steps.length === 0) {
          // Conversational response (no tools needed) — return as a message.
          if (result.textResponse) {
            sendJson(res, 200, {
              status: "ok",
              sessionId: body["sessionId"] ?? "unknown",
              tier: "read",
              ui: "message",
              proposals: [],
              planFingerprint: null,
              approvalTemplate: null,
              verifications: [],
              summary: result.textResponse,
              preferencesUsed: [],
              groupId: null,
              stepRecords: [],
              error: null,
            });
            return true;
          }
          sendJson(res, 200, {
            status: "empty",
            sessionId: body["sessionId"] ?? "unknown",
            tier: "read",
            ui: "message",
            proposals: [],
            planFingerprint: null,
            approvalTemplate: null,
            verifications: [],
            summary: "The planner couldn't determine what to do with that request. Try rephrasing or use one of the quick actions.",
            preferencesUsed: [],
            groupId: null,
            stepRecords: [],
            error: null,
          });
          return true;
        }
        // Execute the planned steps through the normal loop.
        const output = loop.runTurn({
          context: body["context"] as Parameters<AgentLoop["runTurn"]>[0]["context"],
          instruction: body["instruction"] as string,
          plan: result.plan,
          sessionId: body["sessionId"] as string | undefined,
        });
        sendJson(res, 200, output);
        return true;
      } catch (err) {
        const shape = err instanceof FlowOSError
          ? { code: err.code, message: err.message, next: err.next }
          : { code: "PROVIDER_UNREACHABLE", message: `Planner failed: ${err instanceof Error ? err.message : String(err)}`, next: "Check your provider configuration and retry." };
        sendJson(res, 200, {
          status: "failed",
          sessionId: body["sessionId"] ?? "unknown",
          tier: null,
          ui: "message",
          proposals: [],
          planFingerprint: null,
          approvalTemplate: null,
          verifications: [],
          summary: `${shape.message} ${shape.next}`,
          preferencesUsed: [],
          groupId: null,
          stepRecords: [],
          error: shape,
        });
        return true;
      }
    }
    // GET /api/history?sessionId= — backend-owned thread (docs/09 §8).
    if (req.method === "GET" && path === "/api/history") {
      const sessionId = url.searchParams.get("sessionId");
      if (!sessionId) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Missing sessionId.", next: "Pass ?sessionId=…." } });
        return true;
      }
      sendJson(res, 200, { sessionId, turns: loop.getHistory(sessionId) ?? [] });
      return true;
    }
    // GET /api/preferences — explicit memory facts (display-safe; statements are user data).
    if (req.method === "GET" && path === "/api/preferences") {
      sendJson(res, 200, { preferences: prefs.listPreferences() });
      return true;
    }
    // PATCH /api/preferences/:id — edit preference statement.
    if (req.method === "PATCH" && path.startsWith("/api/preferences/")) {
      const id = decodeURIComponent(path.slice("/api/preferences/".length));
      const body = asRecord(await readBody(req));
      const statement = body["statement"];
      if (typeof statement !== "string" || !statement.trim()) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Statement cannot be empty.", next: "Pass { statement: '...' }." } });
        return true;
      }
      try {
        const updated = prefs.updatePreference(id, statement.trim(), { actor: "manual" });
        sendJson(res, 200, { preference: updated });
      } catch (err) {
        if (err instanceof FlowOSError) sendFlowError(res, err);
        else throw err;
      }
      return true;
    }
    // DELETE /api/preferences/:id — delete preference.
    if (req.method === "DELETE" && path.startsWith("/api/preferences/")) {
      const id = decodeURIComponent(path.slice("/api/preferences/".length));
      try {
        sendJson(res, 200, prefs.deleteLearnedPreference(id, { actor: "manual" }));
      } catch (err) {
        if (err instanceof FlowOSError) sendFlowError(res, err);
        else throw err;
      }
      return true;
    }
    // GET /api/permissions — list all permission overrides (doc-11 §4).
    if (req.method === "GET" && path === "/api/permissions") {
      sendJson(res, 200, { overrides: perms.listOverrides() });
      return true;
    }
    // PUT /api/permissions — set or clear a permission override.
    if (req.method === "PUT" && path === "/api/permissions") {
      const body = asRecord(await readBody(req));
      const toolName = body["toolName"];
      const overrideTier = body["overrideTier"];
      if (typeof toolName !== "string") {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Body needs { toolName, overrideTier? }.", next: "Pass a valid tool name." } });
        return true;
      }
      // null/undefined overrideTier means clear the override.
      if (overrideTier === null || overrideTier === undefined) {
        perms.clearOverride(toolName);
        sendJson(res, 200, { cleared: toolName });
        return true;
      }
      if (typeof overrideTier !== "string" || !["auto", "confirm", "blocked"].includes(overrideTier)) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "overrideTier must be auto, confirm, or blocked.", next: "Pass a valid tier." } });
        return true;
      }
      try {
        const override = perms.setOverride(toolName, overrideTier as "auto" | "confirm" | "blocked");
        sendJson(res, 200, { override });
      } catch (err) {
        if (err instanceof FlowOSError) sendFlowError(res, err);
        else throw err;
      }
      return true;
    }
    // POST /api/providers — create/update provider config.
    if (req.method === "POST" && path === "/api/providers") {
      const body = asRecord(await readBody(req));
      const providerType = body["providerType"];
      const model = body["model"];
      if (typeof providerType !== "string" || !PROVIDER_TYPES.includes(providerType as never)) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: `Invalid provider type.`, next: `Use one of: ${PROVIDER_TYPES.join(", ")}.` } });
        return true;
      }
      if (typeof model !== "string" || !model.trim()) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Model cannot be empty.", next: "Enter the model id (e.g. claude-sonnet-4-5)." } });
        return true;
      }
      const input: UpsertProviderInput = {
        providerType: providerType as UpsertProviderInput["providerType"],
        model: model.trim(),
        apiKey: typeof body["apiKey"] === "string" ? body["apiKey"] : null,
        baseUrl: typeof body["baseUrl"] === "string" ? body["baseUrl"] : null,
        ...(typeof body["params"] === "object" && body["params"] !== null ? { params: body["params"] as Record<string, unknown> } : {}),
        isDefault: body["isDefault"] === true,
      };
      const config = providers.upsertConfig(input, { actor: "manual" });
      sendJson(res, 201, { config: providers.listConfigsForDisplay().find((c) => c.id === config.id) ?? config });
      return true;
    }
    // DELETE /api/providers/:id — delete provider config.
    if (req.method === "DELETE" && path.startsWith("/api/providers/")) {
      const id = decodeURIComponent(path.slice("/api/providers/".length));
      try {
        sendJson(res, 200, providers.deleteConfig(id, { actor: "manual" }));
      } catch (err) {
        if (err instanceof FlowOSError) sendFlowError(res, err);
        else throw err;
      }
      return true;
    }
    // POST /api/providers/:id/validate — test-call the provider (1-token ping).
    if (req.method === "POST" && path.endsWith("/validate") && path.startsWith("/api/providers/")) {
      const id = decodeURIComponent(path.slice("/api/providers/".length).replace(/\/validate$/, ""));
      try {
        await validateStoredConfig(providers, id);
        sendJson(res, 200, { ok: true, message: "Provider validated successfully." });
      } catch (err) {
        if (err instanceof FlowOSError) {
          sendJson(res, 200, { ok: false, error: { code: err.code, message: err.message, next: err.next } });
        } else {
          const msg = err instanceof Error ? err.message : String(err);
          sendJson(res, 200, { ok: false, error: { code: "PROVIDER_ERROR", message: `Validation failed: ${msg}`, next: "Check your API key and model, then try again." } });
        }
      }
      return true;
    }
    // GET /api/tasks?status=&priority=&tag= — page reads (service layer, no turn).
    if (req.method === "GET" && path === "/api/tasks") {
      sendJson(res, 200, {
        tasks: tasks.listTasks({
          ...(url.searchParams.get("status") ? { status: url.searchParams.get("status") as "todo" | "doing" | "done" } : {}),
          ...(url.searchParams.get("priority") ? { priority: url.searchParams.get("priority") as "low" | "normal" | "urgent" } : {}),
          ...(url.searchParams.get("tag") ? { tag: url.searchParams.get("tag") as string } : {}),
        }),
      });
      return true;
    }
    // POST /api/tasks — manual create (actor manual, same service as tools).
    if (req.method === "POST" && path === "/api/tasks") {
      const task = tasks.createTask(asInput<Parameters<TaskService["createTask"]>[0]>(asRecord(await readBody(req))), { actor: "manual" });
      sendJson(res, 201, { task });
      return true;
    }
    // PATCH /api/tasks/:id — manual update. DELETE /api/tasks/:id — manual delete.
    if ((req.method === "PATCH" || req.method === "DELETE") && path.startsWith("/api/tasks/")) {
      const id = decodeURIComponent(path.slice("/api/tasks/".length));
      if (req.method === "DELETE") {
        sendJson(res, 200, tasks.deleteTask(id, { actor: "manual" }));
      } else {
        const task = tasks.updateTask(id, asRecord(await readBody(req)) as Parameters<TaskService["updateTask"]>[1], { actor: "manual" });
        sendJson(res, 200, { task });
      }
      return true;
    }
    // GET /api/events?from=&to= — page reads (range required).
    if (req.method === "GET" && path === "/api/events") {
      const from = url.searchParams.get("from");
      const to = url.searchParams.get("to");
      if (!from || !to) {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Missing from/to range.", next: "Pass ?from=ISO&to=ISO." } });
        return true;
      }
      sendJson(res, 200, { events: calendar.listEvents({ from, to }) });
      return true;
    }
    // POST /api/events — manual create. PATCH/DELETE /api/events/:id.
    if (req.method === "POST" && path === "/api/events") {
      const event = calendar.createEvent(asInput<Parameters<CalendarService["createEvent"]>[0]>(asRecord(await readBody(req))), { actor: "manual" });
      sendJson(res, 201, { event });
      return true;
    }
    if ((req.method === "PATCH" || req.method === "DELETE") && path.startsWith("/api/events/")) {
      const id = decodeURIComponent(path.slice("/api/events/".length));
      if (req.method === "DELETE") {
        sendJson(res, 200, calendar.deleteEvent(id, { actor: "manual" }));
      } else {
        const event = calendar.updateEvent(id, asRecord(await readBody(req)) as Parameters<CalendarService["updateEvent"]>[1], { actor: "manual" });
        sendJson(res, 200, { event });
      }
      return true;
    }
    // GET /api/notes + /api/notes/:id — page reads.
    if (req.method === "GET" && path === "/api/notes") {
      sendJson(res, 200, { notes: notes.listNotes().map((n) => ({ ...n, content: undefined })) });
      return true;
    }
    // POST /api/notes — manual create. PATCH/DELETE /api/notes/:id.
    if (req.method === "POST" && path === "/api/notes") {
      const note = notes.createNote(asInput<Parameters<NoteService["createNote"]>[0]>(asRecord(await readBody(req))), { actor: "manual" });
      sendJson(res, 201, { note });
      return true;
    }
    if (req.method === "GET" && path.startsWith("/api/notes/")) {
      const id = decodeURIComponent(path.slice("/api/notes/".length));
      const note = notes.getNote(id);
      if (!note) {
        sendJson(res, 404, { ok: false, error: { code: "NOT_FOUND", message: `Note ${id} not found.`, next: "Refresh the list and try again." } });
        return true;
      }
      sendJson(res, 200, { note });
      return true;
    }
    if ((req.method === "PATCH" || req.method === "DELETE") && path.startsWith("/api/notes/")) {
      const id = decodeURIComponent(path.slice("/api/notes/".length));
      if (req.method === "DELETE") {
        sendJson(res, 200, notes.deleteNote(id, { actor: "manual" }));
      } else {
        const note = notes.updateNote(id, asRecord(await readBody(req)) as Parameters<NoteService["updateNote"]>[1], { actor: "manual" });
        sendJson(res, 200, { note });
      }
      return true;
    }
    // GET /api/log?limit= — action history (snapshots contain no secrets: keys are encrypted blobs).
    if (req.method === "GET" && path === "/api/log") {
      const limit = Math.min(Math.max(Number(url.searchParams.get("limit") ?? 50) || 50, 1), 500);
      sendJson(res, 200, { entries: log.listRecent(limit) });
      return true;
    }
    // POST /api/undo { entryId } — real undo for toast/history affordances.
    if (req.method === "POST" && path === "/api/undo") {
      const body = asRecord(await readBody(req));
      if (typeof body["entryId"] !== "string") {
        sendJson(res, 400, { ok: false, error: { code: "VALIDATION", message: "Body needs { entryId }.", next: "Pass the action-log entry id to undo." } });
        return true;
      }
      try {
        const outcome = undo.undoEntry(body["entryId"] as string, "manual");
        sendJson(res, 200, { ok: true, outcome });
      } catch (err) {
        if (err instanceof FlowOSError) {
          sendJson(res, 200, { ok: false, error: { code: err.code, message: err.message, next: err.next } });
        } else {
          sendJson(res, 500, { ok: false, error: { code: "STORE", message: "Undo failed unexpectedly.", next: "Retry; if it persists, check the debug view." } });
        }
      }
      return true;
    }
    return false;
  }

  async function serveStatic(res: ServerResponse, url: URL): Promise<void> {
    let rel = decodeURIComponent(url.pathname);
    if (rel.endsWith("/")) rel += "index.html";
    const file = normalize(join(PUBLIC_DIR, rel));
    if (!file.startsWith(PUBLIC_DIR) || !existsSync(file)) {
      // SPA fallback (non-/api routes render through index.html).
      const index = join(PUBLIC_DIR, "index.html");
      if (existsSync(index)) {
        const text = await readFile(index, "utf8");
        res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
        res.end(text);
        return;
      }
      res.writeHead(404, { "Content-Type": "text/plain" });
      res.end("Not built yet — run npm run build:web first.");
      return;
    }
    const text = await readFile(file);
    res.writeHead(200, { "Content-Type": MIME[extname(file)] ?? "application/octet-stream" });
    res.end(text);
  }

  function handle(req: IncomingMessage, res: ServerResponse): void {
    (async () => {
      try {
        const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
        if (url.pathname.startsWith("/api/")) {
          const handled = await handleApi(req, res, url);
          if (!handled) {
            sendJson(res, 404, { ok: false, error: { code: "NOT_FOUND", message: `Unknown API route ${url.pathname}.`, next: "Use /api/overview, /api/providers, /api/preferences, /api/tasks, /api/events, /api/notes, /api/turn, /api/history, /api/log, /api/undo." } });
          }
          return;
        }
        if (req.method === "GET") {
          await serveStatic(res, url);
          return;
        }
        sendJson(res, 405, { ok: false, error: { code: "VALIDATION", message: "Method not allowed.", next: "Use GET for pages and the documented verbs for /api routes." } });
      } catch (err) {
        if (err instanceof FlowOSError) {
          sendFlowError(res, err);
        } else {
          sendJson(res, 500, { ok: false, error: { code: "STORE", message: "Unexpected server failure.", next: "Retry; check the server logs." } });
        }
      }
    })().catch(() => {
      try {
        sendJson(res, 500, { ok: false, error: { code: "STORE", message: "Unexpected server failure.", next: "Retry; check the server logs." } });
      } catch {
        // headers already sent — nothing left to do
      }
    });
  }

  return { handle, db };
}
