import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createAppAuth } from "@octokit/auth-app";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { Pool } from "pg";

function required(key: string): string {
  const value = process.env[key];
  if (!value) throw new Error("Missing server configuration: " + key);
  return value;
}

let pool: Pool | undefined;
export function db(): Pool {
  if (!pool) pool = new Pool({
    connectionString: required("DATABASE_URL"),
    max: 4,
    idleTimeoutMillis: 10000,
    connectionTimeoutMillis: 5000,
    ssl: process.env.DATABASE_SSL === "false" ? false : { rejectUnauthorized: true },
  });
  return pool;
}

let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;
export async function requireUser(req: VercelRequest): Promise<string> {
  const header = req.headers.authorization;
  if (!header || !header.startsWith("Bearer ")) throw new Error("UNAUTHORIZED");
  const token = header.slice(7);
  if (token.length > 8192) throw new Error("UNAUTHORIZED");
  if (!jwks) jwks = createRemoteJWKSet(new URL(required("OIDC_JWKS_URL")));
  const result = await jwtVerify(token, jwks, {
    issuer: required("OIDC_ISSUER"),
    audience: required("OIDC_AUDIENCE"),
  });
  if (typeof result.payload.sub !== "string" || !result.payload.sub) throw new Error("UNAUTHORIZED");
  return result.payload.sub;
}

export function respondError(res: VercelResponse, error: unknown): void {
  const message = error instanceof Error ? error.message : "Internal error";
  if (message === "UNAUTHORIZED" || message.includes("JWT")) {
    res.status(401).json({ error: "unauthorized", message: "سجّل الدخول ثم حاول مرة أخرى." });
  } else if (message.startsWith("INVALID:")) {
    res.status(400).json({ error: "invalid_request", message: message.slice(8) });
  } else {
    console.error("STORYFORGE cloud build error", error);
    res.status(500).json({ error: "server_error", message: "تعذر تنفيذ الطلب الآن." });
  }
}

export function allowedHttpsUrl(raw: unknown, field: string): string {
  if (typeof raw !== "string" || raw.length > 2048) throw new Error("INVALID:" + field + " غير صالح.");
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error("INVALID:" + field + " يجب أن يكون رابطًا صحيحًا."); }
  if (url.protocol !== "https:" || url.username || url.password || (url.port && url.port !== "443")) {
    throw new Error("INVALID:" + field + " يجب أن يكون HTTPS آمنًا.");
  }
  const allowed = (process.env.STORYGAME_ALLOWED_HOSTS || "").split(",").map(x => x.trim().toLowerCase()).filter(Boolean);
  const host = url.hostname.toLowerCase();
  if (!allowed.length || !allowed.some(domain => host === domain || host.endsWith("." + domain))) {
    throw new Error("INVALID:" + field + " يجب أن يكون على نطاق مسموح به في إعدادات الخدمة.");
  }
  return url.toString();
}

export async function githubRequest(path: string, init: RequestInit = {}): Promise<Response> {
  const auth = createAppAuth({
    appId: required("GITHUB_APP_ID"),
    privateKey: required("GITHUB_APP_PRIVATE_KEY").replace(/\\n/g, "\n"),
    installationId: required("GITHUB_APP_INSTALLATION_ID"),
  });
  const installation = await auth({ type: "installation" });
  return fetch("https://api.github.com" + path, {
    ...init,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: "Bearer " + installation.token,
      "X-GitHub-Api-Version": "2022-11-28",
      ...(init.headers || {}),
    },
  });
}
