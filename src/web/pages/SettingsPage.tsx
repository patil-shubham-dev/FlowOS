import { useCallback, useEffect, useState, type FormEvent } from "react";
import { deletePreference, deleteProvider, getPreferences, getProviders, patchPreference, postProvider, notifyDataChanged, validateProvider, getPermissions, putPermission, type Preference, type ProviderInfo, type PermissionOverride } from "../api.js";
import { HistoryPanel } from "../components/HistoryPanel.js";

const PROVIDER_TYPES = ["anthropic", "nvidia", "openai", "ollama", "lm_studio", "openrouter", "google", "custom_openai_compatible"] as const;

const NVIDIA_DEFAULTS = { baseUrl: "https://integrate.api.nvidia.com/v1", model: "openai/gpt-oss-20b" };

type Tab = "providers" | "preferences" | "permissions";

interface ProviderDraft {
  providerType: string;
  model: string;
  apiKey: string;
  baseUrl: string;
  isDefault: boolean;
}

const EMPTY_PROVIDER_DRAFT: ProviderDraft = { providerType: "nvidia", model: NVIDIA_DEFAULTS.model, apiKey: "", baseUrl: NVIDIA_DEFAULTS.baseUrl, isDefault: false };

function ProviderForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: ProviderDraft;
  submitLabel: string;
  onSubmit: (input: ProviderDraft) => Promise<void>;
  onCancel?: () => void;
}) {
  const [draft, setDraft] = useState<ProviderDraft>(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof ProviderDraft, v: string | boolean): void => setDraft((d) => ({ ...d, [k]: v }));

  const needsKey = !["ollama", "lm_studio"].includes(draft.providerType);
  const needsUrl = ["ollama", "lm_studio", "custom_openai_compatible", "nvidia"].includes(draft.providerType);

  const handleTypeChange = (v: string): void => {
    set("providerType", v);
    if (v === "nvidia" && !draft.baseUrl) {
      setDraft((d) => ({ ...d, baseUrl: NVIDIA_DEFAULTS.baseUrl, model: d.model || NVIDIA_DEFAULTS.model }));
    }
  };

  async function submit(e: FormEvent): Promise<void> {
    e.preventDefault();
    if (!draft.model.trim()) { setError("Model is required."); return; }
    if (needsKey && !draft.apiKey.trim()) { setError("API key is required for this provider."); return; }
    if (needsUrl && !draft.baseUrl.trim()) { setError("Base URL is required for this provider."); return; }
    setBusy(true);
    setError(null);
    try {
      await onSubmit(draft);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form-card" onSubmit={(e) => void submit(e)}>
      <div className="form-grid">
        <label>Provider type
          <select value={draft.providerType} onChange={(e) => handleTypeChange(e.target.value)}>
            {PROVIDER_TYPES.map((t) => <option key={t} value={t}>{t === "nvidia" ? "Nvidia NIM" : t}</option>)}
          </select>
        </label>
        <label>Model
          <input value={draft.model} onChange={(e) => set("model", e.target.value)} placeholder="e.g. openai/gpt-oss-20b" required />
        </label>
        {needsKey && (
          <label>API key
            <input type="password" value={draft.apiKey} onChange={(e) => set("apiKey", e.target.value)} placeholder="nvapi-..." />
          </label>
        )}
        {needsUrl && (
          <label>Base URL
            <input value={draft.baseUrl} onChange={(e) => set("baseUrl", e.target.value)} placeholder="https://integrate.api.nvidia.com/v1" />
          </label>
        )}
      </div>
      <label className="check-line">
        <input type="checkbox" checked={draft.isDefault} onChange={(e) => set("isDefault", e.target.checked)} />
        Set as default provider
      </label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={busy}>{submitLabel}</button>
        {onCancel && <button type="button" className="mini-btn" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

function PreferenceRow({
  pref,
  onSaved,
  onDeleted,
}: {
  pref: Preference;
  onSaved: () => void;
  onDeleted: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [statement, setStatement] = useState(pref.statement);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save(): Promise<void> {
    if (!statement.trim()) { setError("Statement cannot be empty."); return; }
    setBusy(true);
    setError(null);
    try {
      await patchPreference(pref.id, { statement: statement.trim() });
      setEditing(false);
      onSaved();
      notifyDataChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed.");
    } finally {
      setBusy(false);
    }
  }

  async function del(): Promise<void> {
    if (!window.confirm("Delete this learned fact?")) return;
    try {
      await deletePreference(pref.id);
      onDeleted();
      notifyDataChanged();
    } catch { /* refresh will show error state */ }
  }

  if (editing) {
    return (
      <div className="card form-card">
        <div className="form-grid">
          <label>Edit statement
            <input value={statement} onChange={(e) => setStatement(e.target.value)} />
          </label>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions">
          <button type="button" className="btn-primary" disabled={busy} onClick={() => void save()}>Save</button>
          <button type="button" className="mini-btn" onClick={() => { setEditing(false); setStatement(pref.statement); }}>Cancel</button>
        </div>
      </div>
    );
  }

  return (
    <div className="card pref-row">
      <span className="grow pref-statement">{pref.statement}</span>
      <span className="meta">{pref.createdAt.slice(0, 10)}</span>
      <button type="button" className="mini-btn" onClick={() => setEditing(true)}>Edit</button>
      <button type="button" className="mini-btn" onClick={() => void del()}>Delete</button>
    </div>
  );
}

export function SettingsPage() {
  const [tab, setTab] = useState<Tab>("providers");
  const [providers, setProviders] = useState<ProviderInfo | null>(null);
  const [preferences, setPreferences] = useState<Preference[]>([]);
  const [permissions, setPermissions] = useState<PermissionOverride[]>([]);
  const [showAddProvider, setShowAddProvider] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [validating, setValidating] = useState<string | null>(null);
  const [validateResult, setValidateResult] = useState<{ id: string; ok: boolean; message: string } | null>(null);

  const refreshProviders = useCallback(() => {
    getProviders().then((p) => setProviders(p)).catch(() => setProviders(null));
  }, []);

  const refreshPreferences = useCallback(() => {
    getPreferences().then((r) => setPreferences(r.preferences)).catch(() => setPreferences([]));
  }, []);

  const refreshPermissions = useCallback(() => {
    getPermissions().then((r) => setPermissions(r.overrides)).catch(() => setPermissions([]));
  }, []);

  useEffect(() => {
    refreshProviders();
    refreshPreferences();
    refreshPermissions();
  }, [refreshProviders, refreshPreferences, refreshPermissions]);

  async function handleAddProvider(draft: ProviderDraft): Promise<void> {
    await postProvider({
      providerType: draft.providerType,
      model: draft.model.trim(),
      apiKey: draft.apiKey || null,
      baseUrl: draft.baseUrl || null,
      isDefault: draft.isDefault,
    });
    setShowAddProvider(false);
    refreshProviders();
    notifyDataChanged();
  }

  async function handleDeleteProvider(id: string): Promise<void> {
    if (!window.confirm("Delete this provider config?")) return;
    await deleteProvider(id);
    refreshProviders();
    notifyDataChanged();
  }

  async function handleValidate(id: string): Promise<void> {
    setValidating(id);
    setValidateResult(null);
    try {
      const result = await validateProvider(id);
      setValidateResult({ id, ok: result.ok, message: result.ok ? (result.message ?? "Validated") : (result.error?.message ?? "Validation failed") });
    } catch (err) {
      setValidateResult({ id, ok: false, message: err instanceof Error ? err.message : "Validation failed" });
    } finally {
      setValidating(null);
    }
  }

  const configs = providers?.configs ?? [];

  return (
    <main className="page">
      <h1>Settings</h1>
      <p className="sub">Configure AI providers, manage learned facts, and review action history.</p>

      <div className="filter-row" role="tablist">
        <button type="button" className={`pill${tab === "providers" ? " selected" : ""}`} role="tab" aria-selected={tab === "providers"} onClick={() => setTab("providers")}>AI Providers</button>
        <button type="button" className={`pill${tab === "preferences" ? " selected" : ""}`} role="tab" aria-selected={tab === "preferences"} onClick={() => setTab("preferences")}>Memory</button>
        <button type="button" className={`pill${tab === "permissions" ? " selected" : ""}`} role="tab" aria-selected={tab === "permissions"} onClick={() => setTab("permissions")}>Permissions</button>
        <button type="button" className="pill" onClick={() => setHistoryOpen(true)}>History</button>
      </div>

      {tab === "providers" && (
        <>
          <div className="filter-row">
            <button type="button" className="btn-primary btn-wide" onClick={() => setShowAddProvider((v) => !v)}>
              {showAddProvider ? "Close" : "+ Add provider"}
            </button>
          </div>
          {showAddProvider && (
            <ProviderForm
              initial={EMPTY_PROVIDER_DRAFT}
              submitLabel="Add provider"
              onSubmit={(d) => handleAddProvider(d)}
              onCancel={() => setShowAddProvider(false)}
            />
          )}
          {configs.length === 0 ? (
            <div className="card empty-card">
              <p className="empty-note">No providers configured — add one to enable the agent.</p>
            </div>
          ) : (
            <div className="task-list">
              {configs.map((c) => (
                <div key={c.id} className="card provider-row">
                  <span className="grow provider-info">
                    <strong>{c.providerType}</strong>{" "}
                    <span className="meta">{c.model}</span>
                    {c.isDefault && <span className="tier-badge tier-auto">default</span>}
                    {c.hasApiKey ? <span className="meta">key set</span> : <span className="meta warn-text">no key</span>}
                  </span>
                  <button type="button" className="mini-btn" disabled={validating === c.id} onClick={() => void handleValidate(c.id)}>
                    {validating === c.id ? "Validating\u2026" : "Validate"}
                  </button>
                  <button type="button" className="mini-btn" onClick={() => void handleDeleteProvider(c.id)}>Delete</button>
                </div>
              ))}
            </div>
          )}
          {validateResult && (
            <div className="card validate-result" role="status">
              <span className={`validate-icon ${validateResult.ok ? "ok" : "err"}`}>{validateResult.ok ? "\u2713" : "\u2717"}</span>
              <span className="validate-msg">{validateResult.message}</span>
            </div>
          )}
        </>
      )}

      {tab === "permissions" && (
        <>
          <p className="meta perm-hint">
            Raise friction freely (auto \u2192 confirm). Lower only within safe bounds.
          </p>
          {permissions.length === 0 ? (
            <div className="card empty-card">
              <p className="empty-note">All tools at default tiers — no overrides set.</p>
            </div>
          ) : (
            <div className="task-list">
              {permissions.map((o) => (
                <div key={o.id} className="card provider-row">
                  <span className="grow provider-info"><strong>{o.toolName}</strong> \u2192 <span className="tier-badge tier-auto">{o.overrideTier}</span></span>
                  <button type="button" className="mini-btn" onClick={() => void putPermission(o.toolName, null).then(() => refreshPermissions())}>Reset</button>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {tab === "preferences" && (
        <>
          {preferences.length === 0 ? (
            <div className="card empty-card">
              <p className="empty-note">No learned facts yet — the agent remembers preferences as you interact.</p>
            </div>
          ) : (
            <div className="task-list">
              {preferences.map((p) => (
                <PreferenceRow key={p.id} pref={p} onSaved={refreshPreferences} onDeleted={refreshPreferences} />
              ))}
            </div>
          )}
        </>
      )}

      <HistoryPanel open={historyOpen} onClose={() => setHistoryOpen(false)} onChanged={() => {}} />
    </main>
  );
}
