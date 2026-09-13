import type { ProviderInfo } from "../api.js";

/** Format ISO string to short time (e.g. "09:30"). */
export function shortTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

/** Convert Date or ISO string to ISO-8601 without seconds. */
export function toIso(d: Date | string): string {
  const date = typeof d === "string" ? new Date(d) : d;
  return date.toISOString().slice(0, 16);
}

/** Convert ISO string to <input type="datetime-local"> value. */
export function toLocalInput(iso: string): string {
  return iso ? iso.slice(0, 16) : "";
}

export function TopBar({ providers, route }: { providers: ProviderInfo | null; route: string }) {
  const active = (r: string): string => route === r ? "active" : "";
  return (
    <header className="topbar">
      <span className="topbar-brand">FlowOS</span>
      <nav className="topbar-nav">
        <a href="#" className={active("home")}>Home</a>
        <a href="#/tasks" className={active("tasks")}>Tasks</a>
        <a href="#/calendar" className={active("calendar")}>Calendar</a>
        <a href="#/notes" className={active("notes")}>Notes</a>
      </nav>
      <span className="topbar-spacer" />
      {providers && providers.configs.length > 0 && (
        <span className="topbar-status">
          <span className="dot" />
          {(() => {
            const def = providers.defaultId
              ? providers.configs.find((c) => c.id === providers.defaultId)
              : providers.configs[0];
            return def ? `${def.providerType} \u00B7 ${def.model}` : "";
          })()}
        </span>
      )}
      <a href="#/settings" className="topbar-gear" title="Settings">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      </a>
    </header>
  );
}
