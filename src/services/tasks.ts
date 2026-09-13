// TaskService — the same methods serve manual UI edits and agent tool calls
// (docs/14 §1). Every mutation records an action-log entry with snapshots.
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { MutationContext, PreviewResult, Task, TaskPriority, TaskStatus } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";
import { parseIso, readRawAligned, readRawOne } from "./rows.js";

const PRIORITIES: TaskPriority[] = ["low", "normal", "urgent"];
const STATUSES: TaskStatus[] = ["todo", "doing", "done"];

export interface CreateTaskInput {
  title: string;
  description?: string | null;
  dueAt?: string | null;
  priority?: TaskPriority;
  status?: TaskStatus;
  tag?: string | null;
  estimatedMinutes?: number | null;
  workspaceId?: string;
}

export interface UpdateTaskPatch {
  title?: string;
  description?: string | null;
  dueAt?: string | null;
  priority?: TaskPriority;
  status?: TaskStatus;
  tag?: string | null;
  estimatedMinutes?: number | null;
}

export interface TaskFilter {
  status?: TaskStatus;
  priority?: TaskPriority;
  tag?: string;
  dueFrom?: string;
  dueTo?: string;
  workspaceId?: string;
}

type Row = Record<string, unknown>;

function toTask(row: Row): Task {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    title: row["title"] as string,
    description: (row["description"] as string | null) ?? null,
    dueAt: (row["due_at"] as string | null) ?? null,
    priority: row["priority"] as TaskPriority,
    status: row["status"] as TaskStatus,
    tag: (row["tag"] as string | null) ?? null,
    estimatedMinutes: (row["estimated_minutes"] as number | null) ?? null,
    createdAt: row["created_at"] as string,
    updatedAt: row["updated_at"] as string,
    source: row["source"] as Task["source"],
  };
}

export class TaskService {
  private readonly log: ActionLogService;

  constructor(private readonly db: FlowDatabase) {
    this.log = new ActionLogService(db);
  }

  getTask(id: string): Task | null {
    const row = readRawOne(this.db, "tasks", id);
    return row ? toTask(row) : null;
  }

  listTasks(filter: TaskFilter = {}): Task[] {
    const clauses: string[] = ["workspace_id = ?"];
    const params: unknown[] = [filter.workspaceId ?? DEFAULT_WORKSPACE_ID];
    if (filter.status) {
      clauses.push("status = ?");
      params.push(filter.status);
    }
    if (filter.priority) {
      clauses.push("priority = ?");
      params.push(filter.priority);
    }
    if (filter.tag !== undefined) {
      clauses.push("tag = ?");
      params.push(filter.tag);
    }
    if (filter.dueFrom) {
      clauses.push("due_at >= ?");
      params.push(this.checkedBound(filter.dueFrom, "dueFrom"));
    }
    if (filter.dueTo) {
      clauses.push("due_at <= ?");
      params.push(this.checkedBound(filter.dueTo, "dueTo"));
    }
    const rows = this.db
      .prepare(`SELECT * FROM tasks WHERE ${clauses.join(" AND ")} ORDER BY due_at, created_at`)
      .all(...params) as Row[];
    return rows.map(toTask);
  }

  createTask(input: CreateTaskInput, ctx: MutationContext): Task {
    if (typeof input.title !== "string" || !input.title.trim()) {
      throw new FlowOSError("VALIDATION", "Task title cannot be empty.", "Give the task a short title and try again.");
    }
    const title = input.title.trim();
    const dueAt = this.checkedDate(input.dueAt, "dueAt");
    const priority = input.priority ?? "normal";
    const status = input.status ?? "todo";
    this.checkedEnum(priority, PRIORITIES, "priority");
    this.checkedEnum(status, STATUSES, "status");
    if (input.estimatedMinutes !== undefined && input.estimatedMinutes !== null && input.estimatedMinutes < 0) {
      throw new FlowOSError("VALIDATION", "Estimated duration cannot be negative.", "Use a duration in minutes of 0 or more, or leave it unset.");
    }
    const now = nowIso();
    const task: Task = {
      id: randomUUID(),
      workspaceId: input.workspaceId ?? DEFAULT_WORKSPACE_ID,
      title,
      description: input.description ?? null,
      dueAt,
      priority,
      status,
      tag: input.tag ?? null,
      estimatedMinutes: input.estimatedMinutes ?? null,
      createdAt: now,
      updatedAt: now,
      source: ctx.actor,
    };
    if (ctx.dryRun) {
      return task;
    }
    this.db
      .prepare(
        `INSERT INTO tasks
         (id, workspace_id, title, description, due_at, priority, status, tag,
          estimated_minutes, created_at, updated_at, source)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        task.id, task.workspaceId, task.title, task.description, task.dueAt,
        task.priority, task.status, task.tag, task.estimatedMinutes,
        task.createdAt, task.updatedAt, task.source,
      );
    const after = readRawAligned(this.db, "tasks", [task.id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "createTask",
      targetType: "task",
      targetIds: [task.id],
      beforeState: [null],
      afterState: after,
      groupId: ctx.groupId,
    });
    return task;
  }

  previewCreate(input: CreateTaskInput, ctx: MutationContext): PreviewResult<Task> {
    const result = this.createTask(input, { ...ctx, dryRun: true });
    return { committed: false, before: [null], after: [result], result };
  }

  updateTask(id: string, patch: UpdateTaskPatch, ctx: MutationContext): Task {
    const existing = this.getTask(id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Task ${id} not found.`, "Refresh the task list — it may have been deleted already.");
    }
    if (patch.title !== undefined && !patch.title.trim()) {
      throw new FlowOSError("VALIDATION", "Task title cannot be empty.", "Give the task a short title and try again.");
    }
    const dueAt = patch.dueAt === undefined ? existing.dueAt : this.checkedDate(patch.dueAt, "dueAt");
    if (patch.priority !== undefined) this.checkedEnum(patch.priority, PRIORITIES, "priority");
    if (patch.status !== undefined) this.checkedEnum(patch.status, STATUSES, "status");
    if (patch.estimatedMinutes !== undefined && patch.estimatedMinutes !== null && patch.estimatedMinutes < 0) {
      throw new FlowOSError("VALIDATION", "Estimated duration cannot be negative.", "Use a duration in minutes of 0 or more, or leave it unset.");
    }
    const updated: Task = {
      ...existing,
      title: patch.title === undefined ? existing.title : patch.title.trim(),
      description: patch.description === undefined ? existing.description : patch.description,
      dueAt,
      priority: patch.priority ?? existing.priority,
      status: patch.status ?? existing.status,
      tag: patch.tag === undefined ? existing.tag : patch.tag,
      estimatedMinutes: patch.estimatedMinutes === undefined ? existing.estimatedMinutes : patch.estimatedMinutes,
      updatedAt: nowIso(),
      source: ctx.actor,
    };
    const before = readRawAligned(this.db, "tasks", [id]);
    if (ctx.dryRun) {
      return updated;
    }
    this.db
      .prepare(
        `UPDATE tasks SET title = ?, description = ?, due_at = ?, priority = ?,
         status = ?, tag = ?, estimated_minutes = ?, updated_at = ?, source = ?
         WHERE id = ?`,
      )
      .run(
        updated.title, updated.description, updated.dueAt, updated.priority,
        updated.status, updated.tag, updated.estimatedMinutes,
        updated.updatedAt, updated.source, id,
      );
    const after = readRawAligned(this.db, "tasks", [id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "updateTask",
      targetType: "task",
      targetIds: [id],
      beforeState: before,
      afterState: after,
      groupId: ctx.groupId,
    });
    return updated;
  }

  previewUpdate(id: string, patch: UpdateTaskPatch, ctx: MutationContext): PreviewResult<Task> {
    const beforeRow = readRawOne(this.db, "tasks", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Task ${id} not found.`, "Refresh the task list — it may have been deleted already.");
    }
    const result = this.updateTask(id, patch, { ...ctx, dryRun: true });
    return { committed: false, before: [beforeRow], after: [result], result };
  }

  previewDelete(id: string, ctx: MutationContext): PreviewResult<{ id: string }> {
    const beforeRow = readRawOne(this.db, "tasks", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Task ${id} not found.`, "Refresh the task list — it may already be deleted.");
    }
    return { committed: false, before: [beforeRow], after: [null], result: { id } };
  }

  deleteTask(id: string, ctx: MutationContext): { id: string } {
    const existing = readRawOne(this.db, "tasks", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Task ${id} not found.`, "Refresh the task list — it may already be deleted.");
    }
    if (ctx.dryRun) {
      return { id };
    }
    this.db.prepare(`DELETE FROM tasks WHERE id = ?`).run(id);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "deleteTask",
      targetType: "task",
      targetIds: [id],
      beforeState: [existing],
      afterState: [null],
      groupId: ctx.groupId,
    });
    return { id };
  }

  private checkedDate(value: string | null | undefined, field: string): string | null {    if (value === null || value === undefined) return null;
    const parsed = parseIso(value);
    if (!parsed) {
      throw new FlowOSError("VALIDATION", `"${value}" is not a valid date/time for ${field}.`, "Use an ISO-8601 date/time (e.g. 2026-09-13T19:00:00) and try again.");
    }
    return parsed;
  }

  private checkedBound(value: string, field: string): string {
    const parsed = parseIso(value);
    if (!parsed) {
      throw new FlowOSError("VALIDATION", `"${value}" is not a valid date/time for ${field}.`, "Use an ISO-8601 date/time and try again.");
    }
    return parsed;
  }

  private checkedEnum<T extends string>(value: T, allowed: T[], field: string): void {
    if (!allowed.includes(value)) {
      throw new FlowOSError(
        "VALIDATION",
        `"${value}" is not a valid ${field} (expected one of: ${allowed.join(", ")}).`,
        `Pick one of ${allowed.join(", ")} and try again.`,
      );
    }
  }
}
