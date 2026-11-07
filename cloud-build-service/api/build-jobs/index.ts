import type { VercelRequest, VercelResponse } from "@vercel/node";
import { randomUUID } from "node:crypto";
import { allowedHttpsUrl, db, githubRequest, requireUser, respondError } from "../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") return res.status(405).setHeader("Allow", "POST").json({ error: "method_not_allowed" });
  try {
    const userId = await requireUser(req);
    const body = req.body && typeof req.body === "object" ? req.body as Record<string, unknown> : {};
    const storygameUrl = allowedHttpsUrl(body.storygameUrl, "رابط ملف القصة");
    const title = typeof body.gameTitle === "string" ? body.gameTitle.trim() : "";
    if (!title || title.length > 40) throw new Error("INVALID:اسم اللعبة مطلوب وبحد أقصى 40 حرفًا.");
    const applicationId = body.applicationId === undefined || body.applicationId === "" ? "" : String(body.applicationId);
    if (applicationId && !/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*){1,5}$/.test(applicationId)) throw new Error("INVALID:معرّف التطبيق غير صالح.");
    const versionName = body.versionName === undefined ? "1.0.0" : String(body.versionName);
    if (!/^\d+\.\d+\.\d+$/.test(versionName)) throw new Error("INVALID:الإصدار يجب أن يكون بصيغة 1.0.0.");
    const versionCode = body.versionCode === undefined ? 1 : Number(body.versionCode);
    if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2100000000) throw new Error("INVALID:رقم الإصدار غير صالح.");
    const buildType = body.buildType === "release" ? "release" : body.buildType === undefined || body.buildType === "debug" ? "debug" : null;
    if (!buildType) throw new Error("INVALID:نوع البناء غير صالح.");
    const iconUrl = body.iconUrl ? allowedHttpsUrl(body.iconUrl, "رابط الأيقونة") : "";
    const idempotencyKey = req.headers["idempotency-key"];
    if (typeof idempotencyKey !== "string" || !/^[a-zA-Z0-9_-]{12,100}$/.test(idempotencyKey)) throw new Error("INVALID:مفتاح الطلب غير موجود أو غير صالح.");

    const database = db();
    const client = await database.connect();
    const id = randomUUID();
    const branch = process.env.STORYFORGE_BUILD_REF || "feature/standalone-apk-pipeline";
    try {
      await client.query("BEGIN");
      await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", [userId]);
      const prior = await client.query("SELECT id,status FROM storyforge_build_jobs WHERE user_id=$1 AND idempotency_key=$2", [userId,idempotencyKey]);
      if (prior.rowCount) {
        await client.query("COMMIT");
        return res.status(200).json({ jobId: prior.rows[0].id, status: prior.rows[0].status, reused: true });
      }
      const active = await client.query("SELECT count(*)::int AS count FROM storyforge_build_jobs WHERE user_id=$1 AND status IN ('queued','in_progress') AND created_at > now() - interval '24 hours'", [userId]);
      if (active.rows[0].count >= 2) {
        await client.query("ROLLBACK");
        return res.status(429).json({ error: "rate_limited", message: "لديك طلبا بناء نشطان بالفعل. انتظر انتهاء أحدهما." });
      }
      await client.query(
        "INSERT INTO storyforge_build_jobs(id,user_id,idempotency_key,status,build_ref,game_title,application_id,version_name,version_code,build_type,storygame_url,icon_url) VALUES($1,$2,$3,'queued',$4,$5,$6,$7,$8,$9,$10,$11)",
        [id,userId,idempotencyKey,branch,title,applicationId || null,versionName,versionCode,buildType,storygameUrl,iconUrl || null]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }

    try {
      const owner = process.env.GITHUB_REPOSITORY_OWNER || "omegathor3-afk";
      const repo = process.env.GITHUB_REPOSITORY_NAME || "STORYFORGE";
      const dispatch = await githubRequest("/repos/" + owner + "/" + repo + "/actions/workflows/standalone-apk.yml/dispatches", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ref: branch, inputs: {
          request_id: id, storygame_url: storygameUrl, game_title: title,
          application_id: applicationId, icon_url: iconUrl,
          version_name: versionName, version_code: String(versionCode), build_type: buildType
        } }),
      });
      if (dispatch.status !== 204) throw new Error("GitHub workflow dispatch failed with status " + dispatch.status);
    } catch (error) {
      await database.query("UPDATE storyforge_build_jobs SET status='failed', error_code='dispatch_failed', updated_at=now() WHERE id=$1", [id]);
      throw error;
    }
    return res.status(202).json({ jobId: id, status: "queued", statusUrl: "/api/build-jobs/" + id });
  } catch (error) { return respondError(res, error); }
}
