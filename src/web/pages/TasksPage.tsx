import { useCallback, useEffect, useState, type FormEvent } from "react";
import { deleteTask, getTasks, patchTask, postTask, notifyDataChanged, type TaskRow } from "../api.js";
import { ChatThread } from "../components/ChatThread.js";
import { HistoryPanel } from "../components/HistoryPanel.js";
import { ScopedCommandBar } from "../components/ScopedCommandBar.js";
import { ToastStack } from "../components/ToastStack.js";
import { toIso, toLocalInput } from "../components/TopBar.js";
import { useAgentTurn } from "../hooks/useAgentTurn.js";

type StatusFilter = "all" | "todo" | "doing" | "done";

const UNMATCHED = "I can't interpret free text yet — the language planner lands in a later phase. Try one of the suggestions above; each runs a fixed, reviewable plan.";

export interface TaskDraft {
  title: string;
  description: string;
  dueAt: string;
  priority: "low" | "normal" | "urgent";
  status: "todo" | "doing" | "done";
  tag: string;
  estimatedMinutes: string;
}

const EMPTY_DRAFT: TaskDraft = { title: "", description: "", dueAt: "", priority: "normal", status: "todo", tag: "", estimatedMinutes: "" };

function draftFromTask(t: TaskRow): TaskDraft {
  return {
    title: t.title,
    description: "",
    dueAt: toLocalInput(t.dueAt),
    priority: t.priority,
    status: t.status,
    tag: t.tag ?? "",
    estimatedMinutes: "",
  };
}

function draftToInput(d: TaskDraft): Record<string, unknown> {
  const out: Record<string, unknown> = { title: d.title.trim() };
  if (d.description.trim()) out["description"] = d.description.trim();
  const due = toIso(d.dueAt);
  if (due) out["dueAt"] = due;
  out["priority"] = d.priority;
  out["status"] = d.status;
  if (d.tag.trim()) out["tag"] = d.tag.trim();
  if (d.estimatedMinutes.trim() !== "") {
    const n = Number(d.estimatedMinutes);
    if (Number.isFinite(n) && n >= 0) out["estimatedMinutes"] = n;
  }
  return out;
}

function TaskForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: TaskDraft;
  submitLabel: string;
  onSubmit: (input: Record<string, unknown>) => Promise<void>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState<TaskDraft>(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof TaskDraft, v: string): void => setDraft((d) => ({ ...d, [k]: v }));

  async function submit(e: FormEvent): Promise<void> {
    e.preventDefault();
    if (!draft.title.trim()) {
      setError("Title is required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSubmit(draftToInput(draft));
      setDraft(EMPTY_DRAFT);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form-card" onSubmit={(e) => void submit(e)}>
      <div className="form-grid">
        <label>Title<input value={draft.title} onChange={(e) => set("title", e.target.value)} required /></label>
        <label>Due<input type="datetime-local" value={draft.dueAt} onChange={(e) => set("dueAt", e.target.value)} /></label>
        <label>Priority
          <select value={draft.priority} onChange={(e) => set("priority", e.target.value)}>
            <option value="low">low</option><option value="normal">normal</option><option value="urgent">urgent</option>
          </select>
        </label>
        <label>Status
          <select value={draft.status} onChange={(e) => set("status", e.target.value as TaskDraft["status"])}>
            <option value="todo">todo</option><option value="doing">doing</option><option value="done">done</option>
          </select>
        </label>
        <label>Tag<input value={draft.tag} onChange={(e) => set("tag", e.target.value)} placeholder="optional" /></label>
        <label>Est. minutes<input type="number" min={0} value={draft.estimatedMinutes} onChange={(e) => set("estimatedMinutes", e.target.value)} placeholder="optional" /></label>
        <label>Description<textarea value={draft.description} onChange={(e) => set("description", e.target.value)} placeholder="optional" /></label>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={busy}>{submitLabel}</button>
        {onCancel && <button type="button" className="mini-btn" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

export function TasksPage() {
  const [tasks, setTasks] = useState<TaskRow[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [view, setView] = useState<"list" | "board">("list");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const refresh = useCallback(() => {
    getTasks()
      .then((r) => setTasks(r.tasks))
      .catch(() => setTasks([]));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const visible = tasks.filter((t) => statusFilter === "all" || t.status === statusFilter);
  const visibleUrgent = visible.filter((t) => t.priority === "urgent" && t.status !== "done");

  const turn = useAgentTurn({
    sessionKey: "flowos.session.tasks",
    // Sourced from actual page state at submit time — never hardcoded.
    buildContext: () => ({
      surface: "scoped:tasks",
      visibleIds: visible.map((t) => t.id),
      filters: { status: statusFilter },
      selectedId,
    }),
    onExecuted: refresh,
    onHistory: () => setHistoryOpen(true),
  });

  function submitPillPlan(instruction: string, steps: Array<{ tool: string; input: unknown }>): void {
    void turn.runSteps(instruction, steps);
  }

  const selected = tasks.find((t) => t.id === selectedId) ?? null;
  const scopeChip = `Scope: ${visible.length} visible · filter ${statusFilter} · ${selected ? "1 selected" : "none selected"}`;

  function row(t: TaskRow) {
    if (editingId === t.id) {
      return (
        <div key={t.id}>
          <TaskForm
            initial={draftFromTask(t)}
            submitLabel="Save"
            onCancel={() => setEditingId(null)}
            onSubmit={async (input) => {
              await patchTask(t.id, input);
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
        key={t.id}
        className={`task-row${t.id === selectedId ? " selected" : ""}`}
        onClick={() => setSelectedId(t.id === selectedId ? null : t.id)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            setSelectedId(t.id === selectedId ? null : t.id);
          }
        }}
        role="button"
        tabIndex={0}
        aria-pressed={t.id === selectedId}
      >
        <input
          type="checkbox"
          checked={t.status === "done"}
          aria-label={`Mark '${t.title}' ${t.status === "done" ? "not done" : "done"}`}
          onClick={(e) => e.stopPropagation()}
          onChange={() => {
            patchTask(t.id, { status: t.status === "done" ? "todo" : "done" })
              .then(() => { refresh(); notifyDataChanged(); })
              .catch(() => refresh());
          }}
        />
        <span className={`grow${t.status === "done" ? " status-done" : ""}`}>{t.title}</span>
        <span className={`prio prio-${t.priority}`} title={`Priority: ${t.priority}`}>
          {t.priority === "urgent" ? "▲ " : t.priority === "normal" ? "● " : "○ "}{t.priority}
        </span>
        <span className="meta">{t.status}{t.tag ? ` · ${t.tag}` : ""}</span>
        <span className="row-actions">
          <button
            type="button" className="mini-btn"
            onClick={(e) => { e.stopPropagation(); setEditingId(t.id); setSelectedId(t.id); }}
          >
            Edit
          </button>
          <button
            type="button" className="mini-btn"
            onClick={(e) => {
              e.stopPropagation();
              // Plain client confirm: manual deletion is a direct UI action,
              // not an agent plan — doc-11 tiers govern the agent, not this.
              if (window.confirm(`Delete '${t.title}'? This is undoable from Action History.`)) {
                deleteTask(t.id).then(() => {
                  if (selectedId === t.id) setSelectedId(null);
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
  }

  return (
    <main className="page">
      <h1>Tasks</h1>
      <p className="sub">List and board share the same data. Select a row to scope commands to it.</p>
      <div className="filter-row">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as StatusFilter)} aria-label="Filter by status">
          <option value="all">All statuses</option>
          <option value="todo">To do</option>
          <option value="doing">Doing</option>
          <option value="done">Done</option>
        </select>
        <select value={view} onChange={(e) => setView(e.target.value as "list" | "board")} aria-label="View">
          <option value="list">List</option>
          <option value="board">Board</option>
        </select>
        <button type="button" className="mini-btn" onClick={() => setShowAdd((v) => !v)}>
          {showAdd ? "Close form" : "Add task"}
        </button>
      </div>
      {showAdd && (
        <TaskForm
          initial={EMPTY_DRAFT}
          submitLabel="Add task"
          onSubmit={async (input) => {
            await postTask(input);
            setShowAdd(false);
            refresh();
            notifyDataChanged();
          }}
        />
      )}
      {visible.length === 0 ? (
        <div className="card empty-card"><p className="empty-note">No tasks match this filter — add one, or clear the filter.</p></div>
      ) : view === "list" ? (
        <div className="task-list">{visible.map(row)}</div>
      ) : (
        <div className="task-list">
          {(["todo", "doing", "done"] as const).map((s) => (
            <div key={s}>
              <div className="day-head">{s}</div>
              {visible.filter((t) => t.status === s).map(row)}
            </div>
          ))}
        </div>
      )}
      <div className="surface-thread">
        <ChatThread
          messages={turn.messages}
          thinking={turn.thinking}
          onUndo={turn.undoEntry}
          empty={{ title: "No commands yet", body: "Select a row to scope commands to it, or try a suggestion below." }}
          cards={{ decided: turn.decided, onApprove: turn.onApprove, onCancel: turn.onCancel, onEditApply: turn.onEditApply, onReviewItemized: turn.onReviewItemized }}
        />
      </div>
      <ScopedCommandBar
        scopeChip={scopeChip}
        thinking={turn.thinking}
        pills={[
          {
            label: "Mark selected done",
            instruction: `Mark '${selected?.title ?? ""}' done`,
            run: () => {
              if (!selected) return;
              submitPillPlan(`Mark '${selected.title}' done`, [
                { tool: "updateTask", input: { ids: [selected.id], patch: { status: "done" } } },
              ]);
            },
          },
          {
            label: `Mark all visible urgent done (${visibleUrgent.length})`,
            instruction: "Mark all visible urgent tasks done",
            // ids come from page state — the live proof scoped-push is read.
            run: () => {
              submitPillPlan("Mark all visible urgent tasks done", [
                { tool: "updateTask", input: { ids: visibleUrgent.map((t) => t.id), patch: { status: "done" } } },
              ]);
            },
          },
          {
            label: "Show done tasks",
            instruction: "Show done tasks",
            run: () => {
              submitPillPlan("Show done tasks", [{ tool: "searchTasks", input: { status: "done" } }]);
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
