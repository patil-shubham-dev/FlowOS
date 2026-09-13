// Tool input validation (docs/20: malformed tool call).
// Shape checks live here; VALUE rules (date validity, enums, existence)
// live in the services (defense in depth). A malformed call is rejected
// with a correctable error — never executed, never guessed.
import type { TaskFilter } from "../services/tasks.js";
import { FlowOSError } from "../security/secrets.js";
import type { ToolName } from "./definitions.js";

export type Validation<T> = { ok: true; value: T } | { ok: false; error: FlowOSError };

export function malformed(tool: ToolName, detail: string, expected: string): Validation<never> {
  return {
    ok: false,
    error: new FlowOSError(
      "MALFORMED_TOOL_CALL",
      `Tool "${tool}" got invalid arguments: ${detail}. Nothing was executed.`,
      `Fix the arguments (${expected}) and retry — one self-correction attempt is allowed.`,
    ),
  };
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function isStr(v: unknown): v is string {
  return typeof v === "string";
}

function isStrArr(v: unknown): v is string[] {
  return Array.isArray(v) && v.every((e) => typeof e === "string");
}

function opt<T>(v: unknown, check: (u: unknown) => u is T): v is T | undefined {
  return v === undefined || v === null || check(v);
}

/** ids XOR filter — the resolved-id foundation of code-enforced tiers. */
export interface IdOrFilter<F> {
  ids?: string[];
  filter?: F;
}

export function validateIdOrFilter<F>(
  tool: ToolName,
  input: unknown,
  checkFilter: (u: unknown) => u is F,
): Validation<IdOrFilter<F>> {
  if (!isRecord(input)) return malformed(tool, "input must be an object", "{ ids } or { filter } + patch");
  const { ids, filter } = input;
  if (ids !== undefined && filter !== undefined) {
    return malformed(tool, "pass either ids or filter, not both", "one target selector per call");
  }
  if (ids !== undefined) {
    if (!isStrArr(ids) || ids.length === 0) {
      return malformed(tool, "ids must be a non-empty string array", "{ ids: [\"id-1\", …] }");
    }
    return { ok: true, value: { ids } };
  }
  if (filter !== undefined) {
    if (!isRecord(filter) || !checkFilter(filter)) {
      return malformed(tool, "filter has an invalid shape", "a plain filter object for the target type");
    }
    return { ok: true, value: { filter } };
  }
  return malformed(tool, "missing target selector", "pass either ids or filter (one target selector per call)");
}

export function isTaskFilterShape(u: unknown): u is TaskFilter {
  if (!isRecord(u)) return false;
  const { status, priority, tag, dueFrom, dueTo, workspaceId } = u;
  if (status != null && !["todo", "doing", "done"].includes(status as string)) return false;
  if (priority != null && !["low", "normal", "urgent"].includes(priority as string)) return false;
  return (
    opt(tag, isStr) && opt(dueFrom, isStr) && opt(dueTo, isStr) && opt(workspaceId, isStr)
  );
}

export interface EventRangeShape {
  from: string;
  to: string;
}

export function isEventRangeShape(u: unknown): u is EventRangeShape {
  return isRecord(u) && isStr(u["from"]) && isStr(u["to"]);
}

function checkPatch(tool: ToolName, patch: unknown): Validation<Record<string, unknown>> {
  if (!isRecord(patch) || Object.keys(patch).length === 0) {
    return malformed(tool, "patch must be a non-empty object", "{ patch: { status: \"done\", … } }");
  }
  return { ok: true, value: patch };
}

export interface UpdateTasksInput extends IdOrFilter<TaskFilter> {
  patch: Record<string, unknown>;
}

export function validateUpdateTasks(input: unknown): Validation<UpdateTasksInput> {
  const tool: ToolName = "updateTask";
  if (!isRecord(input)) return malformed(tool, "input must be an object", "{ ids|filter, patch }");
  const sel = validateIdOrFilter(tool, input, isTaskFilterShape);
  if (!sel.ok) return sel;
  const patch = checkPatch(tool, input["patch"]);
  if (!patch.ok) return patch;
  return { ok: true, value: { ...sel.value, patch: patch.value } };
}

export interface UpdateEventsInput extends IdOrFilter<EventRangeShape> {
  patch: Record<string, unknown>;
}

export function validateUpdateEvents(input: unknown): Validation<UpdateEventsInput> {
  const tool: ToolName = "updateEvent";
  if (!isRecord(input)) return malformed(tool, "input must be an object", "{ ids|filter(range), patch }");
  const sel = validateIdOrFilter(tool, input, isEventRangeShape);
  if (!sel.ok) return sel;
  const patch = checkPatch(tool, input["patch"]);
  if (!patch.ok) return patch;
  return { ok: true, value: { ...sel.value, patch: patch.value } };
}

export type DeleteTasksInput = IdOrFilter<TaskFilter>;
export type DeleteEventsInput = IdOrFilter<EventRangeShape>;

export function validateDeleteTasks(input: unknown): Validation<DeleteTasksInput> {
  return validateIdOrFilter("deleteTask", input, isTaskFilterShape);
}

export function validateDeleteEvents(input: unknown): Validation<DeleteEventsInput> {
  return validateIdOrFilter("deleteEvent", input, isEventRangeShape);
}

export function validateRecord(tool: ToolName, input: unknown, what: string): Validation<Record<string, unknown>> {
  if (!isRecord(input)) return malformed(tool, "input must be an object", what);
  return { ok: true, value: input };
}
