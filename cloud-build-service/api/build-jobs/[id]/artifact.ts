import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, githubRequest, requireUser, respondError } from "../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "GET") return res.status(405).setHeader("Allow", "GET").json({ error: "method_not_allowed" });
  try {
    const userId = await requireUser(req);
    const id = String(req.query.id || "");
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("INVALID:معرّف طلب البناء غير صالح.");
    const result = await db().query("SELECT github_run_id,status FROM storyforge_build_jobs WHERE id=$1 AND user_id=$2", [id,userId]);
    if (!result.rowCount || result.rows[0].status !== "completed" || !result.rows[0].github_run_id) return res.status(404).json({ error: "artifact_not_ready" });
    const owner = process.env.GITHUB_REPOSITORY_OWNER || "omegathor3-afk";
    const repo = process.env.GITHUB_REPOSITORY_NAME || "STORYFORGE";
    const response = await githubRequest("/repos/" + owner + "/" + repo + "/actions/runs/" + result.rows[0].github_run_id + "/artifacts");
    if (!response.ok) throw new Error("Could not list build artifacts.");
    const data = await response.json() as { artifacts?: Array<{ name: string; expired: boolean; archive_download_url: string }> };
    const found = (data.artifacts || []).find(entry => entry.name.startsWith("STORYFORGE-GAME-" + id + "-") && !entry.expired);
    if (!found) return res.status(404).json({ error: "artifact_expired" });
    const archiveUrl = new URL(found.archive_download_url);
    const download = await githubRequest(archiveUrl.pathname + archiveUrl.search, { redirect: "manual" });
    if (![301,302,303,307,308].includes(download.status)) throw new Error("Artifact download URL could not be created.");
    const location = download.headers.get("location");
    if (!location || !location.startsWith("https://")) throw new Error("Artifact download redirect is invalid.");
    return res.redirect(302, location);
  } catch (error) { return respondError(res, error); }
}
