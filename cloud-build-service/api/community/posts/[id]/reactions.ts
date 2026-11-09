import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId = await requireUser(req);
    if (req.method !== "POST") return res.status(405).setHeader("Allow","POST").json({error:"method_not_allowed"});
    const postId = typeof req.query.id === "string" ? req.query.id : "";
    if (!/^[0-9a-f-]{36}$/i.test(postId)) throw new Error("INVALID:معرّف المنشور غير صالح.");
    const exists = await db().query("SELECT id FROM community_posts WHERE id=$1 AND status='published'",[postId]);
    if (!exists.rowCount) return res.status(404).json({error:"not_found"});
    const body = req.body && typeof req.body === "object" ? req.body as Record<string,unknown> : {};
    const reaction = body.reaction === "love" || body.reaction === "inspiring" ? body.reaction : "like";
    const client = await db().connect();
    try {
      await client.query("BEGIN");
      const prior = await client.query("SELECT reaction FROM community_reactions WHERE post_id=$1 AND user_id=$2 FOR UPDATE",[postId,userId]);
      if (prior.rowCount && prior.rows[0].reaction === reaction) {
        await client.query("DELETE FROM community_reactions WHERE post_id=$1 AND user_id=$2",[postId,userId]);
        await client.query("COMMIT");
        return res.status(200).json({active:false,reaction:null});
      }
      await client.query(
        "INSERT INTO community_reactions(post_id,user_id,reaction) VALUES($1,$2,$3) ON CONFLICT(post_id,user_id) DO UPDATE SET reaction=EXCLUDED.reaction,created_at=now()",
        [postId,userId,reaction]
      );
      await client.query("COMMIT");
      return res.status(200).json({active:true,reaction});
    } catch(error) { await client.query("ROLLBACK"); throw error; }
    finally { client.release(); }
  } catch(error) { return respondError(res,error); }
}
