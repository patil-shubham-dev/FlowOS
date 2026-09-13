// Context assembly (docs/09 §2) — a distinct, inspectable step.
// Two shapes in, one normalized context out:
// - scoped-push (Tasks/Calendar/Notes pages): the frontend serializes what is
//   visible (filters, visible ids, selection) and attaches it. No tool call is
//   needed to know "what's on screen."
// - pull-based (Home): only a lightweight ambient summary ships; the agent
//   pulls the rest through tools. Cheap by default, cross-module by nature.
// Preferences are ALWAYS included (small, cheap — docs/09 §7) so the planner
// can consult them; usage is recorded so the summary can cite them (FR-6.10).
import { FlowOSError } from "../security/secrets.js";
import type { PreferenceService } from "../services/preferences.js";
import type { Preference, Surface } from "../model/types.js";

export type { Surface };

export interface ScopedPushInput {
  surface: Exclude<Surface, "home">;
  /** Ids currently visible post-filter (the resolution basis). */
  visibleIds: string[];
  /** Active filter description (opaque to assembly, recorded for audit). */
  filters?: Record<string, unknown> | undefined;
  /** Explicitly selected item, if any. */
  selectedId?: string | undefined;
}

export interface PullInput {
  surface: "home";
}

export type ContextInput = ScopedPushInput | PullInput;

export interface AssembledContext {
  surface: Surface;
  /** Resolved scope seed: what the user is looking at. */
  scopeIds: string[];
  selectedId: string | null;
  filters: Record<string, unknown> | null;
  /** Explicit memory facts available to the planner (docs/09 §7). */
  preferences: Preference[];
  /** Lightweight ambient counts for Home (pull-based; details via tools). */
  ambient: { tasksOpen: number; eventsToday: number; notes: number } | null;
}

export interface ContextServices {
  prefs: PreferenceService;
  counts: () => { tasksOpen: number; eventsToday: number; notes: number };
}

export function assembleContext(svc: ContextServices, input: ContextInput): AssembledContext {
  if (!input || typeof input.surface !== "string") {
    throw new FlowOSError(
      "MALFORMED_TOOL_CALL",
      "Context assembly needs a surface (home or scoped:*).",
      "Attach { surface, visibleIds?, selectedId? } to the request and retry.",
    );
  }
  const preferences = svc.prefs.listPreferences();
  if (input.surface === "home") {
    return {
      surface: "home",
      scopeIds: [],
      selectedId: null,
      filters: null,
      preferences,
      ambient: svc.counts(),
    };
  }
  if (!["scoped:tasks", "scoped:calendar", "scoped:notes"].includes(input.surface)) {
    throw new FlowOSError(
      "MALFORMED_TOOL_CALL",
      `Unknown surface "${input.surface}".`,
      "Use home, scoped:tasks, scoped:calendar, or scoped:notes.",
    );
  }
  const scoped = input as ScopedPushInput;
  if (!Array.isArray(scoped.visibleIds) || !scoped.visibleIds.every((e) => typeof e === "string")) {
    throw new FlowOSError(
      "MALFORMED_TOOL_CALL",
      "Scoped context needs visibleIds as a string array.",
      "Serialize the visible item ids with the request and retry.",
    );
  }
  return {
    surface: scoped.surface,
    scopeIds: scoped.visibleIds,
    selectedId: scoped.selectedId ?? null,
    filters: scoped.filters ?? null,
    preferences,
    ambient: null,
  };
}
