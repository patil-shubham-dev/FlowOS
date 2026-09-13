// Verifier (docs/09 §6, docs/20 failure catalog).
// A tool reporting success is not success: after commit, the verifier
// re-fetches each affected target and compares it against the intent (the
// approved proposal's after-images). A mismatch — lying tool, race, partial
// write — surfaces as VERIFICATION_MISMATCH, never a false success.
// Volatile fields (timestamps, source) are excluded: dry-run previews and
// committed rows legitimately differ there.
import { FlowOSError } from "../security/secrets.js";
import type { CalendarService } from "../services/calendar.js";
import type { NoteService } from "../services/notes.js";
import type { PreferenceService } from "../services/preferences.js";
import type { TaskService } from "../services/tasks.js";
import type { Proposal } from "../tools/executor.js";

export interface VerifierServices {
  tasks: TaskService;
  calendar: CalendarService;
  notes: NoteService;
  prefs: PreferenceService;
}

export interface VerifyDetail {
  id: string;
  ok: boolean;
  problem: string | null;
}

export interface VerifyResult {
  ok: boolean;
  checked: number;
  details: VerifyDetail[];
}

const VOLATILE = new Set(["id", "updatedAt", "createdAt", "source", "lastUsedAt"]);
// Note: `id` is excluded deliberately. Targets are re-fetched BY id (so identity
// is pinned by the lookup), while creation previews carry placeholder dry-run
// ids that never equal committed ids. Comparing ids would fail every create.

/**
 * Service previews wrap images as single-element arrays ([row] / [null]);
 * composite previews carry bare domain objects. Normalize both shapes.
 * Exported for the summary composer, which reads the same pairs.
 */
export function unwrap(image: unknown): unknown {
  if (Array.isArray(image)) return image[0] ?? null;
  return image;
}

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value !== null && typeof value === "object") {
    return `{${Object.keys(value)
      .filter((k) => !VOLATILE.has(k))
      .sort()
      .map((k) => `${JSON.stringify(k)}:${stable((value as Record<string, unknown>)[k])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

function titleOf(image: unknown): string {  if (typeof image === "object" && image !== null && "title" in image && typeof (image as { title: unknown }).title === "string") {
    return (image as { title: string }).title;
  }
  if (typeof image === "object" && image !== null && "statement" in image) {
    return String((image as { statement: unknown }).statement);
  }
  return "(item)";
}

export class Verifier {
  constructor(private readonly svc: VerifierServices) {}

  /**
   * Re-fetch every affected target and compare against the approved intent.
   * Throws VERIFICATION_MISMATCH naming the divergent ids (doc-20: what
   * completed vs what didn't is reported precisely by the caller).
   * @param createdIds real ids from the execution result, in preview order,
   * for creation pairs (propose-time dry-run ids are placeholders).
   */
  verify(proposal: Proposal, executedAt: string, createdIds: string[] = []): VerifyResult {
    if (proposal.tool === "generateSchedule") {
      return this.verifyLinkedExistence(proposal, executedAt);
    }
    let createdIdx = 0;
    const details: VerifyDetail[] = proposal.preview.map((pair, i) => {
      const before = unwrap(pair.before);
      const after = unwrap(pair.after);
      let id: string | undefined;
      if (before === null) {
        id = createdIds[createdIdx];
        createdIdx += 1;
      } else {
        id = proposal.affectedIds[i];
      }
      if (id === undefined) {
        return { id: `(index ${i})`, ok: false, problem: "preview has more pairs than resolved ids" };
      }
      return this.verifyOne(proposal.tool, id, after);
    });
    const bad = details.filter((d) => !d.ok);
    if (bad.length > 0) {
      throw new FlowOSError(
        "VERIFICATION_MISMATCH",
        `Verification failed for ${bad.length}/${details.length} item(s): ${bad.map((d) => `${d.id} (${d.problem})`).join("; ")}. The tool reported success but the stored state does not match intent.`,
        "Treat this as a failure, not a success: inspect the items, and offer to roll back the completed portion.",
      );
    }
    return { ok: true, checked: details.length, details };
  }

  private verifyOne(tool: Proposal["tool"], id: string, expectedAfter: unknown): VerifyDetail {
    const current = this.refetch(tool, id);
    if (expectedAfter === null) {
      // Delete intent: the row must be gone.
      return current === null
        ? { id, ok: true, problem: null }
        : { id, ok: false, problem: `expected "${titleOf(current)}" to be deleted, but it still exists` };
    }
    if (current === null) {
      return { id, ok: false, problem: "expected the item to exist, but it is missing" };
    }
    if (stable(current) !== stable(expectedAfter)) {
      return { id, ok: false, problem: `fields differ from intent for "${titleOf(expectedAfter)}"` };
    }
    return { id, ok: true, problem: null };
  }

  /** generateSchedule affects TASK ids but creates EVENT rows: each task must have a linked event. */
  private verifyLinkedExistence(proposal: Proposal, executedAt: string): VerifyResult {
    const now = new Date();
    const from = new Date(now.getTime() - 366 * 86_400_000).toISOString();
    const to = new Date(now.getTime() + 366 * 86_400_000).toISOString();
    const events = this.svc.calendar.listEvents({ from, to });
    const details: VerifyDetail[] = proposal.affectedIds.map((taskId) => {
      const linked = events.filter((e) => e.linkedTaskId === taskId && e.createdAt >= executedAt);
      return linked.length > 0
        ? { id: taskId, ok: true, problem: null }
        : { id: taskId, ok: false, problem: "no scheduled event was created for this task" };
    });
    const bad = details.filter((d) => !d.ok);
    if (bad.length > 0) {
      throw new FlowOSError(
        "VERIFICATION_MISMATCH",
        `Verification failed: ${bad.length} task(s) have no scheduled event (${bad.map((d) => d.id).join(", ")}).`,
        "Treat this as a failure, not a success: inspect the schedule and re-propose.",
      );
    }
    return { ok: true, checked: details.length, details };
  }

  private refetch(tool: Proposal["tool"], id: string): unknown {
    switch (tool) {
      case "createTask":
      case "updateTask":
      case "deleteTask":
        return this.svc.tasks.getTask(id);
      case "createEvent":
      case "updateEvent":
      case "deleteEvent":
      case "reschedule":
      case "optimizeSchedule":
        return this.svc.calendar.getEvent(id);
      case "createNote":
      case "updateNote":
      case "linkNoteToTask":
        return this.svc.notes.getNote(id);
      case "saveLearnedPreference":
      case "deleteLearnedPreference":
        return this.svc.prefs.getPreference(id);
      default:
        return null;
    }
  }
}