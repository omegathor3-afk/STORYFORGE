import type { VercelRequest, VercelResponse } from "@vercel/node";
import { randomUUID } from "node:crypto";
import { db, requireUser, respondError } from "../_lib/runtime";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  try {
    const userId=await requireUser(req);
    if(req.method!=="POST") return res.status(405).setHeader("Allow","POST").json({error:"method_not_allowed"});
    const body=req.body && typeof req.body==="object" ? req.body as Record<string,unknown> : {};
    const targetType=body.targetType;
    const targetId=typeof body.targetId==="string" ? body.targetId.trim() : "";
    const reason=body.reason;
    const details=typeof body.details==="string" ? body.details.trim() : "";
    if(!["post","comment","user"].includes(String(targetType))) throw new Error("INVALID:نوع البلاغ غير صالح.");
    if(!targetId || targetId.length>255) throw new Error("INVALID:العنصر المبلّغ عنه غير صالح.");
    if(!["spam","harassment","violence","copyright","other"].includes(String(reason))) throw new Error("INVALID:سبب البلاغ غير صالح.");
    if(details.length>500) throw new Error("INVALID:تفاصيل البلاغ طويلة جدًا.");
    const id=randomUUID();
    await db().query(
      "INSERT INTO community_reports(id,reporter_id,target_type,target_id,reason,details) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(reporter_id,target_type,target_id) DO NOTHING",
      [id,userId,targetType,targetId,reason,details]
    );
    return res.status(202).json({accepted:true});
  } catch(error) { return respondError(res,error); }
}
