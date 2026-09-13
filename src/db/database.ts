// SQLite database factory. Single-user, local-first: one file per instance
// (or :memory: for tests). WAL mode is enabled by the schema itself.
import Database from "better-sqlite3";
import { SCHEMA_SQL } from "./schema.js";

export type FlowDatabase = Database.Database;

export function openDatabase(path: string): FlowDatabase {
  const db = new Database(path);
  db.exec(SCHEMA_SQL);
  return db;
}

export function closeDatabase(db: FlowDatabase): void {
  db.close();
}
