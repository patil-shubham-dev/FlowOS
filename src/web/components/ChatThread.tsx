import { useEffect, useRef } from "react";
import type { LoopOutput } from "../api.js";

export interface LiveMessage {
  role: "user" | "assistant";
  content: string;
  timestamp: string;
}

export type CardDecision = "approved" | "rejected" | "edited" | null;

/** Convert a history turn into display messages. */
export function messageFromTurn(turn: { instruction: string; output?: LoopOutput }): LiveMessage[] {
  const msgs: LiveMessage[] = [];
  if (turn.instruction) {
    msgs.push({ role: "user", content: turn.instruction, timestamp: "" });
  }
  if (turn.output?.summary) {
    msgs.push({ role: "assistant", content: turn.output.summary, timestamp: "" });
  }
  return msgs;
}

/** Convert a turn output into assistant messages. */
export function messageFromOutput(instruction: string, output: LoopOutput): LiveMessage[] {
  const msgs: LiveMessage[] = [];
  if (instruction) {
    msgs.push({ role: "user", content: instruction, timestamp: new Date().toISOString() });
  }
  if (output.summary) {
    msgs.push({ role: "assistant", content: output.summary, timestamp: new Date().toISOString() });
  }
  return msgs;
}

interface Cards {
  decided: unknown[];
  onApprove: (id: string) => void;
  onCancel: (id: string) => void;
  onEditApply: (id: string, input: unknown) => void;
  onReviewItemized: (id: string) => void;
}

export function ChatThread({
  messages,
  thinking,
  onUndo,
  empty,
  cards,
}: {
  messages: LiveMessage[];
  thinking: boolean;
  onUndo: (idx: number) => void;
  empty: { title: string; body: string };
  cards: Cards;
}) {
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, thinking]);

  if (messages.length === 0 && !thinking) {
    return (
      <div className="empty-state">
        <h2>{empty.title}</h2>
        <p>{empty.body}</p>
      </div>
    );
  }

  return (
    <div className="thread">
      {messages.map((msg, i) => (
        <div key={i} className={msg.role === "user" ? "user-msg" : "agent-msg"}>
          {msg.content}
          {msg.role === "assistant" && i === messages.length - 1 && messages[i - 1]?.role === "user" && (
            <button
              type="button"
              className="mini-btn undo-btn"
              onClick={() => onUndo(i)}
            >
              Undo
            </button>
          )}
        </div>
      ))}
      {thinking && (
        <div className="thinking-dots">
          <span />
          <span />
          <span />
        </div>
      )}
      <div ref={endRef} />
    </div>
  );
}
