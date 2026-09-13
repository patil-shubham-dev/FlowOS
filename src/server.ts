// FlowOS process entry: env wiring + listen. All request handling lives in
// app.ts (createApp) so tests can bind the same handler to an ephemeral port.
import { createServer } from "node:http";
import { join } from "node:path";
import { createApp } from "./app.js";

const PORT = Number(process.env["FLOWOS_PORT"] ?? 3001);
const DATA_DIR = process.env["FLOWOS_DATA_DIR"] ?? join(process.cwd(), "data");
const DB_PATH = process.env["FLOWOS_DB"] ?? join(DATA_DIR, "flowos.db");
const PUBLIC_DIR = join(process.cwd(), "dist", "public");

const app = createApp({ dbPath: DB_PATH, dataDir: DATA_DIR, publicDir: PUBLIC_DIR });

createServer((req, res) => app.handle(req, res)).listen(PORT, () => {
  console.log(`[FlowOS] serving on http://localhost:${PORT} (db: ${DB_PATH})`);
});
