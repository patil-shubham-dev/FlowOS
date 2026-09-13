// NoteService — markdown notes, tags, task links, FTS5 search.
// Verdict session 4: full-text search for MVP (no embeddings/vector store).
import { randomUUID } from "node:crypto";
import type { FlowDatabase } from "../db/database.js";
import type { MutationContext, Note, PreviewResult } from "../model/types.js";
import { DEFAULT_WORKSPACE_ID } from "../model/types.js";
import { FlowOSError, nowIso } from "../security/secrets.js";
import { ActionLogService } from "./actionLog.js";
import { readRawAligned, readRawOne } from "./rows.js";

type Row = Record<string, unknown>;

export interface CreateNoteInput {
  title: string;
  content?: string;
  tags?: string[];
  linkedTaskIds?: string[];
  workspaceId?: string;
}

export interface UpdateNotePatch {
  title?: string;
  content?: string;
  tags?: string[];
  linkedTaskIds?: string[];
}

export interface NoteSearchHit {
  note: Note;
  rank: number;
}

function toNote(row: Row): Note {
  return {
    id: row["id"] as string,
    workspaceId: row["workspace_id"] as string,
    title: row["title"] as string,
    content: row["content"] as string,
    tags: JSON.parse((row["tags"] as string) || "[]") as string[],
    linkedTaskIds: JSON.parse((row["linked_task_ids"] as string) || "[]") as string[],
    createdAt: row["created_at"] as string,
    updatedAt: row["updated_at"] as string,
    source: row["source"] as Note["source"],
  };
}

export class NoteService {
  private readonly log: ActionLogService;

  constructor(private readonly db: FlowDatabase) {
    this.log = new ActionLogService(db);
  }

  getNote(id: string): Note | null {
    const row = readRawOne(this.db, "notes", id);
    return row ? toNote(row) : null;
  }

  listNotes(workspaceId: string = DEFAULT_WORKSPACE_ID): Note[] {
    const rows = this.db
      .prepare(`SELECT * FROM notes WHERE workspace_id = ? ORDER BY updated_at DESC`)
      .all(workspaceId) as Row[];
    return rows.map(toNote);
  }

  createNote(input: CreateNoteInput, ctx: MutationContext): Note {
    if (typeof input.title !== "string" || !input.title.trim()) {
      throw new FlowOSError("VALIDATION", "Note title cannot be empty.", "Give the note a short title and try again.");
    }
    const title = input.title.trim();
    const linkedTaskIds = input.linkedTaskIds ?? [];
    this.requireTasks(linkedTaskIds);
    const now = nowIso();
    const note: Note = {
      id: randomUUID(),
      workspaceId: input.workspaceId ?? DEFAULT_WORKSPACE_ID,
      title,
      content: input.content ?? "",
      tags: input.tags ?? [],
      linkedTaskIds,
      createdAt: now,
      updatedAt: now,
      source: ctx.actor,
    };
    if (ctx.dryRun) {
      return note;
    }
    this.db
      .prepare(
        `INSERT INTO notes
         (id, workspace_id, title, content, tags, linked_task_ids,
          created_at, updated_at, source)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(
        note.id, note.workspaceId, note.title, note.content,
        JSON.stringify(note.tags), JSON.stringify(note.linkedTaskIds),
        note.createdAt, note.updatedAt, note.source,
      );
    const after = readRawAligned(this.db, "notes", [note.id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "createNote",
      targetType: "note",
      targetIds: [note.id],
      beforeState: [null],
      afterState: after,
      groupId: ctx.groupId,
    });
    return note;
  }

  updateNote(id: string, patch: UpdateNotePatch, ctx: MutationContext): Note {
    const existing = this.getNote(id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Note ${id} not found.`, "Refresh the notes list — it may have been deleted already.");
    }
    const title = patch.title === undefined ? existing.title : patch.title.trim();
    if (!title) {
      throw new FlowOSError("VALIDATION", "Note title cannot be empty.", "Give the note a short title and try again.");
    }
    const linkedTaskIds = patch.linkedTaskIds ?? existing.linkedTaskIds;
    this.requireTasks(linkedTaskIds);
    const updated: Note = {
      ...existing,
      title,
      content: patch.content ?? existing.content,
      tags: patch.tags ?? existing.tags,
      linkedTaskIds,
      updatedAt: nowIso(),
      source: ctx.actor,
    };
    const before = readRawAligned(this.db, "notes", [id]);
    if (ctx.dryRun) {
      return updated;
    }
    this.db
      .prepare(
        `UPDATE notes SET title = ?, content = ?, tags = ?,
         linked_task_ids = ?, updated_at = ?, source = ? WHERE id = ?`,
      )
      .run(
        updated.title, updated.content, JSON.stringify(updated.tags),
        JSON.stringify(updated.linkedTaskIds), updated.updatedAt, updated.source, id,
      );
    const after = readRawAligned(this.db, "notes", [id]);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "updateNote",
      targetType: "note",
      targetIds: [id],
      beforeState: before,
      afterState: after,
      groupId: ctx.groupId,
    });
    return updated;
  }

  deleteNote(id: string, ctx: MutationContext): { id: string } {
    const existing = readRawOne(this.db, "notes", id);
    if (!existing) {
      throw new FlowOSError("NOT_FOUND", `Note ${id} not found.`, "Refresh the notes list — it may already be deleted.");
    }
    if (ctx.dryRun) {
      return { id };
    }
    this.db.prepare(`DELETE FROM notes WHERE id = ?`).run(id);
    this.log.record({
      actor: ctx.actor,
      sessionId: ctx.sessionId,
      toolOrOperation: "deleteNote",
      targetType: "note",
      targetIds: [id],
      beforeState: [existing],
      afterState: [null],
      groupId: ctx.groupId,
    });
    return { id };
  }

  previewCreate(input: CreateNoteInput, ctx: MutationContext): PreviewResult<Note> {
    const result = this.createNote(input, { ...ctx, dryRun: true });
    return { committed: false, before: [null], after: [result], result };
  }

  previewUpdate(id: string, patch: UpdateNotePatch, ctx: MutationContext): PreviewResult<Note> {
    const beforeRow = readRawOne(this.db, "notes", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Note ${id} not found.`, "Refresh the notes list — it may have been deleted already.");
    }
    const result = this.updateNote(id, patch, { ...ctx, dryRun: true });
    return { committed: false, before: [beforeRow], after: [result], result };
  }

  previewDelete(id: string, ctx: MutationContext): PreviewResult<{ id: string }> {
    const beforeRow = readRawOne(this.db, "notes", id);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Note ${id} not found.`, "Refresh the notes list — it may already be deleted.");
    }
    return { committed: false, before: [beforeRow], after: [null], result: { id } };
  }

  previewLinkNoteToTask(noteId: string, taskId: string, ctx: MutationContext): PreviewResult<Note> {
    const beforeRow = readRawOne(this.db, "notes", noteId);
    if (!beforeRow) {
      throw new FlowOSError("NOT_FOUND", `Note ${noteId} not found.`, "Refresh the notes list and try again.");
    }
    const result = this.linkNoteToTask(noteId, taskId, { ...ctx, dryRun: true });
    return { committed: false, before: [beforeRow], after: [result], result };
  }

  linkNoteToTask(noteId: string, taskId: string, ctx: MutationContext): Note {
    const note = this.getNote(noteId);
    if (!note) {
      throw new FlowOSError("NOT_FOUND", `Note ${noteId} not found.`, "Refresh the notes list and try again.");
    }
    this.requireTasks([taskId]);
    if (note.linkedTaskIds.includes(taskId)) {
      return note; // idempotent-safe (docs/10 §1)
    }
    return this.updateNote(noteId, { linkedTaskIds: [...note.linkedTaskIds, taskId] }, ctx);
  }

  /** FTS5 search with LIKE fallback when the query isn't valid MATCH syntax. */
  searchNotes(query: string, workspaceId: string = DEFAULT_WORKSPACE_ID): NoteSearchHit[] {
    const q = query.trim();
    if (!q) return [];
    try {
      const rows = this.db
        .prepare(
          `SELECT n.*, rank AS fts_rank FROM notes_fts
           JOIN notes n ON n.rowid = notes_fts.rowid
           WHERE notes_fts MATCH ? AND n.workspace_id = ?
           ORDER BY rank`,
        )
        .all(q, workspaceId) as Array<Row & { fts_rank: number }>;
      return rows.map((r) => ({ note: toNote(r), rank: r["fts_rank"] as number }));
    } catch {
      const like = `%${q.replace(/[%_\\]/g, (c) => `\\${c}`)}%`;
      const rows = this.db
        .prepare(
          `SELECT * FROM notes
           WHERE workspace_id = ?
             AND (title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')
           ORDER BY updated_at DESC`,
        )
        .all(workspaceId, like, like, like) as Row[];
      return rows.map((note, i) => ({ note: toNote(note), rank: i }));
    }
  }

  private requireTasks(taskIds: string[]): void {
    for (const taskId of taskIds) {
      const row = readRawOne(this.db, "tasks", taskId);
      if (!row) {
        throw new FlowOSError(
          "VALIDATION",
          `Linked task ${taskId} does not exist — nothing was changed.`,
          "Link to an existing task, or create the task first.",
        );
      }
    }
  }
}
