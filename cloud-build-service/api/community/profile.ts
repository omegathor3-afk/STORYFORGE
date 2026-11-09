import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId = await requireUser(req);
    if (req.method === "GET") {
      const result = await db().query(
        "SELECT user_id,display_name,bio,created_at,(SELECT count(*)::int FROM community_posts WHERE user_id=$1 AND status='published') AS posts_count,(SELECT count(*)::int FROM community_follows WHERE followed_id=$1) AS followers_count,(SELECT count(*)::int FROM community_follows WHERE follower_id=$1) AS following_count FROM community_profiles WHERE user_id=$1",
        [userId]
      );
      return res.status(200).json({profile:result.rows[0] || null});
    }
    if (req.method === "POST") {
      const body = req.body && typeof req.body === "object" ? req.body as Record<string,unknown> : {};
      const name = typeof body.displayName === "string" ? body.displayName.trim() : "";
      const bio = typeof body.bio === "string" ? body.bio.trim() : "";
      if (!name || name.length > 40) throw new Error("INVALID:اسم العرض مطلوب وبحد أقصى 40 حرفًا.");
      if (bio.length > 240) throw new Error("INVALID:النبذة تتجاوز 240 حرفًا.");
      await db().query(
        "INSERT INTO community_profiles(user_id,display_name,bio) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET display_name=EXCLUDED.display_name,bio=EXCLUDED.bio,updated_at=now()",
        [userId,name,bio]
      );
      return res.status(200).json({displayName:name,bio});
    }
    return res.status(405).setHeader("Allow","GET, POST").json({error:"method_not_allowed"});
  } catch(error) { return respondError(res,error); }
}
