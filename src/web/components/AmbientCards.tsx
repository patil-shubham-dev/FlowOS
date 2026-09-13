import type { Overview } from "../api.js";

export function AmbientCards({ overview }: { overview: Overview | null }) {
  if (!overview) {
    return (
      <aside className="rail">
        <section className="card ambient-card">
          <h3>Open tasks</h3>
          <div className="skeleton skeleton-line" />
          <div className="skeleton skeleton-line short" />
        </section>
        <section className="card ambient-card">
          <h3>Today's events</h3>
          <div className="skeleton skeleton-line" />
        </section>
        <section className="card ambient-card">
          <h3>Workspace</h3>
          <div className="skeleton skeleton-line short" />
        </section>
      </aside>
    );
  }

  const dueTasks = overview.tasksOpen.slice(0, 4);
  const upcomingEvents = overview.eventsToday.slice(0, 3);

  return (
    <aside className="rail">
      {dueTasks.length > 0 && (
        <section className="card ambient-card">
          <h3>Open tasks</h3>
          <ul>
            {dueTasks.map((t) => (
              <li key={t.id}>
                {t.title}
                <span className="meta">
                  {" "}{t.dueAt ? new Date(t.dueAt).toLocaleDateString(undefined, { month: "short", day: "numeric" }) : ""}
                  {" "}{t.priority !== "normal" ? `\u00B7 ${t.priority}` : ""}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {upcomingEvents.length > 0 && (
        <section className="card ambient-card">
          <h3>Today's events</h3>
          <ul>
            {upcomingEvents.map((e) => (
              <li key={e.id}>
                {e.title}
                <span className="meta">
                  {" "}{new Date(e.startAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card ambient-card">
        <h3>Workspace</h3>
        <p className="meta">
          {overview.tasksOpen.length} open task{overview.tasksOpen.length !== 1 ? "s" : ""}
          {" "}\u00B7 {overview.eventsToday.length} event{overview.eventsToday.length !== 1 ? "s" : ""} today
          {" "}\u00B7 {overview.notesCount} note{overview.notesCount !== 1 ? "s" : ""}
        </p>
      </section>
    </aside>
  );
}
