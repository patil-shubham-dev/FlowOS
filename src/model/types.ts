// FlowOS domain types — mirrors docs/13-data-model.md (conceptual entities).
// Every core entity carries workspaceId (defaulted) so a future
// multi-workspace model never needs a breaking migration (doc 13 §1, doc 08 §7).

export const DEFAULT_WORKSPACE_ID = "default";

/** UI surface a turn originates from (docs/09 §2, §8). */
export type Surface = "home" | "scoped:tasks" | "scoped:calendar" | "scoped:notes";

export type Actor = "agent" | "manual";

export type RiskTier = "read" | "auto" | "confirm" | "blocked";

export type UndoStatus = "available" | "undone" | "no-longer-undoable";

export type TaskStatus = "todo" | "doing" | "done";
export type TaskPriority = "low" | "normal" | "urgent";

export interface Task {
  id: string;
  workspaceId: string;
  title: string;
  description: string | null;
  dueAt: string | null; // ISO-8601
  priority: TaskPriority;
  status: TaskStatus;
  tag: string | null; // free-text tag in MVP (not a Projects module)
  estimatedMinutes: number | null;
  createdAt: string;
  updatedAt: string;
  source: Actor;
}

export interface CalendarEvent {
  id: string;
  workspaceId: string;
  title: string;
  startAt: string; // ISO-8601
  endAt: string; // ISO-8601
  isFlexible: boolean; // agent may reschedule without being asked specifically
  linkedTaskId: string | null;
  createdAt: string;
  updatedAt: string;
  source: Actor;
}

export interface Note {
  id: string;
  workspaceId: string;
  title: string;
  content: string; // markdown
  tags: string[];
  linkedTaskIds: string[];
  createdAt: string;
  updatedAt: string;
  source: Actor;
}

export interface Preference {
  id: string;
  workspaceId: string;
  statement: string; // human-readable fact
  sourceSessionId: string | null;
  createdAt: string;
  lastUsedAt: string | null;
}

export type ProviderType =
  | "openai"
  | "anthropic"
  | "nvidia"
  | "google"
  | "openrouter"
  | "ollama"
  | "lm_studio"
  | "custom_openai_compatible";

export interface ProviderConfig {
  id: string;
  workspaceId: string;
  providerType: ProviderType;
  model: string;
  /** Encrypted at rest (AES-256-GCM). Null for local providers. Never logged. */
  apiKeyEncrypted: string | null;
  baseUrl: string | null;
  params: Record<string, unknown>;
  isDefault: boolean;
}

export interface ActionLogEntry {
  id: string;
  workspaceId: string;
  timestamp: string;
  actor: Actor;
  sessionId: string | null;
  toolOrOperation: string;
  targetType: string;
  targetIds: string[];
  /** Row snapshots before the change (null entries for creations). */
  beforeState: unknown;
  /** Row snapshots after the change (null entries for deletions). */
  afterState: unknown;
  /** Cascading/multi-step actions share one group id (undo as a unit). */
  groupId: string | null;
  undoStatus: UndoStatus;
  undoReason: string | null;
}

export interface AgentMessage {
  role: "user" | "assistant" | "tool";
  content: string;
  toolCalls?: unknown[];
  toolResults?: unknown[];
  providerType?: ProviderType;
  model?: string;
  timestamp: string;
}

export interface AgentSession {
  id: string;
  workspaceId: string;
  surface: "home" | "scoped:tasks" | "scoped:calendar" | "scoped:notes";
  startedAt: string;
  messages: AgentMessage[];
}

/** Mutation context threaded through every service call (doc 12 §1). */
export interface MutationContext {
  actor: Actor;
  sessionId?: string | undefined;
  /** When set, the resulting log entry joins this cascade group. */
  groupId?: string | undefined;
  /** Preview mode: compute before/after without committing (doc 09 §4). */
  dryRun?: boolean | undefined;
}

/**
 * Result of a service-level preview (confirm-gate diff source, docs/09 §4).
 * Convention: `before` holds raw store snapshots (or null for creations);
 * `after` holds the would-be domain objects (or null for deletions).
 * Previews never commit and never write ActionLog rows — the audit log's
 * contract is "this actually happened," so cancelled/proposed-only plans
 * leave zero mutation entries (debug-only traces excepted).
 */
export interface PreviewResult<T> {
  committed: false;
  before: unknown;
  after: unknown;
  result: T;
}
