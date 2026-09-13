import { useState } from "react";

export interface ScopedPill {
  label: string;
  instruction: string;
  run: () => void;
}

export function ScopedCommandBar({
  scopeChip,
  pills,
  thinking,
  onSubmitText,
}: {
  scopeChip: string;
  pills: ScopedPill[];
  thinking: boolean;
  onSubmitText: (text: string) => void;
}) {
  const [text, setText] = useState("");
  const [expanded, setExpanded] = useState(false);

  function submit(): void {
    const value = text.trim();
    if (!value || thinking) return;
    setText("");
    setExpanded(false);
    onSubmitText(value);
  }

  return (
    <div className="cmd-wrap scoped">
      {expanded && (
        <div className="scope-panel">
          <div className="scope-chip" role="note">{scopeChip}</div>
          <div className="pills">
            {pills.map((p) => (
              <button key={p.label} type="button" className="pill" disabled={thinking} onClick={p.run}>
                {p.label}
              </button>
            ))}
          </div>
        </div>
      )}
      <form
        className="cmd-bar"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <span aria-hidden="true" className="cmd-icon">\u2726</span>
        <input
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            if (e.target.value.length > 0) setExpanded(true);
          }}
          onFocus={() => setExpanded(true)}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              setExpanded(false);
              (e.target as HTMLInputElement).blur();
            }
          }}
          placeholder="Tell me what to do\u2026"
          aria-label="Scoped command"
          disabled={thinking}
        />
        <button type="submit" className="btn-primary btn-send" disabled={thinking || text.trim().length === 0}>
          Send
        </button>
      </form>
    </div>
  );
}
