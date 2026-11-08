import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../../../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId = await requireUser(req);
    if (req.method !== "POST") return res.status(405).setHeader("Allow","POST").json({error:"method_not_allowed"});
    const target = typeof req.body?.userId === "string" ? req.body.userId.trim() : "";
    if (!target || target.length > 255 || target === userId) throw new Error("INVALID:حساب المتابعة غير صالح.");
    const client=await db().connect();
    try {
      await client.query("BEGIN");
      const prior=await client.query("SELECT 1 FROM community_follows WHERE follower_id=$1 AND followed_id=$2 FOR UPDATE",[userId,target]);
      if (prior.rowCount) {
        await client.query("DELETE FROM community_follows WHERE follower_id=$1 AND followed_id=$2",[userId,target]);
        await client.query("COMMIT");
        return res.status(200).json({following:false});
      }
      await client.query("INSERT INTO community_follows(follower_id,followed_id) VALUES($1,$2) ON CONFLICT DO NOTHING",[userId,target]);
      await client.query("COMMIT");
      return res.status(200).json({following:true});
    } catch(error) { await client.query("ROLLBACK"); throw error; }
    finally { client.release(); }
  } catch(error) { return respondError(res,error); }
}
