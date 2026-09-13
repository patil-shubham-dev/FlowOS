import { useEffect, useState } from "react";
import { getLog, postUndo, type ActionLogEntry } from "../api.js";

// Minimal Action History panel: real rows, real Undo buttons. Serves the
// toast "History" action now and the Settings surface later.
export function HistoryPanel({ open, onClose, onChanged }: { open: boolean; onClose: () => void; onChanged: () => void }) {
  const [entries, setEntries] = useState<ActionLogEntry[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function refresh(): Promise<void> {
    try {
      const res = await getLog(50);
      setEntries(res.entries);
    } catch {
      setNotice("Couldn't load history. Is the server running?");
    }
  }

  useEffect(() => {
    if (open) {
      setNotice(null);
      void refresh();
    }
  }, [open]);

  if (!open) return null;

  async function undo(id: string): Promise<void> {
    setBusy(id);
    setNotice(null);
    try {
      const res = await postUndo(id);
      if (res.ok) {
        setNotice("Undone.");
        await refresh();
        onChanged();
      } else {
        setNotice(`${res.error?.message ?? "Undo failed."} ${res.error?.next ?? ""}`);
      }
    } catch {
      setNotice("Undo failed — is the server running?");
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="history-overlay" onClick={onClose} aria-hidden="true" />
      <aside className="history-panel" aria-label="Action history">
        <div className="history-panel-header">
          <h2>Action history</h2>
          <button type="button" className="mini-btn history-close" onClick={onClose} title="Close">
            &times;
          </button>
        </div>
        {notice && <p role="status">{notice}</p>}
        {entries.length === 0 ? (
          <p className="empty-note">No agent or manual actions recorded yet.</p>
        ) : (
          entries.map((e) => (
            <div key={e.id} className="log-row">
              <span className="meta">{e.timestamp.slice(0, 16).replace("T", " ")}</span>
              <span className="grow">
                {e.toolOrOperation} · {e.actor}
                {e.undoStatus !== "available" && <span className="meta"> · {e.undoReason ?? e.undoStatus}</span>}
              </span>
              <button
                type="button"
                className="mini-btn"
                disabled={e.undoStatus !== "available" || busy === e.id}
                onClick={() => void undo(e.id)}
                title={e.undoStatus !== "available" ? (e.undoReason ?? e.undoStatus) : "Undo this action"}
              >
                Undo
              </button>
            </div>
          ))
        )}
        <p><button type="button" className="mini-btn" onClick={onClose}>Close</button></p>
      </aside>
    </>
  );
}
