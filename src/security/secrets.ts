// Credential + error primitives.
// - API keys are AES-256-GCM encrypted at rest (docs/08 §3, docs/19 §3).
//   Key source: FLOWOS_MASTER_KEY env (32 bytes, hex or base64), else a
//   machine-local key file (0600) generated on first run. No credential is
//   ever written to logs — use redact() before persisting debug output.
// - FlowOSError carries what failed, why, and what to do next (docs/20 §3).

import { randomBytes, createCipheriv, createDecipheriv } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync, chmodSync } from "node:fs";
import { join } from "node:path";

export type ErrorCode =
  | "VALIDATION"
  | "NOT_FOUND"
  | "CONFLICT"
  | "STORE"
  | "PROVIDER_AUTH"
  | "PROVIDER_UNREACHABLE"
  | "PROVIDER_RATE_LIMIT"
  | "MODEL_NO_TOOLCALL"
  | "MALFORMED_TOOL_CALL"
  | "AMBIGUOUS_REQUEST"
  | "PARTIAL_EXECUTION"
  | "VERIFICATION_MISMATCH"
  | "UNDO_UNAVAILABLE"
  | "BLOCKED_ACTION"
  | "NOT_IMPLEMENTED"
  | "SECRETS";

export class FlowOSError extends Error {
  readonly code: ErrorCode;
  /** What the user should do next (docs/20 §3 — always present). */
  readonly next: string;
  readonly causeDetail?: string | undefined;

  constructor(code: ErrorCode, message: string, next: string, causeDetail?: string) {
    super(message);
    this.name = "FlowOSError";
    this.code = code;
    this.next = next;
    this.causeDetail = causeDetail;
  }

  toUserString(): string {
    return `${this.message} Next step: ${this.next}`;
  }
}

const KEY_BYTES = 32;

function loadMasterKey(dataDir: string): Buffer {
  const fromEnv = process.env["FLOWOS_MASTER_KEY"];
  if (fromEnv) {
    const key = fromEnv.startsWith("hex:")
      ? Buffer.from(fromEnv.slice(4), "hex")
      : Buffer.from(fromEnv, "base64");
    if (key.length !== KEY_BYTES) {
      throw new FlowOSError(
        "SECRETS",
        "FLOWOS_MASTER_KEY has the wrong length (need 32 bytes).",
        "Set FLOWOS_MASTER_KEY to 32 random bytes, hex- or base64-encoded.",
      );
    }
    return key;
  }
  mkdirSync(dataDir, { recursive: true });
  const keyPath = join(dataDir, "flowos.key");
  if (existsSync(keyPath)) {
    const raw = readFileSync(keyPath, "utf8").trim();
    return Buffer.from(raw, "hex");
  }
  const key = randomBytes(KEY_BYTES);
  writeFileSync(keyPath, key.toString("hex"), { mode: 0o600 });
  try {
    chmodSync(keyPath, 0o600);
  } catch {
    // Non-POSIX platforms (Windows ACLs apply instead) — file is in the
    // user's local data dir; the threat model here is casual disclosure.
  }
  return key;
}

/** AES-256-GCM envelope: {"iv","tag","data"} base64. */
export function encryptSecret(plaintext: string, dataDir: string): string {
  const key = loadMasterKey(dataDir);
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const data = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return JSON.stringify({
    iv: iv.toString("base64"),
    tag: tag.toString("base64"),
    data: data.toString("base64"),
  });
}

export function decryptSecret(envelope: string, dataDir: string): string {
  const key = loadMasterKey(dataDir);
  let parsed: { iv: string; tag: string; data: string };
  try {
    parsed = JSON.parse(envelope) as { iv: string; tag: string; data: string };
  } catch {
    throw new FlowOSError("SECRETS", "Stored credential is corrupted.", "Re-enter the provider API key in Settings.");
  }
  try {
    const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(parsed.iv, "base64"));
    decipher.setAuthTag(Buffer.from(parsed.tag, "base64"));
    return Buffer.concat([
      decipher.update(Buffer.from(parsed.data, "base64")),
      decipher.final(),
    ]).toString("utf8");
  } catch {
    throw new FlowOSError(
      "SECRETS",
      "Stored credential could not be decrypted (wrong key?).",
      "Check FLOWOS_MASTER_KEY matches the key used at setup, or re-enter the provider API key in Settings.",
    );
  }
}

/** Strip anything that looks like a credential before persisting debug logs. */
export function redactSecrets(value: unknown): unknown {
  if (typeof value === "string") {
    return value
      .replace(/(sk-ant-[A-Za-z0-9\-_]{10,})/g, "[REDACTED]")
      .replace(/(sk-[A-Za-z0-9]{10,})/g, "[REDACTED]")
      .replace(/("api_?key"\s*:\s*")[^"]+(")/gi, "$1[REDACTED]$2");
  }
  if (Array.isArray(value)) return value.map(redactSecrets);
  if (value !== null && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value)) {
      out[k] = /api.?key|secret|token|password/i.test(k) ? "[REDACTED]" : redactSecrets(v);
    }
    return out;
  }
  return value;
}

export function nowIso(): string {
  return new Date().toISOString();
}
