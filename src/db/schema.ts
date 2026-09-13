// FlowOS SQLite schema — implements docs/13-data-model.md entities.
// The action_log table doubles as the future sync stream (docs/18 §2):
// every entry is a discrete, timestamped, attributable change.

export const SCHEMA_SQL = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  title TEXT NOT NULL,
  description TEXT,
  due_at TEXT,
  priority TEXT NOT NULL DEFAULT 'normal',
  status TEXT NOT NULL DEFAULT 'todo',
  tag TEXT,
  estimated_minutes INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  source TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tasks_workspace ON tasks(workspace_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(workspace_id, due_at);

CREATE TABLE IF NOT EXISTS events (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  title TEXT NOT NULL,
  start_at TEXT NOT NULL,
  end_at TEXT NOT NULL,
  is_flexible INTEGER NOT NULL DEFAULT 0,
  linked_task_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  source TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_workspace ON events(workspace_id);
CREATE INDEX IF NOT EXISTS idx_events_range ON events(workspace_id, start_at, end_at);

CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  title TEXT NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  tags TEXT NOT NULL DEFAULT '[]',
  linked_task_ids TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  source TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notes_workspace ON notes(workspace_id);

-- Full-text search for searchNotes (verdict session 4: FTS for MVP, no embeddings).
CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
  title, content, tags,
  content='notes', content_rowid='rowid'
);
CREATE TRIGGER IF NOT EXISTS notes_ai AFTER INSERT ON notes BEGIN
  INSERT INTO notes_fts(rowid, title, content, tags)
  VALUES (new.rowid, new.title, new.content, new.tags);
END;
CREATE TRIGGER IF NOT EXISTS notes_ad AFTER DELETE ON notes BEGIN
  INSERT INTO notes_fts(notes_fts, rowid, title, content, tags)
  VALUES ('delete', old.rowid, old.title, old.content, old.tags);
END;
CREATE TRIGGER IF NOT EXISTS notes_au AFTER UPDATE ON notes BEGIN
  INSERT INTO notes_fts(notes_fts, rowid, title, content, tags)
  VALUES ('delete', old.rowid, old.title, old.content, old.tags);
  INSERT INTO notes_fts(rowid, title, content, tags)
  VALUES (new.rowid, new.title, new.content, new.tags);
END;

CREATE TABLE IF NOT EXISTS preferences (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  statement TEXT NOT NULL,
  source_session_id TEXT,
  created_at TEXT NOT NULL,
  last_used_at TEXT
);

CREATE TABLE IF NOT EXISTS provider_configs (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  provider_type TEXT NOT NULL,
  model TEXT NOT NULL,
  api_key_encrypted TEXT,
  base_url TEXT,
  params TEXT NOT NULL DEFAULT '{}',
  is_default INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS action_log (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  timestamp TEXT NOT NULL,
  actor TEXT NOT NULL,
  session_id TEXT,
  tool_or_operation TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_ids TEXT NOT NULL DEFAULT '[]',
  before_state TEXT,
  after_state TEXT,
  group_id TEXT,
  undo_status TEXT NOT NULL DEFAULT 'available',
  undo_reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_action_log_workspace ON action_log(workspace_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_action_log_group ON action_log(group_id);

CREATE TABLE IF NOT EXISTS agent_sessions (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  surface TEXT NOT NULL,
  started_at TEXT NOT NULL,
  messages TEXT NOT NULL DEFAULT '[]'
);

-- Permission overrides (doc-11 §4): users may raise friction freely,
-- lower only within safe bounds. The code-enforced floor in risk.ts
-- prevents bypassing blocked-tier tools regardless of UI settings.
CREATE TABLE IF NOT EXISTS permission_overrides (
  id TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL DEFAULT 'default',
  tool_name TEXT NOT NULL,
  override_tier TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(workspace_id, tool_name)
);
`;
