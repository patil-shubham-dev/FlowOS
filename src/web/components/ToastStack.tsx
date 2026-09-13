import { useEffect, useState } from "react";

// Toasts (design.md §5.6): bottom-left stack for auto-executed actions.
// Undo + View history hit real endpoints. Auto-dismiss with a leaving
// transition; reduced-motion users get the static state change only.

export interface Toast {
  id: string;
  message: string;
  undoEntryId: string | null;
  onUndo: (entryId: string) => Promise<boolean>;
  onHistory: () => void;
}

function ToastRow({ toast, dismiss }: { toast: Toast; dismiss: (id: string) => void }) {
  const [leaving, setLeaving] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  useEffect(() => {
    const t = setTimeout(() => {
      setLeaving(true);
      setTimeout(() => dismiss(toast.id), 200);
    }, 8000);
    return () => clearTimeout(t);
  }, [toast.id, dismiss]);

  async function undo(): Promise<void> {
    if (!toast.undoEntryId) return;
    const ok = await toast.onUndo(toast.undoEntryId);
    setDone(ok ? "Undone." : "Undo failed — see history.");
    setLeaving(true);
    setTimeout(() => dismiss(toast.id), 2500);
  }

  return (
    <div className={`toast${leaving ? " leaving" : ""}`} role="status">
      <span className="msg">{done ?? toast.message}</span>
      {done === null && toast.undoEntryId && (
        <button type="button" onClick={() => void undo()}>
          Undo
        </button>
      )}
      {done === null && (
        <>
          <button
            type="button"
            onClick={() => {
              toast.onHistory();
              dismiss(toast.id);
            }}
          >
            History
          </button>
          <button
            type="button"
            className="toast-close"
            onClick={() => dismiss(toast.id)}
            title="Dismiss"
          >
            &times;
          </button>
        </>
      )}
    </div>
  );
}

export function ToastStack({ toasts, dismiss }: { toasts: Toast[]; dismiss: (id: string) => void }) {
  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((t) => (
        <ToastRow key={t.id} toast={t} dismiss={dismiss} />
      ))}
    </div>
  );
}
