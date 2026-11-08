import type { VercelRequest, VercelResponse } from "@vercel/node";
import { db, requireUser, respondError } from "../../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId=await requireUser(req);
    const moderators=(process.env.COMMUNITY_MODERATOR_SUBJECTS || "").split(",").map(v=>v.trim()).filter(Boolean);
    if(!moderators.includes(userId)) return res.status(403).json({error:"forbidden",message:"هذه العملية مخصصة لمشرفي المجتمع."});
    if(req.method==="GET") {
      const result=await db().query(
        "SELECT id,reporter_id,target_type,target_id,reason,details,status,created_at FROM community_reports WHERE status='pending' ORDER BY created_at ASC LIMIT 100"
      );
      return res.status(200).json({reports:result.rows});
    }
    if(req.method==="POST") {
      const body=req.body && typeof req.body==="object" ? req.body as Record<string,unknown> : {};
      const targetType=body.targetType;
      const targetId=typeof body.targetId==="string" ? body.targetId : "";
      const action=body.action;
      if(!["post","comment"].includes(String(targetType)) || !targetId || targetId.length>255) throw new Error("INVALID:العنصر المحدد غير صالح.");
      if(!["hide","restore","dismiss"].includes(String(action))) throw new Error("INVALID:إجراء الإشراف غير صالح.");
      if(action==="hide" || action==="restore") {
        const table=targetType==="post" ? "community_posts" : "community_comments";
        const status=action==="hide" ? "hidden" : "published";
        await db().query("UPDATE "+table+" SET status=$1 WHERE id=$2",[status,targetId]);
      }
      await db().query("UPDATE community_reports SET status=$1 WHERE target_type=$2 AND target_id=$3 AND status='pending'",
        [action==="dismiss" ? "dismissed" : "reviewed",targetType,targetId]);
      return res.status(200).json({success:true});
    }
    return res.status(405).setHeader("Allow","GET, POST").json({error:"method_not_allowed"});
  } catch(error) { return respondError(res,error); }
}
