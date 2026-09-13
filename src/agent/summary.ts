// Summary composer (docs/07 FR-6.7, docs/06 §3, docs/07 FR-6.10).
// Pure function: structured facts in, plain-language report out. Every agent
// action ends with what changed and why; when a learned preference drove a
// decision, the summary names it explicitly ("Since you usually X…").
// The UI renders this string — it never invents content of its own.
import type { Preference } from "../model/types.js";
import { getToolDefinition } from "../tools/definitions.js";
import type { Proposal } from "../tools/executor.js";
import { unwrap } from "./verify.js";
import type { VerifyResult } from "./verify.js";

export interface SummaryInput {
  proposal: Proposal;
  /** True when the action committed (false for empty/blocked/failed). */
  committed: boolean;
  groupId: string | null;
  verification: VerifyResult | null;
  /** Preferences the plan consulted (FR-6.10) — cited by statement. */
  preferencesUsed: Preference[];
  /** Terminal failure, if the turn failed (error already specific per docs/20). */
  failure?: { message: string; next: string } | undefined;
}

function titlesOf(proposal: Proposal, fromBefore: boolean): string[] {
  const out: string[] = [];
  for (const pair of proposal.preview) {
    const image = unwrap(fromBefore ? pair.before : pair.after);
    if (typeof image === "object" && image !== null && "title" in image && typeof (image as { title: unknown }).title === "string") {
      out.push((image as { title: string }).title);
    }
  }
  return out;
}

function nameItems(titles: string[], total: number): string {
  if (titles.length === 0) return total === 1 ? "1 item" : `${total} items`;
  const shown = titles.slice(0, 3).map((t) => `'${t}'`);
  if (total <= 3) {
    return shown.length === 1 ? shown[0] as string : `${shown.slice(0, -1).join(", ")} and ${shown[shown.length - 1]}`;
  }
  return `${shown.join(", ")} and ${total - 3} other${total - 3 === 1 ? "" : "s"}`;
}

/** Short human date: "2026-09-21T09:00:00.000Z" -> "Sep 21, 09:00". */
export function shortDate(iso: string | null): string {
  if (!iso) return "no date";
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!m) return iso;
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const mon = months[Number(m[2]) - 1] ?? m[2];
  return `${mon} ${m[3]}, ${m[4]}:${m[5]}`;
}

export function composeSummary(input: SummaryInput): string {
  const { proposal } = input;

  if (input.failure) {
    return `${input.failure.message} ${input.failure.next}`;
  }

  if (proposal.kind === "empty") {
    return proposal.message ?? "Nothing matched — no changes made.";
  }

  if (proposal.kind === "blocked") {
    const itemized = proposal.itemizedAlternative;
    const base =
      proposal.message ??
      `That request is blocked-by-default (bulk destructive actions never run as one call).`;
    const follow =
      itemized && itemized.affectedIds.length > 0
        ? ` I've listed the ${itemized.affectedIds.length} affected item(s) individually — review that breakdown to proceed item by item.`
        : "";
    return `${base}${follow}`;
  }

  // Committed (ready auto or approved confirm).
  // Reads produce no sentence: the UI renders the readResult data itself, and
  // reads are neither undoable nor footer-worthy.
  if (!getToolDefinition(proposal.tool).mutating) {
    return "";
  }
  const total = proposal.preview.length;
  const verb = verbWord(proposal.tool);
  const opener =
    input.preferencesUsed.length > 0
      ? `Since ${lcfirst((input.preferencesUsed[0] as Preference).statement)}, I ${verb} ` // FR-6.10: name it
      : `I ${verb} `;
  const lines: string[] = [opener + describeChange(proposal, total)];

  if (proposal.message) {
    lines.push(proposal.message); // conflict notes, unmoved-fixed warnings, unplaced items
  }

  if (input.verification && !input.verification.ok) {
    lines.push("Warning: post-execution verification did not fully match intent — review the items above.");
  }

  lines.push(
    input.groupId
      ? "You can undo this as one grouped action from Action History."
      : "You can undo this from Action History.",
  );
  return lines.join(" ");
}

function lcfirst(s: string): string {
  return s.charAt(0).toLowerCase() + s.slice(1);
}

/** Past-tense verb; the opener supplies the subject ("I …" / "Since …, I …"). */
function verbWord(tool: Proposal["tool"]): string {
  switch (tool) {
    case "createTask":
    case "createEvent":
    case "createNote":
      return "created";
    case "updateTask":
    case "updateNote":
      return "updated";
    case "updateEvent":
    case "reschedule":
      return "moved";
    case "deleteTask":
    case "deleteEvent":
      return "deleted";
    case "linkNoteToTask":
      return "linked";
    case "optimizeSchedule":
      return "lightened the schedule for";
    case "generateSchedule":
      return "scheduled";
    case "saveLearnedPreference":
      return "noted the preference";
    case "deleteLearnedPreference":
      return "removed the preference";
    default:
      return "completed";
  }
}

const QUIET_KEYS = new Set(["id", "workspaceid", "createdat", "updatedat", "source", "lastusedat"]);

function normKey(key: string): string {
  return key.replace(/_/g, "").toLowerCase();
}

function shortVal(key: string, value: unknown): string {
  if (value === null || value === undefined) return "unset";
  if (typeof value === "string" && /At$/.test(key)) return shortDate(value);
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

/** Field-level diffs between a before/after pair ("status: todo → done").
 *  Before images are raw store rows (snake_case), afters are domain objects
 *  (camelCase) — keys compare case/underscore-insensitively; display uses
 *  the domain key. Strict === on values would false-positive, so compare
 *  through JSON with null/undefined treated as equal (both render "unset"). */
function pairDiffs(before: unknown, after: unknown): string[] {
  if (typeof before !== "object" || before === null || typeof after !== "object" || after === null) return [];
  const beforeByNorm = new Map<string, unknown>();
  for (const key of Object.keys(before as Record<string, unknown>)) {
    beforeByNorm.set(normKey(key), (before as Record<string, unknown>)[key]);
  }
  const out: string[] = [];
  for (const key of Object.keys(after as Record<string, unknown>)) {
    if (QUIET_KEYS.has(normKey(key))) continue;
    // Raw rows store booleans as 0/1; domain objects as true/false.
    const [b, a] = flexNorm(beforeByNorm.get(normKey(key)) ?? null, (after as Record<string, unknown>)[key] ?? null);
    if (JSON.stringify(b) !== JSON.stringify(a)) {
      out.push(`${key}: ${shortVal(key, b)} → ${shortVal(key, a)}`);
    }
  }
  return out;
}

/** Coerce 0/1 against a boolean counterpart (raw-row vs domain shapes). */
function flexNorm(b: unknown, a: unknown): [unknown, unknown] {
  if (typeof a === "boolean" && (b === 0 || b === 1)) return [b === 1, a];
  if (typeof b === "boolean" && (a === 0 || a === 1)) return [b, a === 1];
  return [b, a];
}

function describeChange(proposal: Proposal, total: number): string {
  const titles = titlesOf(proposal, false);
  const gone = titlesOf(proposal, true);
  switch (proposal.tool) {
    case "createTask":
    case "createEvent":
    case "createNote":
      return `${nameItems(titles, total)}.`;
    case "updateTask":
    case "updateNote":
    case "updateEvent":
      return describeUpdate(proposal, titles, total);
    case "deleteTask":
    case "deleteEvent":
      return `${nameItems(gone, total)}.`;
    case "linkNoteToTask":
      return titles.length > 0 ? `note '${titles[0]}'.` : "the note.";
    case "reschedule": {
      const first = unwrap(proposal.preview[0]?.after) as { startAt?: string; endAt?: string } | null | undefined;
      const dest = first?.startAt ? ` to ${shortDate(first.startAt)}${first.endAt ? `–${shortDate(first.endAt)}` : ""}` : "";
      if (titles.length <= 1) return `${nameItems(titles, total)}${dest}.`;
      return `${titles[0]}${dest} (and shifted ${total - 1} cascading item${total - 1 === 1 ? "" : "s"}: ${titles.slice(1).join(", ")}).`;
    }
    case "optimizeSchedule":
      return `the day — deferred ${nameItems(titles, total)} to free time tomorrow.`;
    case "generateSchedule":
      return `${nameItems(titles, total)} into free time.`;
    case "saveLearnedPreference":
    case "deleteLearnedPreference":
      return "it.";
    default:
      return "";
  }
}

/** Updates name their field diffs; shared diffs collapse across items. */
function describeUpdate(proposal: Proposal, titles: string[], total: number): string {
  const diffs = proposal.preview.map((p) => pairDiffs(unwrap(p.before), unwrap(p.after)));
  const first = diffs[0] ?? [];
  const shared = first.filter((d) => diffs.every((other) => other.includes(d))).slice(0, 2);
  if (total === 1) {
    const detail = first.slice(0, 2).join("; ");
    return detail ? `${nameItems(titles, total)} (${detail}).` : `${nameItems(titles, total)}.`;
  }
  if (shared.length > 0) {
    return `${nameItems(titles, total)} (${shared.join("; ")}).`;
  }
  return `${nameItems(titles, total)}.`;
}
