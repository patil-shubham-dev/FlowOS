// Tool definitions (docs/10-agent-tool-specification.md).
// Every tool maps 1:1 to a service-layer method. Each declares its base
// risk tier (the FLOOR — scope can escalate, never de-escalate, docs/11 §2),
// preview support, and inverse strategy. No tool touches storage directly,
// and no filesystem/OS/external-comm tool exists here by design (docs/10 §8).
import type { RiskTier } from "../model/types.js";

export type ToolName =
  | "createTask"
  | "updateTask"
  | "deleteTask"
  | "searchTasks"
  | "createEvent"
  | "updateEvent"
  | "deleteEvent"
  | "findFreeTime"
  | "detectConflicts"
  | "createNote"
  | "updateNote"
  | "searchNotes"
  | "linkNoteToTask"
  | "reschedule"
  | "optimizeSchedule"
  | "generateSchedule"
  | "getWorkspaceContext"
  | "globalSearch"
  | "saveLearnedPreference"
  | "deleteLearnedPreference";

export const ALL_TOOL_NAMES: ToolName[] = [
  "createTask", "updateTask", "deleteTask", "searchTasks",
  "createEvent", "updateEvent", "deleteEvent", "findFreeTime", "detectConflicts",
  "createNote", "updateNote", "searchNotes", "linkNoteToTask",
  "reschedule", "optimizeSchedule", "generateSchedule",
  "getWorkspaceContext", "globalSearch",
  "saveLearnedPreference", "deleteLearnedPreference",
];

export type InverseStrategy = "inverse" | "snapshot";

export interface ToolDefinition {
  name: ToolName;
  description: string;
  /** Base tier = FLOOR. The classifier escalates by resolved scope only. */
  baseTier: RiskTier;
  supportsPreview: boolean;
  inverseStrategy: InverseStrategy;
  mutating: boolean;
}

/**
 * Base tiers transcribed from docs/10 §§2–6 (single-item scope).
 * Composite/scheduling tools sit at confirm floor: inherently multi-item.
 */
export const TOOL_REGISTRY: Record<ToolName, ToolDefinition> = {
  createTask: { name: "createTask", description: "Create a single task.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  updateTask: { name: "updateTask", description: "Update fields on task(s). Auto for one item, confirm for bulk.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  deleteTask: { name: "deleteTask", description: "Delete task(s) by explicit id list. Never auto; filter-based bulk is blocked.", baseTier: "confirm", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  searchTasks: { name: "searchTasks", description: "Query tasks by filter. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  createEvent: { name: "createEvent", description: "Create a single calendar event.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  updateEvent: { name: "updateEvent", description: "Update/move event(s). Auto for one non-conflicting item; confirm when it creates a conflict or cascades.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  deleteEvent: { name: "deleteEvent", description: "Delete event(s) by explicit id list. Never auto.", baseTier: "confirm", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  findFreeTime: { name: "findFreeTime", description: "Query free slots in a range. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  detectConflicts: { name: "detectConflicts", description: "List overlapping events. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  createNote: { name: "createNote", description: "Create a single note.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  updateNote: { name: "updateNote", description: "Edit a note.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  searchNotes: { name: "searchNotes", description: "Full-text search over notes. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  linkNoteToTask: { name: "linkNoteToTask", description: "Associate a note with a task.", baseTier: "auto", supportsPreview: true, inverseStrategy: "inverse", mutating: true },
  reschedule: { name: "reschedule", description: "Move one item and resolve cascading effects. Always confirm.", baseTier: "confirm", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  optimizeSchedule: { name: "optimizeSchedule", description: "Propose a lighter day by deferring flexible events. Always confirm.", baseTier: "confirm", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  generateSchedule: { name: "generateSchedule", description: "Place unscheduled tasks into free time. Always confirm.", baseTier: "confirm", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  getWorkspaceContext: { name: "getWorkspaceContext", description: "Ambient summary for Home pull-based context. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  globalSearch: { name: "globalSearch", description: "Search across tasks/events/notes. Read-only.", baseTier: "read", supportsPreview: false, inverseStrategy: "snapshot", mutating: false },
  saveLearnedPreference: { name: "saveLearnedPreference", description: "Write one explicit fact to the visible preference store. Always audit-surfaced.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
  deleteLearnedPreference: { name: "deleteLearnedPreference", description: "Remove one preference fact.", baseTier: "auto", supportsPreview: true, inverseStrategy: "snapshot", mutating: true },
};

export function getToolDefinition(name: ToolName): ToolDefinition {
  return TOOL_REGISTRY[name];
}
