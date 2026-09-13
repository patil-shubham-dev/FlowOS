// Summary composer tests: the exact strings the UI renders verbatim.
import { describe, expect, it } from "vitest";
import { composeSummary } from "../src/agent/summary.js";
import type { Proposal } from "../src/tools/executor.js";

function proposal(tool: Proposal["tool"], overrides: Partial<Proposal> = {}): Proposal {
  return {
    kind: "ready", tier: "auto", tool,
    inputFingerprint: "fp", groupId: null, preview: [], affectedIds: [],
    message: null, itemizedAlternative: null, executableInput: null, readResult: null,
    ...overrides,
  };
}

describe("summary composer", () => {
  it("reads produce no sentence (UI renders the data)", () => {
    expect(
      composeSummary({ proposal: proposal("getWorkspaceContext", { tier: "read" }), committed: true, groupId: null, verification: null, preferencesUsed: [] }),
    ).toBe("");
    expect(
      composeSummary({ proposal: proposal("searchTasks", { tier: "read" }), committed: true, groupId: null, verification: null, preferencesUsed: [] }),
    ).toBe("");
  });

  it("auto create names the item and offers undo", () => {
    const out = composeSummary({
      proposal: proposal("createTask", {
        preview: [{ before: null, after: { id: "1", title: "Buy milk" } }],
        affectedIds: ["1"],
      }),
      committed: true, groupId: null, verification: { ok: true, checked: 1, details: [] }, preferencesUsed: [],
    });
    expect(out).toBe("I created 'Buy milk'. You can undo this from Action History.");
  });

  it("single update names field diffs", () => {
    const out = composeSummary({
      proposal: proposal("updateTask", {
        preview: [{
          before: { id: "1", title: "t", status: "todo", priority: "urgent" },
          after: { id: "1", title: "t", status: "done", priority: "urgent", updatedAt: "X", source: "agent" },
        }],
        affectedIds: ["1"],
      }),
      committed: true, groupId: null, verification: { ok: true, checked: 1, details: [] }, preferencesUsed: [],
    });
    expect(out).toBe("I updated 't' (status: todo → done). You can undo this from Action History.");
  });

  it("cites a used preference explicitly", () => {
    const out = composeSummary({
      proposal: proposal("reschedule", {
        kind: "confirm", tier: "confirm",
        preview: [{ before: { id: "e", title: "Maths" }, after: { id: "e", title: "Maths", startAt: "2026-09-20T19:00:00.000Z", endAt: "2026-09-20T20:00:00.000Z" } }],
        affectedIds: ["e"],
      }),
      committed: true, groupId: "g", verification: { ok: true, checked: 1, details: [] },
      preferencesUsed: [{ id: "p", workspaceId: "default", statement: "You usually study at 7pm", sourceSessionId: null, createdAt: "x", lastUsedAt: null }],
    });
    expect(out.startsWith("Since you usually study at 7pm, I moved 'Maths' to Sep 20, 19:00")).toBe(true);
    expect(out).toMatch(/grouped action/);
  });

  it("blocked explains with the itemized pointer", () => {
    const out = composeSummary({
      proposal: proposal("deleteTask", {
        kind: "blocked", tier: "blocked", affectedIds: ["a", "b"],
        message: "Blocked reason. Matched 2 task(s).",
        itemizedAlternative: proposal("deleteTask", { kind: "confirm", tier: "confirm", affectedIds: ["a", "b"] }),
      }),
      committed: false, groupId: null, verification: null, preferencesUsed: [],
    });
    expect(out).toMatch(/Blocked reason\./);
    expect(out).toMatch(/item by item/);
  });

  it("empty passes the message through", () => {
    const out = composeSummary({
      proposal: proposal("updateTask", { kind: "empty", message: "No tasks match — nothing to change." }),
      committed: false, groupId: null, verification: null, preferencesUsed: [],
    });
    expect(out).toBe("No tasks match — nothing to change.");
  });
});
