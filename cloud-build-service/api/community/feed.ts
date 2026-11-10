import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../_lib/runtime";
import { parseCommunityFeedCursor } from "../_lib/community-feed-cursor";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId = await requireUser(req);
    if (req.method === "GET") {
      const limit = Math.min(50, Math.max(1, Number(req.query.limit) || 20));
      const genre = typeof req.query.genre === "string" ? req.query.genre.slice(0, 32) : "";
      const cursor = typeof req.query.cursor === "string" ? req.query.cursor : "";
      const params: unknown[] = [userId];
      let where = "p.status='published'";
      if (genre && genre !== "all") { params.push(genre); where += " AND p.genre=$" + params.length; }
      if (cursor) {
        const parsedCursor = parseCommunityFeedCursor(cursor);
        if (!parsedCursor) throw new Error("INVALID:مؤشر الصفحة غير صالح.");
        params.push(parsedCursor.time, parsedCursor.id);
        where += " AND (p.created_at,p.id)<($" + (params.length - 1) + "::timestamptz,$" + params.length + "::uuid)";
      }
      params.push(limit);
      const result = await db().query(
        `SELECT p.id,p.user_id,p.title,p.body,p.genre,(p.story_json IS NOT NULL) AS has_story,p.created_at,
          COALESCE(pr.display_name,'كاتب مجهول') AS author_name,
          (SELECT count(*)::int FROM community_reactions r WHERE r.post_id=p.id) AS likes_count,
          (SELECT count(*)::int FROM community_comments c WHERE c.post_id=p.id AND c.status='published') AS comments_count,
          EXISTS(SELECT 1 FROM community_reactions r WHERE r.post_id=p.id AND r.user_id=$1) AS liked
         FROM community_posts p LEFT JOIN community_profiles pr ON pr.user_id=p.user_id
         WHERE ${where} ORDER BY p.created_at DESC,p.id DESC LIMIT $${params.length}`, params
      );
      const rows = result.rows;
      const last = rows[rows.length - 1];
      return res.status(200).json({
        posts: rows,
        nextCursor: rows.length === limit && last ? new Date(last.created_at).toISOString() + "|" + last.id : null
      });
    }
    if (req.method === "POST") {
      const body = req.body && typeof req.body === "object" ? req.body as Record<string, unknown> : {};
      const title = typeof body.title === "string" ? body.title.trim() : "";
      const content = typeof body.body === "string" ? body.body.trim() : "";
      const genre = typeof body.genre === "string" ? body.genre : "other";
      const allowedGenres = ["horror","fantasy","mystery","romance","adventure","scifi","other"];
      if (!title || title.length > 80) throw new Error("INVALID:عنوان المنشور مطلوب وبحد أقصى 80 حرفًا.");
      if (content.length > 4000) throw new Error("INVALID:نص المنشور يتجاوز 4000 حرف.");
      if (!allowedGenres.includes(genre)) throw new Error("INVALID:تصنيف القصة غير صالح.");
      const story = body.story && typeof body.story === "object" ? JSON.stringify(body.story) : null;
      if (story && story.length > 2000000) throw new Error("INVALID:حزمة القصة أكبر من الحد المسموح.");
      const recent = await db().query("SELECT count(*)::int AS count FROM community_posts WHERE user_id=$1 AND created_at > now()-interval '1 hour'", [userId]);
      if (recent.rows[0].count >= 10) return res.status(429).json({error:"rate_limited",message:"وصلت للحد المؤقت للنشر. حاول بعد قليل."});
      const { randomUUID } = await import("node:crypto");
      const id = randomUUID();
      await db().query(
        "INSERT INTO community_posts(id,user_id,title,body,genre,story_json) VALUES($1,$2,$3,$4,$5,$6::jsonb)",
        [id,userId,title,content,genre,story]
      );
      return res.status(201).json({id,title,body:content,genre,status:"published"});
    }
    return res.status(405).setHeader("Allow","GET, POST").json({error:"method_not_allowed"});
  } catch (error) { return respondError(res,error); }
}
