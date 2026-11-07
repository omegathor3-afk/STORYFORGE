import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, githubRequest, requireUser, respondError } from "../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "GET") return res.status(405).setHeader("Allow", "GET").json({ error: "method_not_allowed" });
  try {
    const userId = await requireUser(req);
    const id = String(req.query.id || "");
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("INVALID:معرّف طلب البناء غير صالح.");
    const database = db();
    const result = await database.query("SELECT * FROM storyforge_build_jobs WHERE id=$1 AND user_id=$2", [id,userId]);
    if (!result.rowCount) return res.status(404).json({ error: "not_found" });
    const item = result.rows[0];
    if (item.status === "queued" || item.status === "in_progress") {
      const owner = process.env.GITHUB_REPOSITORY_OWNER || "omegathor3-afk";
      const repo = process.env.GITHUB_REPOSITORY_NAME || "STORYFORGE";
      const response = await githubRequest("/repos/" + owner + "/" + repo + "/actions/workflows/standalone-apk.yml/runs?branch=" + encodeURIComponent(item.build_ref) + "&event=workflow_dispatch&per_page=100");
      if (!response.ok) throw new Error("GitHub run lookup failed: " + response.status);
      const data = await response.json() as { workflow_runs?: Array<{ id: number; display_title?: string; status: string; conclusion: string | null }> };
      const run = (data.workflow_runs || []).find(entry => entry.display_title === "STORYFORGE APK - " + id);
      if (run) {
        const nextStatus = run.status === "completed" ? (run.conclusion === "success" ? "completed" : "failed") : "in_progress";
        await database.query("UPDATE storyforge_build_jobs SET github_run_id=$2,status=$3,github_conclusion=$4,updated_at=now() WHERE id=$1", [id,run.id,nextStatus,run.conclusion]);
        item.github_run_id = run.id;
        item.status = nextStatus;
        item.github_conclusion = run.conclusion;
      }
    }
    let artifactAvailable = false;
    if (item.status === "completed" && item.github_run_id) {
      const owner = process.env.GITHUB_REPOSITORY_OWNER || "omegathor3-afk";
      const repo = process.env.GITHUB_REPOSITORY_NAME || "STORYFORGE";
      const response = await githubRequest("/repos/" + owner + "/" + repo + "/actions/runs/" + item.github_run_id + "/artifacts");
      if (response.ok) {
        const data = await response.json() as { artifacts?: Array<{ name: string; expired: boolean }> };
        artifactAvailable = (data.artifacts || []).some(entry => entry.name.startsWith("STORYFORGE-GAME-" + id + "-") && !entry.expired);
      }
    }
    return res.status(200).json({
      jobId: id, status: item.status, conclusion: item.github_conclusion || null,
      gameTitle: item.game_title, buildType: item.build_type,
      createdAt: item.created_at, updatedAt: item.updated_at,
      artifactAvailable,
      downloadUrl: artifactAvailable ? "/api/build-jobs/" + id + "/artifact" : null,
    });
  } catch (error) { return respondError(res, error); }
}
