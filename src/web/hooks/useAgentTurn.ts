import { useCallback, useEffect, useRef, useState } from "react";
import {
  getHistory,
  postTurn,
  postUndo,
  resolveUndoEntry,
  type LoopOutput,
  type PlanApproval,
} from "../api.js";
import { messageFromOutput, messageFromTurn, type CardDecision, type LiveMessage } from "../components/ChatThread.js";
import type { Toast } from "../components/ToastStack.js";

export interface TurnContext {
  surface: string;
  visibleIds?: string[];
  filters?: Record<string, unknown>;
  selectedId?: string | null;
}

export interface PlanSteps {
  steps: Array<{ tool: string; input: unknown }>;
}

// Shared orchestration for every surface (Home + scoped pages): session
// continuity, history load, turn submission, the full approval round trip,
// toasts, and the status+ui discrimination. Pages supply context + pills;
// ALL rendering goes through the Phase 1 components unchanged.
export function useAgentTurn(options: {
  sessionKey: string;
  buildContext: () => TurnContext;
  onExecuted: () => void;
  onHistory: () => void;
}) {
  const [sessionId] = useState<string>(() => {
    const existing = localStorage.getItem(options.sessionKey);
    if (existing) return existing;
    const id = crypto.randomUUID();
    localStorage.setItem(options.sessionKey, id);
    return id;
  });
  const [messages, setMessages] = useState<LiveMessage[]>([]);
  const [thinking, setThinking] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pending, setPending] = useState<Record<string, { instruction: string; steps: Array<{ tool: string; input: unknown }> }>>({});
  const [decided, setDecided] = useState<Record<string, CardDecision>>({});
  const counter = useRef(0);
  const buildContext = options.buildContext;
  const onExecuted = options.onExecuted;
  const onHistory = options.onHistory;

  useEffect(() => {
    getHistory(sessionId)
      .then((h) => setMessages(h.turns.flatMap((t) => messageFromTurn(t))))
      .catch(() => setMessages([]));
  }, [sessionId]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const undoEntry = useCallback(
    async (entryId: string): Promise<boolean> => {
      try {
        const res = await postUndo(entryId);
        if (res.ok) onExecuted();
        return res.ok;
      } catch {
        return false;
      }
    },
    [onExecuted],
  );

  function handleOutput(
    instruction: string,
    out: LoopOutput,
    sentSteps: Array<{ tool: string; input: unknown }> | null,
  ): void {
    if (out.status === "needs-approval" && out.planFingerprint) {
      const fp = out.planFingerprint;
      if (sentSteps) {
        setPending((prev) => ({ ...prev, [fp]: { instruction, steps: sentSteps } }));
      }
      setMessages((prev) => [
        ...prev,
        { id: `u-${Date.now()}`, role: "user", text: instruction },
        {
          id: `a-${Date.now()}`, role: "agent", text: out.summary,
          card: {
            kind: "diff", fingerprint: fp, proposals: out.proposals,
            template: out.approvalTemplate, heldSteps: sentSteps, instruction,
          },
        },
      ]);
      return;
    }
    if (out.status === "blocked" && out.planFingerprint) {
      const fp = out.planFingerprint;
      setMessages((prev) => [
        ...prev,
        { id: `u-${Date.now()}`, role: "user", text: instruction },
        {
          id: `a-${Date.now()}`, role: "agent", text: "",
          card: {
            kind: "blocked", fingerprint: fp, proposals: out.proposals,
            template: null, heldSteps: null, instruction,
          },
        },
      ]);
      return;
    }
    setMessages((prev) => [...prev, ...messageFromOutput(instruction, out)]);
    if (out.status === "executed" && (out.ui === "toast" || out.ui === "inline")) {
      onExecuted();
    }
    if (out.status === "executed" && out.ui === "toast") {
      const entryId = resolveUndoEntry(out);
      counter.current += 1;
      const id = `toast-${counter.current}`;
      setToasts((prev) => [
        ...prev,
        { id, message: out.summary, undoEntryId: entryId, onUndo: undoEntry, onHistory },
      ]);
    }
  }

  async function runSteps(
    instruction: string,
    steps?: Array<{ tool: string; input: unknown }>,
    approval?: PlanApproval,
  ): Promise<LoopOutput | null> {
    setThinking(true);
    try {
      const out = await postTurn({
        context: buildContext(),
        instruction,
        ...(steps ? { plan: { steps } } : {}),
        ...(approval ? { approval } : {}),
        sessionId,
      });
      handleOutput(instruction, out, steps ?? null);
      return out;
    } catch {
      setMessages((prev) => [
        ...prev,
        { id: `err-${Date.now()}`, role: "user", text: instruction },
        { id: `err-${Date.now()}-a`, role: "agent", text: "Couldn't reach the server. Is it running?", tone: "error" },
      ]);
      return null;
    } finally {
      setThinking(false);
    }
  }

  function onApprove(fp: string): void {
    const held = pending[fp];
    const cardMsg = messages.flatMap((m) => (m.card && m.card.fingerprint === fp ? [m.card] : []))[0];
    const template = cardMsg?.template;
    if (!held || !template) return;
    setDecided((prev) => ({ ...prev, [fp]: "approved" }));
    void runSteps(held.instruction, held.steps, template);
  }

  function onCancel(fp: string): void {
    // No turn call → zero ActionLog rows by construction.
    setDecided((prev) => ({ ...prev, [fp]: "cancelled" }));
  }

  function onEditApply(fp: string, stepIndex: number, input: unknown): void {
    const held = pending[fp];
    if (!held) return;
    const steps = held.steps.map((s, i) => (i === stepIndex ? { tool: s.tool, input } : s));
    setDecided((prev) => ({ ...prev, [fp]: "superseded" }));
    // Re-submit WITHOUT approval: the backend re-classifies (tier can change).
    void runSteps(held.instruction, steps);
  }

  function onReviewItemized(fp: string): void {
    const found = messages.flatMap((m) =>
      m.card && m.card.fingerprint === fp && m.card.kind === "blocked" ? [m.card] : [],
    )[0];
    const executable = found?.proposals[0]?.itemizedAlternative?.executableInput as { ids: string[] } | null | undefined;
    const tool = found?.proposals[0]?.tool;
    if (!executable || !tool) return;
    setDecided((prev) => ({ ...prev, [fp]: "superseded" }));
    // The echoed executable input re-enters the normal gate (confirm card).
    void runSteps(found.instruction, [{ tool, input: executable }]);
  }

  function pushLocal(userText: string, agentText: string): void {
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, role: "user", text: userText },
      { id: `a-${Date.now()}`, role: "agent", text: agentText },
    ]);
  }

  return {
    sessionId, messages, thinking, toasts, decided,
    runSteps, onApprove, onCancel, onEditApply, onReviewItemized,
    undoEntry, dismissToast, pushLocal,
  };
}
