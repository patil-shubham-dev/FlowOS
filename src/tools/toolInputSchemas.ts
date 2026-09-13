// JSON Schemas for the 20 MVP tools (docs/10). These describe shape to the
// provider; VALUE validation stays in services (defense in depth). Kept
// deliberately loose — required fields + types only.
import type { ToolName } from "./definitions.js";

export const TOOL_INPUT_SCHEMAS: Record<ToolName, Record<string, unknown>> = {
  createTask: {
    type: "object",
    properties: {
      title: { type: "string" },
      description: { type: ["string", "null"] },
      dueAt: { type: ["string", "null"], description: "ISO-8601 date/time" },
      priority: { type: "string", enum: ["low", "normal", "urgent"] },
      status: { type: "string", enum: ["todo", "doing", "done"] },
      tag: { type: ["string", "null"] },
      estimatedMinutes: { type: ["number", "null"] },
    },
    required: ["title"],
    additionalProperties: false,
  },
  updateTask: {
    type: "object",
    properties: {
      ids: { type: "array", items: { type: "string" } },
      filter: { type: "object", description: "Task filter: status, priority, tag, dueFrom, dueTo" },
      patch: { type: "object", description: "Fields to change" },
    },
    required: ["patch"],
    additionalProperties: false,
  },
  deleteTask: {
    type: "object",
    properties: {
      ids: { type: "array", items: { type: "string" } },
      filter: { type: "object", description: "Bulk selector — blocked-by-default, returns an itemized plan instead" },
    },
    additionalProperties: false,
  },
  searchTasks: {
    type: "object",
    properties: {
      status: { type: "string" },
      priority: { type: "string" },
      tag: { type: "string" },
      dueFrom: { type: "string" },
      dueTo: { type: "string" },
    },
    additionalProperties: false,
  },
  createEvent: {
    type: "object",
    properties: {
      title: { type: "string" },
      startAt: { type: "string", description: "ISO-8601" },
      endAt: { type: "string", description: "ISO-8601" },
      isFlexible: { type: "boolean" },
      linkedTaskId: { type: ["string", "null"] },
    },
    required: ["title", "startAt", "endAt"],
    additionalProperties: false,
  },
  updateEvent: {
    type: "object",
    properties: {
      ids: { type: "array", items: { type: "string" } },
      filter: { type: "object", description: "Range: { from, to }" },
      patch: { type: "object", description: "Fields to change" },
    },
    required: ["patch"],
    additionalProperties: false,
  },
  deleteEvent: {
    type: "object",
    properties: {
      ids: { type: "array", items: { type: "string" } },
      filter: { type: "object", description: "Bulk selector — blocked-by-default" },
    },
    additionalProperties: false,
  },
  findFreeTime: {
    type: "object",
    properties: {
      from: { type: "string" },
      to: { type: "string" },
      durationMinutes: { type: "number" },
    },
    required: ["from", "to", "durationMinutes"],
    additionalProperties: false,
  },
  detectConflicts: {
    type: "object",
    properties: { from: { type: "string" }, to: { type: "string" } },
    required: ["from", "to"],
    additionalProperties: false,
  },
  createNote: {
    type: "object",
    properties: {
      title: { type: "string" },
      content: { type: "string" },
      tags: { type: "array", items: { type: "string" } },
      linkedTaskIds: { type: "array", items: { type: "string" } },
    },
    required: ["title"],
    additionalProperties: false,
  },
  updateNote: {
    type: "object",
    properties: {
      id: { type: "string" },
      patch: { type: "object" },
    },
    required: ["id", "patch"],
    additionalProperties: false,
  },
  searchNotes: {
    type: "object",
    properties: { query: { type: "string" } },
    required: ["query"],
    additionalProperties: false,
  },
  linkNoteToTask: {
    type: "object",
    properties: { noteId: { type: "string" }, taskId: { type: "string" } },
    required: ["noteId", "taskId"],
    additionalProperties: false,
  },
  reschedule: {
    type: "object",
    properties: {
      eventId: { type: "string" },
      newStartAt: { type: "string" },
      newEndAt: { type: "string" },
    },
    required: ["eventId", "newStartAt", "newEndAt"],
    additionalProperties: false,
  },
  optimizeSchedule: {
    type: "object",
    properties: { date: { type: "string", description: "YYYY-MM-DD" } },
    required: ["date"],
    additionalProperties: false,
  },
  generateSchedule: {
    type: "object",
    properties: {
      dateFrom: { type: "string" },
      dateTo: { type: "string" },
      taskIds: { type: "array", items: { type: "string" } },
    },
    required: ["dateFrom", "dateTo"],
    additionalProperties: false,
  },
  getWorkspaceContext: {
    type: "object",
    properties: { date: { type: "string", description: "YYYY-MM-DD, default today" } },
    additionalProperties: false,
  },
  globalSearch: {
    type: "object",
    properties: { query: { type: "string" } },
    required: ["query"],
    additionalProperties: false,
  },
  saveLearnedPreference: {
    type: "object",
    properties: { statement: { type: "string" } },
    required: ["statement"],
    additionalProperties: false,
  },
  deleteLearnedPreference: {
    type: "object",
    properties: { id: { type: "string" } },
    required: ["id"],
    additionalProperties: false,
  },
};
