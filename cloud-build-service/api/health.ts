import type { VercelRequest, VercelResponse } from "@vercel/node";

const REQUIRED_ENV = [
  "DATABASE_URL",
  "OIDC_ISSUER",
  "OIDC_AUDIENCE",
  "OIDC_JWKS_URL",
  "GITHUB_APP_ID",
  "GITHUB_APP_PRIVATE_KEY",
  "GITHUB_APP_INSTALLATION_ID",
  "STORYGAME_ALLOWED_HOSTS",
  "STORYFORGE_BUILD_REF",
];

export default function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "GET") {
    return res.status(405).setHeader("Allow", "GET").json({ error: "method_not_allowed" });
  }

  const ready = REQUIRED_ENV.every((key) => Boolean(process.env[key]?.trim()));
  return res.status(ready ? 200 : 503).json({
    service: "storyforge-cloud-build",
    status: ready ? "ready" : "not_configured",
  });
}
