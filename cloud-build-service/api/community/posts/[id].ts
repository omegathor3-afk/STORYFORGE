import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    await requireUser(req);
    if (req.method !== "GET") return res.status(405).setHeader("Allow","GET").json({error:"method_not_allowed"});
    const id = typeof req.query.id === "string" ? req.query.id : "";
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("INVALID:معرّف المنشور غير صالح.");
    const result = await db().query(
      "SELECT p.id,p.title,p.body,p.genre,p.story_json,p.created_at,COALESCE(pr.display_name,'كاتب مجهول') AS author_name FROM community_posts p LEFT JOIN community_profiles pr ON pr.user_id=p.user_id WHERE p.id=$1 AND p.status='published'",
      [id]
    );
    if (!result.rowCount) return res.status(404).json({error:"not_found"});
    return res.status(200).json({post:result.rows[0]});
  } catch(error) { return respondError(res,error); }
}
