import { useCallback, useEffect, useState, type FormEvent } from "react";
import { deleteNote, getNote, getNotes, getTasks, patchNote, postNote, notifyDataChanged, type NoteRow, type TaskRow } from "../api.js";
import { ChatThread } from "../components/ChatThread.js";
import { HistoryPanel } from "../components/HistoryPanel.js";
import { ScopedCommandBar } from "../components/ScopedCommandBar.js";
import { ToastStack } from "../components/ToastStack.js";
import { useAgentTurn } from "../hooks/useAgentTurn.js";

const UNMATCHED = "I can't interpret free text yet — the language planner lands in a later phase. Try one of the suggestions above; each runs a fixed, reviewable plan.";

interface NoteDraft {
  title: string;
  content: string;
  tags: string;
  linkedTaskIds: string[];
}

const EMPTY_NOTE: NoteDraft = { title: "", content: "", tags: "", linkedTaskIds: [] };

function NoteForm({
  initial,
  tasks,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: NoteDraft;
  tasks: TaskRow[];
  submitLabel: string;
  onSubmit: (input: Record<string, unknown>) => Promise<void>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState<NoteDraft>(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function toggleTask(id: string): void {
    setDraft((d) => ({
      ...d,
      linkedTaskIds: d.linkedTaskIds.includes(id) ? d.linkedTaskIds.filter((t) => t !== id) : [...d.linkedTaskIds, id],
    }));
  }

  async function submit(e: FormEvent): Promise<void> {
    e.preventDefault();
    if (!draft.title.trim()) {
      setError("Title is required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        title: draft.title.trim(),
        content: draft.content,
        tags: draft.tags.split(",").map((t) => t.trim()).filter((t) => t.length > 0),
        linkedTaskIds: draft.linkedTaskIds,
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
        <label>Tags (comma-separated)<input value={draft.tags} onChange={(e) => setDraft({ ...draft, tags: e.target.value })} placeholder="optional" /></label>
        <label>Content (markdown)<textarea value={draft.content} onChange={(e) => setDraft({ ...draft, content: e.target.value })} /></label>
      </div>
      {tasks.length > 0 && (
        <fieldset className="linked-tasks-fieldset">
          <legend className="linked-tasks-legend">Linked tasks</legend>
          {tasks.map((t) => (
            <label key={t.id} className="check-line">
              <input type="checkbox" checked={draft.linkedTaskIds.includes(t.id)} onChange={() => toggleTask(t.id)} />
              {t.title}
            </label>
          ))}
        </fieldset>
      )}
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={busy}>{submitLabel}</button>
        {onCancel && <button type="button" className="mini-btn" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

export function NotesPage() {
  const [notes, setNotes] = useState<NoteRow[]>([]);
  const [allTasks, setAllTasks] = useState<TaskRow[]>([]);
  const [openId, setOpenId] = useState<string | null>(null);
  const [openBody, setOpenBody] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const refresh = useCallback(() => {
    getNotes()
      .then((r) => {
        setNotes(r.notes);
        const stillOpen = openId ? r.notes.some((n) => n.id === openId) : false;
        if (openId && !stillOpen) {
          setOpenId(null);
          setOpenBody(null);
          setEditing(false);
        }
      })
      .catch(() => setNotes([]));
    getTasks()
      .then((r) => setAllTasks(r.tasks))
      .catch(() => setAllTasks([]));
  }, [openId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!openId) {
      setOpenBody(null);
      return;
    }
    getNote(openId)
      .then((r) => setOpenBody(r.note.content))
      .catch(() => setOpenBody(null));
  }, [openId, notes.length]);

  const turn = useAgentTurn({
    sessionKey: "flowos.session.notes",
    // Push-context: the open/visible note(s) travel with the request — the
    // pull path (Home) is never consulted here, so the two can't share state.
    buildContext: () => ({
      surface: "scoped:notes",
      visibleIds: openId ? [openId] : notes.map((n) => n.id),
      selectedId: openId,
    }),
    onExecuted: refresh,
    onHistory: () => setHistoryOpen(true),
  });

  const open = notes.find((n) => n.id === openId) ?? null;
  const scopeChip = open
    ? `Scope: open note '${open.title}' · ${open.tags.length} tag(s)`
    : `Scope: ${notes.length} visible note(s)`;

  function findRelated(): void {
    if (!open) return;
    // Query derived from page state (first tag, else first title word) —
    // deterministic, never guessed.
    const query = open.tags[0] ?? open.title.split(/\s+/)[0] ?? "";
    if (!query) {
      turn.pushLocal("Find related notes", "The open note has no tags or title words to search for.");
      return;
    }
    void turn.runSteps(`Find notes related to '${query}'`, [{ tool: "searchNotes", input: { query } }]);
  }

  return (
    <main className="page">
      <h1>Notes</h1>
      <p className="sub">Markdown notes with tags and task links. Open one to scope commands to it.</p>
      <div className="filter-row">
        <button type="button" className="mini-btn" onClick={() => setShowAdd((v) => !v)}>
          {showAdd ? "Close form" : "Add note"}
        </button>
      </div>
      {showAdd && (
        <NoteForm
          initial={EMPTY_NOTE}
          tasks={allTasks}
          submitLabel="Add note"
          onSubmit={async (input) => {
            await postNote(input);
            setShowAdd(false);
            refresh();
            notifyDataChanged();
          }}
        />
      )}
      {notes.length === 0 ? (
        <div className="card empty-card"><p className="empty-note">No notes yet — capture one, or try “Search notes” below.</p></div>
      ) : (
        <div className="note-list">
          {notes.map((n) => (
            <div
              key={n.id}
              className={`note-row${n.id === openId ? " selected" : ""}`}
              onClick={() => setOpenId(n.id === openId ? null : n.id)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setOpenId(n.id === openId ? null : n.id);
                }
              }}
              role="button"
              tabIndex={0}
              aria-pressed={n.id === openId}
            >
              <span className="grow">{n.title}</span>
              <span className="meta">{n.tags.join(" ")}</span>
            </div>
          ))}
        </div>
      )}
      {open && (
        editing ? (
          <NoteForm
            initial={{
              title: open.title,
              content: openBody ?? "",
              tags: open.tags.join(", "),
              linkedTaskIds: open.linkedTaskIds,
            }}
            tasks={allTasks}
            submitLabel="Save"
            onCancel={() => setEditing(false)}
            onSubmit={async (input) => {
              await patchNote(open.id, input);
              setEditing(false);
              refresh();
              getNote(open.id).then((r) => setOpenBody(r.note.content)).catch(() => setOpenBody(null));
              notifyDataChanged();
            }}
          />
        ) : (
        <div className="note-view" aria-label={`Open note: ${open.title}`}>
          <h2 className="note-view-title">{open.title}</h2>
          <div>{openBody ?? "Loading…"}</div>
          <div className="form-actions">
            <button type="button" className="mini-btn" onClick={() => setEditing(true)}>Edit</button>
            <button
              type="button" className="mini-btn"
              onClick={() => {
                if (window.confirm(`Delete '${open.title}'? This is undoable from Action History.`)) {
                  deleteNote(open.id).then(() => {
                    setOpenId(null);
                    setOpenBody(null);
                    refresh();
                    notifyDataChanged();
                  }).catch(() => refresh());
                }
              }}
            >
              Delete
            </button>
          </div>
        </div>
        )
      )}
      <div className="surface-thread">
        <ChatThread
          messages={turn.messages}
          thinking={turn.thinking}
          onUndo={turn.undoEntry}
          empty={{ title: "No commands yet", body: "Open a note to scope commands to it, or try a suggestion below." }}
          cards={{ decided: turn.decided, onApprove: turn.onApprove, onCancel: turn.onCancel, onEditApply: turn.onEditApply, onReviewItemized: turn.onReviewItemized }}
        />
      </div>
      <ScopedCommandBar
        scopeChip={scopeChip}
        thinking={turn.thinking}
        pills={[
          {
            label: "Find related notes",
            instruction: "Find related notes",
            run: findRelated,
          },
          {
            label: "Search by top tag",
            instruction: "Search by top tag",
            // Query derived from visible list state (most common tag) —
            // deterministic, never guessed.
            run: () => {
              const counts = new Map<string, number>();
              for (const n of notes) {
                for (const t of n.tags) counts.set(t, (counts.get(t) ?? 0) + 1);
              }
              const top = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
              if (!top) {
                turn.pushLocal("Search by top tag", "No tags on any visible note — open one and add tags first.");
                return;
              }
              void turn.runSteps(`Search notes tagged '${top}'`, [{ tool: "searchNotes", input: { query: top } }]);
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
