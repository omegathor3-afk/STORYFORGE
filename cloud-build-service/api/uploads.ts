import type { VercelRequest, VercelResponse } from "@vercel/node";
import { randomUUID } from "node:crypto";
import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { requireUser, respondError } from "./_lib/runtime";

let client: S3Client | undefined;
function storage(): { client: S3Client; bucket: string } {
  const account = process.env.R2_ACCOUNT_ID;
  const accessKey = process.env.R2_ACCESS_KEY_ID;
  const secret = process.env.R2_SECRET_ACCESS_KEY;
  const bucket = process.env.R2_BUCKET_NAME;
  if (!account || !accessKey || !secret || !bucket) throw new Error("Missing server configuration: R2 storage");
  if (!client) client = new S3Client({
    region: "auto",
    endpoint: "https://" + account + ".r2.cloudflarestorage.com",
    forcePathStyle: true,
    credentials: { accessKeyId: accessKey, secretAccessKey: secret },
  });
  return { client, bucket };
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") return res.status(405).setHeader("Allow", "POST").json({ error: "method_not_allowed" });
  try {
    await requireUser(req);
    const body = req.body && typeof req.body === "object" ? req.body as Record<string, unknown> : {};
    const fileName = typeof body.fileName === "string" ? body.fileName : "";
    const fileSize = Number(body.fileSize);
    if (!fileName.toLowerCase().endsWith(".storygame")) throw new Error("INVALID:امتداد الملف يجب أن يكون .storygame.");
    if (!Number.isSafeInteger(fileSize) || fileSize < 1 || fileSize > 100 * 1024 * 1024) {
      throw new Error("INVALID:حجم ملف القصة يجب ألا يتجاوز 100 ميجابايت.");
    }
    const { client: s3, bucket } = storage();
    const key = "storygames/" + randomUUID() + ".storygame";
    const uploadUrl = await getSignedUrl(s3, new PutObjectCommand({
      Bucket: bucket, Key: key, ContentType: "application/octet-stream", ContentLength: fileSize,
    }), { expiresIn: 600 });
    const storygameUrl = await getSignedUrl(s3, new GetObjectCommand({
      Bucket: bucket, Key: key, ResponseContentType: "application/octet-stream",
      ResponseContentDisposition: 'attachment; filename="story.storygame"',
    }), { expiresIn: 3600 });
    return res.status(200).json({ uploadUrl, storygameUrl, expiresInSeconds: 600 });
  } catch (error) { return respondError(res, error); }
}
