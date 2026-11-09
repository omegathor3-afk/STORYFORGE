import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId=await requireUser(req);
    if(req.method!=="GET") return res.status(405).setHeader("Allow","GET").json({error:"method_not_allowed"});
    const query=typeof req.query.q==="string" ? req.query.q.trim().slice(0,40) : "";
    const result=await db().query(
      "SELECT p.user_id,p.display_name,p.bio,(SELECT count(*)::int FROM community_posts cp WHERE cp.user_id=p.user_id AND cp.status='published') AS posts_count,(SELECT count(*)::int FROM community_follows f WHERE f.followed_id=p.user_id) AS followers_count,EXISTS(SELECT 1 FROM community_follows f WHERE f.follower_id=$1 AND f.followed_id=p.user_id) AS following FROM community_profiles p WHERE p.user_id<>$1 AND ($2='' OR p.display_name ILIKE '%' || $2 || '%') ORDER BY followers_count DESC,p.updated_at DESC LIMIT 30",
      [userId,query]
    );
    return res.status(200).json({members:result.rows});
  } catch(error) { return respondError(res,error); }
}
