// Risk classifier tests (docs/11 §2 + locked non-negotiables).
// - One floor assertion PER TOOL at minimum scope: scope can escalate past
//   the floor, never below it (deleteEvent is never auto, at any scope).
// - Tier follows RESOLVED ids, never request phrasing.
import { describe, expect, it } from "vitest";
import { classifyFilterBulkDelete, classifyRisk } from "../src/agent/risk.js";
import { ALL_TOOL_NAMES, TOOL_REGISTRY, type ToolName } from "../src/tools/definitions.js";

const MIN_SCOPE: Record<ToolName, string[]> = {
  createTask: ["new"],
  updateTask: ["t-1"],
  deleteTask: ["t-1"],
  searchTasks: [],
  createEvent: ["new"],
  updateEvent: ["e-1"],
  deleteEvent: ["e-1"],
  findFreeTime: [],
  detectConflicts: [],
  createNote: ["new"],
  updateNote: ["n-1"],
  searchNotes: [],
  linkNoteToTask: ["n-1"],
  reschedule: ["e-1"],
  optimizeSchedule: ["e-1", "e-2"],
  generateSchedule: ["t-1", "t-2"],
  getWorkspaceContext: [],
  globalSearch: [],
  saveLearnedPreference: ["new"],
  deleteLearnedPreference: ["p-1"],
};

describe("risk floors (one per tool, minimum scope)", () => {
  for (const tool of ALL_TOOL_NAMES) {
    it(`${tool} never classifies below its ${TOOL_REGISTRY[tool].baseTier} floor`, () => {
      const c = classifyRisk({ tool, resolvedIds: MIN_SCOPE[tool] });
      expect(c.tier).toBe(TOOL_REGISTRY[tool].baseTier);
    });
  }
});

describe("scope escalation", () => {
  it("auto-base tools escalate to confirm past one resolved item", () => {
    for (const tool of ["updateTask", "updateEvent", "createNote", "updateNote"] as ToolName[]) {
      expect(classifyRisk({ tool, resolvedIds: ["a", "b", "c"] }).tier).toBe("confirm");
    }
  });

  it("confirm-base tools stay confirm at any scope (never de-escalate)", () => {
    for (const tool of ["deleteTask", "deleteEvent", "reschedule", "optimizeSchedule", "generateSchedule"] as ToolName[]) {
      expect(classifyRisk({ tool, resolvedIds: ["only-one"] }).tier).toBe("confirm");
      expect(classifyRisk({ tool, resolvedIds: ["a", "b", "c", "d"] }).tier).toBe("confirm");
    }
  });

  it("deleteEvent is never auto — the floor holds at minimum scope", () => {
    expect(classifyRisk({ tool: "deleteEvent", resolvedIds: ["e-1"] }).tier).not.toBe("auto");
    expect(classifyRisk({ tool: "deleteEvent", resolvedIds: ["e-1"] }).tier).not.toBe("read");
  });

  it("single non-conflicting updateEvent stays auto", () => {
    expect(classifyRisk({ tool: "updateEvent", resolvedIds: ["e-1"] }).tier).toBe("auto");
  });

  it("single updateEvent creating a conflict escalates to confirm", () => {
    expect(
      classifyRisk({ tool: "updateEvent", resolvedIds: ["e-1"], createsConflictOrCascade: true }).tier,
    ).toBe("confirm");
  });

  it("reads ignore scope entirely", () => {
    for (const tool of ["searchTasks", "findFreeTime", "detectConflicts", "searchNotes", "getWorkspaceContext", "globalSearch"] as ToolName[]) {
      expect(classifyRisk({ tool, resolvedIds: ["a", "b", "c"] }).tier).toBe("read");
    }
  });
});

describe("resolved scope, not request text", () => {
  it("'all urgent tasks' resolving to one task is auto; to many is confirm", () => {
    // Same phrasing, different resolved scope -> different tier. The
    // classifier never sees the words, only the ids (by construction: there
    // is no request-text parameter on classifyRisk).
    expect(classifyRisk({ tool: "updateTask", resolvedIds: ["t-9"] }).tier).toBe("auto");
    expect(classifyRisk({ tool: "updateTask", resolvedIds: ["t-1", "t-2", "t-3"] }).tier).toBe("confirm");
  });

  it("filter-based bulk delete is blocked unconditionally", () => {
    expect(classifyFilterBulkDelete("deleteTask").tier).toBe("blocked");
    expect(classifyFilterBulkDelete("deleteEvent").tier).toBe("blocked");
  });
});
