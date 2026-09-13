import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { deleteEvent, getEvents, getTasks, patchEvent, postEvent, notifyDataChanged, type EventRow, type TaskRow } from "../api.js";
import { ChatThread } from "../components/ChatThread.js";
import { HistoryPanel } from "../components/HistoryPanel.js";
import { ScopedCommandBar } from "../components/ScopedCommandBar.js";
import { ToastStack } from "../components/ToastStack.js";
import { shortTime, toIso, toLocalInput } from "../components/TopBar.js";
import { useAgentTurn } from "../hooks/useAgentTurn.js";

const UNMATCHED = "I can't interpret free text yet — the language planner lands in a later phase. Try one of the suggestions above; each runs a fixed, reviewable plan.";

function dayKey(d: Date): string {
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function addHours(iso: string, hours: number): string {
  return new Date(Date.parse(iso) + hours * 3_600_000).toISOString();
}

interface EventDraft {
  title: string;
  startAt: string;
  endAt: string;
  isFlexible: boolean;
  linkedTaskId: string;
}

const EMPTY_EVENT: EventDraft = { title: "", startAt: "", endAt: "", isFlexible: false, linkedTaskId: "" };

function EventForm({
  initial,
  tasks,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: EventDraft;
  tasks: TaskRow[];
  submitLabel: string;
  onSubmit: (input: Record<string, unknown>) => Promise<void>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState<EventDraft>(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent): Promise<void> {
    e.preventDefault();
    if (!draft.title.trim() || !draft.startAt || !draft.endAt) {
      setError("Title, start, and end are required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        title: draft.title.trim(),
        startAt: toIso(draft.startAt),
        endAt: toIso(draft.endAt),
        isFlexible: draft.isFlexible,
        ...(draft.linkedTaskId ? { linkedTaskId: draft.linkedTaskId } : {}),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form-card" onSubmit={(e) => void submit(e)}>
      <div className="form-grid">
        <label>Title<input value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} required /></label>
        <label>Start<input type="datetime-local" value={draft.startAt} onChange={(e) => setDraft({ ...draft, startAt: e.target.value })} required /></label>
        <label>End<input type="datetime-local" value={draft.endAt} onChange={(e) => setDraft({ ...draft, endAt: e.target.value })} required /></label>
        <label>Linked task
          <select value={draft.linkedTaskId} onChange={(e) => setDraft({ ...draft, linkedTaskId: e.target.value })}>
            <option value="">None</option>
            {tasks.map((t) => (
              <option key={t.id} value={t.id}>{t.title}</option>
            ))}
          </select>
        </label>
      </div>
      <label className="check-line">
        <input type="checkbox" checked={draft.isFlexible} onChange={(e) => setDraft({ ...draft, isFlexible: e.target.checked })} />
        Flexible (the agent may reschedule this without being asked specifically)
      </label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={busy}>{submitLabel}</button>
        {onCancel && <button type="button" className="mini-btn" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

export function CalendarPage() {
  const [anchor, setAnchor] = useState<string>(() => dayKey(new Date()));
  const [view, setView] = useState<"day" | "week">("day");
  const [events, setEvents] = useState<EventRow[]>([]);
  const [allTasks, setAllTasks] = useState<TaskRow[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const range = useMemo(() => {
    const start = `${anchor}T00:00:00`;
    const endDate = new Date(Date.parse(`${anchor}T00:00:00`) + (view === "day" ? 1 : 7) * 86_400_000);
    const end = `${dayKey(endDate)}T00:00:00`;
    return { from: start, to: end };
  }, [anchor, view]);

  const refresh = useCallback(() => {
    getEvents(range.from, range.to)
      .then((r) => setEvents(r.events))
      .catch(() => setEvents([]));
    getTasks()
      .then((r) => setAllTasks(r.tasks))
      .catch(() => setAllTasks([]));
  }, [range]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const turn = useAgentTurn({
    sessionKey: "flowos.session.calendar",
    // Visible range + events on screen + selection, sourced from page state.
    buildContext: () => ({
      surface: "scoped:calendar",
      visibleIds: events.map((e) => e.id),
      filters: { from: range.from, to: range.to },
      selectedId,
    }),
    onExecuted: refresh,
    onHistory: () => setHistoryOpen(true),
  });

  const selected = events.find((e) => e.id === selectedId) ?? null;
  const scopeChip = `Scope: ${events.length} visible · ${view} ${anchor} · ${selected ? "1 selected" : "none selected"}`;

  function shiftSelected(hours: number): void {
    if (!selected) return;
    void turn.runSteps(`Shift '${selected.title}' +${hours}h`, [
      {
        tool: "reschedule",
        input: {
          eventId: selected.id,
          newStartAt: addHours(selected.startAt, hours),
          newEndAt: addHours(selected.endAt, hours),
        },
      },
    ]);
  }

  const days: string[] = useMemo(() => {
    const out: string[] = [];
    const base = Date.parse(`${anchor}T00:00:00`);
    const n = view === "day" ? 1 : 7;
    for (let i = 0; i < n; i++) {
      out.push(dayKey(new Date(base + i * 86_400_000)));
    }
    return out;
  }, [anchor, view]);

  return (
    <main className="page">
      <h1>Calendar</h1>
      <p className="sub">Day and week views. Flexible events may be moved by the agent; fixed ones never shift silently.</p>
      <div className="filter-row">
        <button type="button" className="mini-btn" onClick={() => setAnchor(dayKey(new Date()))}>Today</button>
        <button
          type="button" className="mini-btn"
          onClick={() => setAnchor(dayKey(new Date(Date.parse(`${anchor}T00:00:00`) - 86_400_000)))}
          aria-label="Previous day"
        >
          ←
        </button>
        <button
          type="button" className="mini-btn"
          onClick={() => setAnchor(dayKey(new Date(Date.parse(`${anchor}T00:00:00`) + 86_400_000)))}
          aria-label="Next day"
        >
          →
        </button>
        <select value={view} onChange={(e) => setView(e.target.value as "day" | "week")} aria-label="View">
          <option value="day">Day</option>
          <option value="week">Week</option>
        </select>
        <span className="meta">{anchor}</span>
        <button type="button" className="mini-btn" onClick={() => setShowAdd((v) => !v)}>
          {showAdd ? "Close form" : "Add event"}
        </button>
      </div>
      {showAdd && (
        <EventForm
          initial={{ ...EMPTY_EVENT, startAt: toLocalInput(`${anchor}T09:00:00`), endAt: toLocalInput(`${anchor}T10:00:00`) }}
          tasks={allTasks}
          submitLabel="Add event"
          onSubmit={async (input) => {
            await postEvent(input);
            setShowAdd(false);
            refresh();
            notifyDataChanged();
          }}
        />
      )}
      {events.length === 0 ? (
        <div className="card empty-card"><p className="empty-note">No events in this range — add one, or try “Show conflicts” below.</p></div>
      ) : (
        <div className="event-list">
          {days.map((d) => (
            <div key={d}>
              {view === "week" && <div className="day-head">{d}</div>}
              {events
                .filter((e) => e.startAt.slice(0, 10) <= d && e.endAt.slice(0, 10) >= d)
                .map((e) => {
                  if (editingId === e.id) {
                    return (
                      <div key={e.id}>
                        <EventForm
                          initial={{
                            title: e.title,
                            startAt: toLocalInput(e.startAt),
                            endAt: toLocalInput(e.endAt),
                            isFlexible: e.isFlexible,
                            linkedTaskId: e.linkedTaskId ?? "",
                          }}
                          tasks={allTasks}
                          submitLabel="Save"
                          onCancel={() => setEditingId(null)}
                           onSubmit={async (input) => {
                             await patchEvent(e.id, input);
                             setEditingId(null);
                             refresh();
                             notifyDataChanged();
                           }}
                        />
                      </div>
                    );
                  }
                  return (
                  <div
                    key={e.id}
                    className={`event-row${e.id === selectedId ? " selected" : ""}`}
                    onClick={() => setSelectedId(e.id === selectedId ? null : e.id)}
                    onKeyDown={(ev) => {
                      if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        setSelectedId(e.id === selectedId ? null : e.id);
                      }
                    }}
                    role="button"
                    tabIndex={0}
                    aria-pressed={e.id === selectedId}
                  >
                    <span className="grow">
                      {e.title} <span className="meta">{shortTime(e.startAt)}–{shortTime(e.endAt)}</span>
                    </span>
                    <span className="meta">{e.isFlexible ? "↔ Flexible" : "🔒 Fixed"}</span>
                    <span className="row-actions">
                      <button
                        type="button" className="mini-btn"
                        onClick={(ev) => { ev.stopPropagation(); setEditingId(e.id); setSelectedId(e.id); }}
                      >
                        Edit
                      </button>
                      <button
                        type="button" className="mini-btn"
                        onClick={(ev) => {
                          ev.stopPropagation();
                          if (window.confirm(`Delete '${e.title}'? This is undoable from Action History.`)) {
                            deleteEvent(e.id).then(() => {
                              if (selectedId === e.id) setSelectedId(null);
                              refresh();
                              notifyDataChanged();
                            }).catch(() => refresh());
                          }
                        }}
                      >
                        Delete
                      </button>
                    </span>
                  </div>
                  );
                })}
            </div>
          ))}
        </div>
      )}
      <div className="surface-thread">
        <ChatThread
          messages={turn.messages}
          thinking={turn.thinking}
          onUndo={turn.undoEntry}
          empty={{ title: "No commands yet", body: "Select an event to scope commands to it, or try a suggestion below." }}
          cards={{ decided: turn.decided, onApprove: turn.onApprove, onCancel: turn.onCancel, onEditApply: turn.onEditApply, onReviewItemized: turn.onReviewItemized }}
        />
      </div>
      <ScopedCommandBar
        scopeChip={scopeChip}
        thinking={turn.thinking}
        pills={[
          {
            label: "Shift selected +2h",
            instruction: "Shift selected event +2h",
            // Calendar's genuinely new case: reschedule cascades to flexible
            // conflicts, rendered grouped with single undo (verified live).
            run: () => shiftSelected(2),
          },
          {
            label: "Show conflicts",
            instruction: "Show conflicts in view",
            run: () => {
              void turn.runSteps("Show conflicts in view", [
                { tool: "detectConflicts", input: { from: range.from, to: range.to } },
              ]);
            },
          },
          {
            label: "Find 1h free time",
            instruction: "Find 1h free time in view",
            run: () => {
              void turn.runSteps("Find 1h free time in view", [
                { tool: "findFreeTime", input: { from: range.from, to: range.to, durationMinutes: 60 } },
              ]);
            },
          },
        ]}
        onSubmitText={(text) => turn.pushLocal(text, UNMATCHED)}
      />
      <ToastStack toasts={turn.toasts} dismiss={turn.dismissToast} />
      <HistoryPanel open={historyOpen} onClose={() => setHistoryOpen(false)} onChanged={refresh} />
    </main>
  );
}
