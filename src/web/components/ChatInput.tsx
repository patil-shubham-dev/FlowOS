import { useEffect, useRef, useState, type KeyboardEvent } from "react";

export interface Pill {
  label: string;
  planKey: "today" | "open-tasks" | "demo-task" | "demo-bulk-done" | "demo-delete-done";
  instruction: string;
}

const PILLS: Pill[] = [
  { label: "What do I need today?", planKey: "today", instruction: "What do I need today?" },
  { label: "List open tasks", planKey: "open-tasks", instruction: "List my open tasks" },
  { label: "Create a task", planKey: "demo-task", instruction: "Create a sample task" },
];

export function ChatInput({
  disabled,
  disabledReason,
  thinking,
  providerName,
  onSubmit,
  onPill,
  showPills,
}: {
  disabled: boolean;
  disabledReason: string | null;
  thinking: boolean;
  providerName: string | null;
  onSubmit: (text: string) => void;
  onPill: (pill: Pill) => void;
  showPills: boolean;
}) {
  const [text, setText] = useState("");
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!ref.current) return;
    ref.current.style.height = "auto";
    ref.current.style.height = `${Math.min(ref.current.scrollHeight, 160)}px`;
  }, [text]);

  function send(): void {
    const t = text.trim();
    if (!t || disabled || thinking) return;
    setText("");
    onSubmit(t);
  }

  function onKey(e: KeyboardEvent<HTMLTextAreaElement>): void {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  }

  return (
    <div className="chat-input-wrap">
      {showPills && (
        <div className="chat-pills">
          {PILLS.map((p) => (
            <button key={p.planKey} type="button" className="chat-pill" onClick={() => onPill(p)}>
              {p.label}
            </button>
          ))}
        </div>
      )}
      {providerName && (
        <div className="provider-label">{providerName}</div>
      )}
      <div className="chat-container">
        <textarea
          ref={ref}
          className="chat-textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder={disabled ? (disabledReason ?? "Configure a provider in Settings") : "Ask FlowOS anything\u2026"}
          rows={1}
          disabled={disabled}
          title={disabled ? (disabledReason ?? undefined) : undefined}
        />
        <button
          type="button"
          className="chat-send"
          onClick={send}
          disabled={disabled || thinking || !text.trim()}
          title="Send"
        >
          {thinking ? (
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <rect x="3" y="3" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.75" />
            </svg>
          ) : (
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 12V2M7 2L2.5 6.5M7 2L11.5 6.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}
