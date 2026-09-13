import { useCallback, useEffect, useState } from "react";
import { getOverview, getProviders, type Overview, type ProviderInfo } from "./api.js";
import { AmbientCards } from "./components/AmbientCards.js";
import { ChatInput, type Pill } from "./components/ChatInput.js";
import { ChatThread, type LiveMessage } from "./components/ChatThread.js";
import { HistoryPanel } from "./components/HistoryPanel.js";
import { ToastStack } from "./components/ToastStack.js";
import { TopBar } from "./components/TopBar.js";
import { useAgentTurn } from "./hooks/useAgentTurn.js";
import { CalendarPage } from "./pages/CalendarPage.js";
import { NotesPage } from "./pages/NotesPage.js";
import { SettingsPage } from "./pages/SettingsPage.js";
import { TasksPage } from "./pages/TasksPage.js";

const todayLocal = (): string => {
  const d = new Date();
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

// Explicit plans bound to pills — quick actions that skip the planner.
// The free-text planner (Phase 5) handles unmatched text server-side.
function planFor(key: "today" | "open-tasks" | "demo-task" | "demo-bulk-done" | "demo-delete-done"): { tool: string; input: unknown } {
  switch (key) {
    case "today":
      return { tool: "getWorkspaceContext", input: { date: todayLocal() } };
    case "open-tasks":
      return { tool: "searchTasks", input: { status: "todo" } };
    case "demo-task":
      return { tool: "createTask", input: { title: "Sample task (demo)" } };
    case "demo-bulk-done":
      return { tool: "updateTask", input: { filter: { status: "todo" }, patch: { status: "done" } } };
    case "demo-delete-done":
      return { tool: "deleteTask", input: { filter: { status: "done" } } };
  }
}

export type Route = "home" | "tasks" | "calendar" | "notes" | "settings";

function routeFromHash(): Route {
  const h = window.location.hash.replace(/^#\/?/, "");
  return h === "tasks" || h === "calendar" || h === "notes" || h === "settings" ? h : "home";
}

function Home() {
  const [providers, setProviders] = useState<ProviderInfo | null>(null);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  const refreshOverview = useCallback(() => {
    getOverview()
      .then((o) => setOverview(o))
      .catch(() => setOverview(null));
  }, []);

  const turn = useAgentTurn({
    sessionKey: "flowos.sessionId",
    buildContext: () => ({ surface: "home" }),
    onExecuted: refreshOverview,
    onHistory: () => setHistoryOpen(true),
  });

  useEffect(() => {
    getProviders().then((p) => setProviders(p)).catch(() => setProviders(null));
    refreshOverview();
  }, [refreshOverview]);

  // Refresh ambient cards whenever any page mutates data (custom event
  // dispatched by api.notifyDataChanged after successful CRUD writes).
  useEffect(() => {
    const onChanged = (): void => refreshOverview();
    window.addEventListener("flowos:data-changed", onChanged);
    return () => window.removeEventListener("flowos:data-changed", onChanged);
  }, [refreshOverview]);

  function onPill(pill: Pill): void {
    void turn.runSteps(pill.instruction, [planFor(pill.planKey)]);
  }

  function onSubmit(text: string): void {
    // Send to backend planner (Phase 5): no plan = LLM plans server-side.
    void turn.runSteps(text);
  }

  const hasProvider = providers !== null && providers.configs.length > 0;
  const providerName = providers?.defaultId
    ? providers.configs.find((c) => c.id === providers.defaultId)
    : providers?.configs[0];
  const displayName = providerName ? `${providerName.providerType} \u00B7 ${providerName.model}` : null;

  return (
    <>
      <main className="home-main">
        <div className="chat-col">
          <ChatThread
            messages={turn.messages}
            thinking={turn.thinking}
            onUndo={turn.undoEntry}
            empty={{ title: "What do you need today?", body: hasProvider ? "Ask below, or try a suggestion \u2014 I\u2019ll pull from your tasks, calendar, and notes." : "Configure an AI provider in Settings to get started." }}
            cards={{ decided: turn.decided, onApprove: turn.onApprove, onCancel: turn.onCancel, onEditApply: turn.onEditApply, onReviewItemized: turn.onReviewItemized }}
          />
          <ChatInput
            disabled={!hasProvider}
            disabledReason={hasProvider ? null : "Configure a provider in Settings to enable the agent."}
            thinking={turn.thinking}
            providerName={displayName}
            showPills={turn.messages.length === 0 && !turn.thinking}
            onSubmit={onSubmit}
            onPill={onPill}
          />
        </div>
        <AmbientCards overview={overview} />
      </main>
      <ToastStack toasts={turn.toasts} dismiss={turn.dismissToast} />
      <HistoryPanel open={historyOpen} onClose={() => setHistoryOpen(false)} onChanged={refreshOverview} />
    </>
  );
}

export function App() {
  const [route, setRoute] = useState<Route>(() => routeFromHash());
  const [providers, setProviders] = useState<ProviderInfo | null>(null);

  useEffect(() => {
    const onHash = (): void => setRoute(routeFromHash());
    window.addEventListener("hashchange", onHash);
    getProviders().then((p) => setProviders(p)).catch(() => setProviders(null));
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  return (
    <>
      <TopBar providers={providers} route={route} />
      {route === "home" && <Home />}
      {route === "tasks" && <TasksPage />}
      {route === "calendar" && <CalendarPage />}
      {route === "notes" && <NotesPage />}
      {route === "settings" && <SettingsPage />}
    </>
  );
}
