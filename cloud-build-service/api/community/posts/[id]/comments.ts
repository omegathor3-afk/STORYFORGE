import type { VercelRequest, VercelResponse } from "@vercel/node";
import { randomUUID } from "node:crypto";
import { db, requireUser, respondError } from "../../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId = await requireUser(req);
    const postId = typeof req.query.id === "string" ? req.query.id : "";
    if (!/^[0-9a-f-]{36}$/i.test(postId)) throw new Error("INVALID:معرّف المنشور غير صالح.");
    const post = await db().query("SELECT id FROM community_posts WHERE id=$1 AND status='published'",[postId]);
    if (!post.rowCount) return res.status(404).json({error:"not_found"});
    if (req.method === "GET") {
      const result = await db().query(
        "SELECT c.id,c.body,c.created_at,c.user_id,COALESCE(p.display_name,'كاتب مجهول') AS author_name FROM community_comments c LEFT JOIN community_profiles p ON p.user_id=c.user_id WHERE c.post_id=$1 AND c.status='published' ORDER BY c.created_at ASC LIMIT 100",
        [postId]
      );
      return res.status(200).json({comments:result.rows});
    }
    if (req.method === "POST") {
      const body = req.body && typeof req.body === "object" ? req.body as Record<string,unknown> : {};
      const content = typeof body.body === "string" ? body.body.trim() : "";
      if (!content || content.length > 1200) throw new Error("INVALID:التعليق مطلوب وبحد أقصى 1200 حرف.");
      const recent = await db().query("SELECT count(*)::int AS count FROM community_comments WHERE user_id=$1 AND created_at > now()-interval '1 hour'",[userId]);
      if (recent.rows[0].count >= 40) return res.status(429).json({error:"rate_limited",message:"وصلت للحد المؤقت للتعليقات."});
      const id=randomUUID();
      await db().query("INSERT INTO community_comments(id,post_id,user_id,body) VALUES($1,$2,$3,$4)",[id,postId,userId,content]);
      return res.status(201).json({id,body:content});
    }
    return res.status(405).setHeader("Allow","GET, POST").json({error:"method_not_allowed"});
  } catch(error) { return respondError(res,error); }
}
