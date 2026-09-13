import { useEffect, useState } from "react";
import type { PreviewPair, Proposal } from "../api.js";

// Confirmation cards (design.md §5.5): inline diff cards, never modals.
// before→after rows per affected item, tier label, undoable-reassurance
// line, Approve / Edit / Cancel. Rendering only — tiers, previews, and the
// approval payload all come from the backend contract verbatim.

function shortVal(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
    const m = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
    return m ? `${m[3]}.${m[2]} ${m[4]}:${m[5]}` : value;
  }
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function titleOf(image: unknown): string {
  if (typeof image === "object" && image !== null && "title" in image && typeof (image as { title: unknown }).title === "string") {
    return (image as { title: string }).title;
  }
  return "item";
}

function unwrap(image: unknown): unknown {
  return Array.isArray(image) ? (image[0] ?? null) : image;
}

function changedFields(before: unknown, after: unknown): Array<{ key: string; from: unknown; to: unknown }> {
  const b = (typeof before === "object" && before !== null ? before : {}) as Record<string, unknown>;
  const a = (typeof after === "object" && after !== null ? after : {}) as Record<string, unknown>;
  const quiet = new Set(["id", "workspaceid", "createdat", "updatedat", "source", "lastusedat"]);
  const norm = (k: string): string => k.replace(/_/g, "").toLowerCase();
  const bNorm = new Map<string, unknown>(Object.keys(b).map((k) => [norm(k), b[k]]));
  const out: Array<{ key: string; from: unknown; to: unknown }> = [];
  for (const key of Object.keys(a)) {
    if (quiet.has(norm(key))) continue;
    const bv = bNorm.has(norm(key)) ? bNorm.get(norm(key)) : null;
    const av = a[key] ?? null;
    const normBool = (v: unknown): unknown => {
      if (typeof v === "boolean" && (bv === 0 || bv === 1)) return bv === 1;
      if ((v === 0 || v === 1) && typeof bv === "boolean") return v === 1;
      return v;
    };
    if (JSON.stringify(normBool(bv)) !== JSON.stringify(normBool(av))) {
      out.push({ key, from: bv, to: av });
    }
  }
  return out;
}

function PairRows({ pair }: { pair: PreviewPair }) {
  const before = unwrap(pair.before);
  const after = unwrap(pair.after);
  if (before === null && after !== null) {
    return (
      <div className="diff-row">
        <span className="diff-new">+ {titleOf(after)}</span>
      </div>
    );
  }
  if (after === null && before !== null) {
    return (
      <div className="diff-row">
        <span className="diff-old">− {titleOf(before)}</span>
      </div>
    );
  }
  const fields = changedFields(before, after);
  return (
    <div className="diff-row">
      <span className="diff-title">{titleOf(after)}</span>
      {fields.length === 0 ? (
        <span className="meta">no field changes</span>
      ) : (
        <ul className="diff-fields">
          {fields.map((f) => (
            <li key={f.key}>
              <code>{f.key}</code>: <s>{shortVal(f.from)}</s> → <strong>{shortVal(f.to)}</strong>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function ProposalCard({ proposal, stepIndex, entering }: { proposal: Proposal; stepIndex: number | null; entering?: boolean }) {
  return (
    <div className={`diff-card${entering ? " entering" : ""}`}>
      <div className="diff-head">
        <span className={`tier-badge tier-${proposal.tier}`}>{proposal.tier}</span>
        <span className="diff-tool">{proposal.tool}</span>
        {stepIndex !== null && <span className="meta">step {stepIndex + 1}</span>}
      </div>
      <div className="diff-body">
        {proposal.preview.map((p, i) => (
          <PairRows key={i} pair={p} />
        ))}
      </div>
      <p className="meta" style={{ margin: "8px 0 0" }}>This will be undoable from Action History.</p>
    </div>
  );
}

export function DiffCard({
  fingerprint,
  proposals,
  heldSteps,
  decided,
  thinking,
  onApprove,
  onCancel,
  onEditApply,
}: {
  fingerprint: string;
  proposals: Proposal[];
  heldSteps: Array<{ tool: string; input: unknown }> | null;
  decided: "open" | "approved" | "cancelled" | "superseded";
  thinking: boolean;
  onApprove: (fp: string) => void;
  onCancel: (fp: string) => void;
  onEditApply: (fp: string, stepIndex: number, input: unknown) => void;
}) {
  const [editing, setEditing] = useState<number | null>(null);
  const [draft, setDraft] = useState("");
  const [editError, setEditError] = useState<string | null>(null);
  const [enterClass, setEnterClass] = useState("entering");

  // §7: enter animation on mount (opacity 0 + translateY 12px + blur 4px → visible)
  useEffect(() => {
    const raf = requestAnimationFrame(() => setEnterClass(""));
    return () => cancelAnimationFrame(raf);
  }, []);

  if (decided !== "open") {
    const label =
      decided === "approved" ? "Approved ✓" : decided === "cancelled" ? "Cancelled — nothing was executed." : "Superseded by an edited plan.";
    return <div className="diff-card decided resolving"><span className="meta">{label}</span></div>;
  }

  function startEdit(stepIndex: number): void {
    const held = heldSteps?.[stepIndex];
    setDraft(JSON.stringify(held ? held.input : {}, null, 2));
    setEditError(null);
    setEditing(stepIndex);
  }

  function applyEdit(stepIndex: number): void {
    try {
      const parsed = JSON.parse(draft) as unknown;
      setEditing(null);
      onEditApply(fingerprint, stepIndex, parsed);
    } catch {
      setEditError("Not valid JSON — fix it and try again. Nothing was sent.");
    }
  }

  return (
    <div
      className="diff-card-wrap"
      onKeyDown={(e) => {
        if (e.key === "Escape") onCancel(fingerprint);
      }}
    >
      <div className="diff-head-main">
        <span className="tier-badge tier-confirm">needs your approval</span>
        <span className="meta">{proposals.reduce((n, p) => n + p.affectedIds.length, 0)} item(s)</span>
      </div>
      {proposals.map((p, i) => (
        <div key={i} style={{ animationDelay: `${i * 50}ms` }}>
          <ProposalCard proposal={p} stepIndex={proposals.length > 1 ? i : null} entering={enterClass === "entering"} />
          {heldSteps && heldSteps.length > 0 && (
            editing === i ? (
              <div className="edit-box">
                <textarea
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  rows={6}
                  aria-label={`Edit step ${i + 1} input as JSON`}
                  spellCheck={false}
                />
                {editError && <p className="agent-msg error" role="alert">{editError}</p>}
                <div className="diff-actions">
                  <button type="button" className="btn-primary" disabled={thinking} onClick={() => applyEdit(i)}>
                    Apply &amp; re-review
                  </button>
                  <button type="button" className="mini-btn" onClick={() => setEditing(null)}>
                    Back
                  </button>
                </div>
                <p className="meta">Edits re-submit for backend re-classification — the tier can change.</p>
              </div>
            ) : (
              <button type="button" className="mini-btn" disabled={thinking} onClick={() => startEdit(i)}>
                Edit step {proposals.length > 1 ? i + 1 : "input"}
              </button>
            )
          )}
        </div>
      ))}
      <div className="diff-actions">
        <button
          type="button"
          className="btn-primary"
          disabled={thinking}
          onClick={() => onApprove(fingerprint)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) onApprove(fingerprint);
          }}
        >
          Approve
        </button>
        <button type="button" className="mini-btn" onClick={() => onCancel(fingerprint)}>
          Cancel
        </button>
      </div>
    </div>
  );
}

export function BlockedCard({
  proposal,
  decided,
  thinking,
  onReviewItemized,
  onCancel,
}: {
  proposal: Proposal;
  decided: "open" | "approved" | "cancelled" | "superseded";
  thinking: boolean;
  onReviewItemized: () => void;
  onCancel: () => void;
}) {
  const [enterClass, setEnterClass] = useState("entering");
  useEffect(() => {
    const raf = requestAnimationFrame(() => setEnterClass(""));
    return () => cancelAnimationFrame(raf);
  }, []);

  if (decided !== "open") {
    return (
      <div className="diff-card decided resolving">
        <span className="meta">{decided === "cancelled" ? "Cancelled — nothing was executed." : "Resolving…"}</span>
      </div>
    );
  }
  const itemized = proposal.itemizedAlternative;
  return (
    <div
      className={`diff-card-wrap blocked ${enterClass}`}
      onKeyDown={(e) => {
        if (e.key === "Escape") onCancel();
      }}
    >
      <div className="diff-head-main">
        <span className="tier-badge tier-blocked">blocked — review needed</span>
      </div>
      <p style={{ margin: "8px 0" }}>{proposal.message}</p>
      {itemized && (
        <>
          <ProposalCard proposal={itemized} stepIndex={null} />
          <div className="diff-actions">
            <button type="button" className="btn-primary" disabled={thinking || itemized.executableInput == null} onClick={onReviewItemized}>
              Review itemized plan
            </button>
            <button type="button" className="mini-btn" onClick={onCancel}>
              Cancel
            </button>
          </div>
        </>
      )}
    </div>
  );
}
