// API client: typed wrappers over the FlowOS backend (src/server.ts).
// Mirrors LoopInput/LoopOutput; no planning or classification lives here —
// the client ships explicit plans (suggestion pills) and renders responses.

export type UiHint = "diff-card" | "blocked-card" | "toast" | "inline" | "message";
export type LoopStatus = "executed" | "needs-approval" | "blocked" | "empty" | "failed";

export interface PreviewPair {
  before: unknown;
  after: unknown;
}

export interface Proposal {
  kind: "ready" | "confirm" | "blocked" | "empty";
  tier: "read" | "auto" | "confirm" | "blocked";
  tool: string;
  inputFingerprint: string;
  groupId: string | null;
  preview: PreviewPair[];
  affectedIds: string[];
  message: string | null;
  itemizedAlternative: Proposal | null;
  /** Present only on itemized alternatives: submit verbatim to run the breakdown. */
  executableInput: unknown;
  readResult: unknown;
}

export interface PlanApproval {
  tier: string;
  planFingerprint: string;
  groupId: string;
  steps: Array<{ inputFingerprint: string; affectedIds: string[] }>;
}

export interface StepRecord {
  tool: string;
  kind: string;
  tier: string;
  affectedIds: string[];
  resultSummary: unknown;
  logEntryIds: string[];
}

export interface LoopOutput {
  status: LoopStatus;
  sessionId: string;
  tier: string | null;
  ui: UiHint;
  proposals: Proposal[];
  planFingerprint: string | null;
  approvalTemplate: PlanApproval | null;
  verifications: Array<{ ok: boolean; checked: number }>;
  summary: string;
  preferencesUsed: Array<{ id: string; statement: string }>;
  groupId: string | null;
  stepRecords: StepRecord[];
  error: { code: string; message: string; next: string } | null;
}

export interface PlanStep {
  tool: string;
  input: unknown;
}

export interface TurnInput {
  context: { surface: string; visibleIds?: string[]; filters?: Record<string, unknown>; selectedId?: string | null };
  instruction: string;
  /** Optional: when absent, the backend planner (LLM) produces a plan server-side. */
  plan?: { steps: PlanStep[]; preferencesUsed?: string[] };
  approval?: PlanApproval;
  sessionId?: string;
}

export interface TurnRecord {
  timestamp: string;
  instruction: string;
  surface: string;
  status: string;
  summary: string;
  groupId: string | null;
  errorCode: string | null;
  steps?: Array<{ tool: string; logEntryIds?: string[] }>;
}

export interface Overview {
  date: string;
  counts: { tasksOpen: number; tasksDueToday: number; eventsToday: number; notes: number };
  tasksDueToday: Array<{ id: string; title: string; dueAt: string | null; priority: string; status: string }>;
  upcomingEvents: Array<{ id: string; title: string; startAt: string; endAt: string }>;
}

export interface ProviderInfo {
  configs: Array<{ id: string; providerType: string; model: string; hasApiKey: boolean; isDefault: boolean }>;
  defaultId: string | null;
}

export interface Preference {
  id: string;
  statement: string;
  sourceSessionId: string | null;
  createdAt: string;
  lastUsedAt: string | null;
}

export function getPreferences(): Promise<{ preferences: Preference[] }> {
  return fetch("/api/preferences").then((r) => json<{ preferences: Preference[] }>(r));
}

// Permission overrides (doc-11 §4).
export interface PermissionOverride {
  id: string;
  toolName: string;
  overrideTier: string;
  createdAt: string;
  updatedAt: string;
}

export function getPermissions(): Promise<{ overrides: PermissionOverride[] }> {
  return fetch("/api/permissions").then((r) => json<{ overrides: PermissionOverride[] }>(r));
}

export function putPermission(toolName: string, overrideTier: string | null): Promise<{ override?: PermissionOverride; cleared?: string }> {
  return fetch("/api/permissions", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ toolName, overrideTier }),
  }).then((r) => json<{ override?: PermissionOverride; cleared?: string }>(r));
}

export interface ActionLogEntry {
  id: string;
  timestamp: string;
  actor: string;
  toolOrOperation: string;
  targetIds: string[];
  groupId: string | null;
  undoStatus: string;
  undoReason: string | null;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  }
  return (await res.json()) as T;
}

export function postTurn(input: TurnInput): Promise<LoopOutput> {
  return fetch("/api/turn", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  }).then((r) => json<LoopOutput>(r));
}

export function getHistory(sessionId: string): Promise<{ sessionId: string; turns: TurnRecord[] }> {
  return fetch(`/api/history?sessionId=${encodeURIComponent(sessionId)}`).then((r) => json<{ sessionId: string; turns: TurnRecord[] }>(r));
}

export function getOverview(date?: string): Promise<Overview> {
  const q = date ? `?date=${encodeURIComponent(date)}` : "";
  return fetch(`/api/overview${q}`).then((r) => json<Overview>(r));
}

export function getProviders(): Promise<ProviderInfo> {
  return fetch("/api/providers").then((r) => json<ProviderInfo>(r));
}

export function postUndo(entryId: string): Promise<{ ok: boolean; error?: { code: string; message: string; next: string } }> {
  return fetch("/api/undo", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ entryId }),
  }).then((r) => json<{ ok: boolean; error?: { code: string; message: string; next: string } }>(r));
}

export function getLog(limit = 50): Promise<{ entries: ActionLogEntry[] }> {
  return fetch(`/api/log?limit=${limit}`).then((r) => json<{ entries: ActionLogEntry[] }>(r));
}

export interface TaskRow {
  id: string;
  title: string;
  description: string | null;
  dueAt: string | null;
  priority: "low" | "normal" | "urgent";
  status: "todo" | "doing" | "done";
  tag: string | null;
}

export function getTasks(filter: { status?: string; priority?: string; tag?: string } = {}): Promise<{ tasks: TaskRow[] }> {
  const q = new URLSearchParams(filter as Record<string, string>).toString();
  return fetch(`/api/tasks${q ? `?${q}` : ""}`).then((r) => json<{ tasks: TaskRow[] }>(r));
}

export interface EventRow {
  id: string;
  title: string;
  startAt: string;
  endAt: string;
  isFlexible: boolean;
  linkedTaskId: string | null;
}

export function getEvents(from: string, to: string): Promise<{ events: EventRow[] }> {
  return fetch(`/api/events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`).then((r) => json<{ events: EventRow[] }>(r));
}

export interface NoteRow {
  id: string;
  title: string;
  content?: string;
  tags: string[];
  linkedTaskIds: string[];
  updatedAt: string;
}

export function getNotes(): Promise<{ notes: NoteRow[] }> {
  return fetch("/api/notes").then((r) => json<{ notes: NoteRow[] }>(r));
}

export function getNote(id: string): Promise<{ note: NoteRow & { content: string } }> {
  return fetch(`/api/notes/${encodeURIComponent(id)}`).then((r) => json<{ note: NoteRow & { content: string } }>(r));
}

// Manual writes (actor "manual", same services as agent tools — docs/12 §1).
// Service validation errors surface as thrown Errors carrying message + next.
async function write<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const parsed = (await res.json()) as { ok?: boolean; error?: { message: string; next: string } } & T;
  if (!res.ok || (parsed as { ok?: boolean }).ok === false || parsed.error) {
    const err = (parsed as { error?: { message: string; next: string } }).error;
    throw new Error(err ? `${err.message} ${err.next}` : `HTTP ${res.status}`);
  }
  return parsed;
}

/** Dispatch a custom event after any successful mutation so Home can refresh ambient data. */
export function notifyDataChanged(): void {
  window.dispatchEvent(new CustomEvent("flowos:data-changed"));
}

export function postTask(input: Record<string, unknown>): Promise<{ task: TaskRow }> {
  return write("POST", "/api/tasks", input);
}

export function patchTask(id: string, patch: Record<string, unknown>): Promise<{ task: TaskRow }> {
  return write("PATCH", `/api/tasks/${encodeURIComponent(id)}`, patch);
}

export function deleteTask(id: string): Promise<{ id: string }> {
  return write("DELETE", `/api/tasks/${encodeURIComponent(id)}`);
}

export function postEvent(input: Record<string, unknown>): Promise<{ event: EventRow }> {
  return write("POST", "/api/events", input);
}

export function patchEvent(id: string, patch: Record<string, unknown>): Promise<{ event: EventRow }> {
  return write("PATCH", `/api/events/${encodeURIComponent(id)}`, patch);
}

export function deleteEvent(id: string): Promise<{ id: string }> {
  return write("DELETE", `/api/events/${encodeURIComponent(id)}`);
}

export function postNote(input: Record<string, unknown>): Promise<{ note: NoteRow }> {
  return write("POST", "/api/notes", input);
}

export function patchNote(id: string, patch: Record<string, unknown>): Promise<{ note: NoteRow }> {
  return write("PATCH", `/api/notes/${encodeURIComponent(id)}`, patch);
}

export function deleteNote(id: string): Promise<{ id: string }> {
  return write("DELETE", `/api/notes/${encodeURIComponent(id)}`);
}

/**
 * Shared Undo-target resolution (toast + inline use this exact function —
 * one implementation, no forks). First audit row across the turn's steps;
 * group semantics in the backend revert the whole unit from any member.
 * Returns null for reads/empties/failures (no Undo rendered).
 */
export function resolveUndoEntry(out: LoopOutput): string | null {
  return out.stepRecords.flatMap((s) => s.logEntryIds)[0] ?? null;
}

// Settings: provider CRUD + validate.
export function postProvider(input: { providerType: string; model: string; apiKey?: string | null; baseUrl?: string | null; params?: Record<string, unknown>; isDefault?: boolean }): Promise<{ config: ProviderInfo["configs"][number] }> {
  return write("POST", "/api/providers", input);
}

export function deleteProvider(id: string): Promise<{ id: string }> {
  return write("DELETE", `/api/providers/${encodeURIComponent(id)}`);
}

export function validateProvider(id: string): Promise<{ ok: boolean; message?: string; error?: { code: string; message: string; next: string } }> {
  return write("POST", `/api/providers/${encodeURIComponent(id)}/validate`);
}

// Settings: preference edit/delete.
export function patchPreference(id: string, patch: { statement: string }): Promise<{ preference: Preference }> {
  return write("PATCH", `/api/preferences/${encodeURIComponent(id)}`, patch);
}

export function deletePreference(id: string): Promise<{ id: string }> {
  return write("DELETE", `/api/preferences/${encodeURIComponent(id)}`);
}
