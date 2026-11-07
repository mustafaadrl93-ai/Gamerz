import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const OWNER_UID = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2";
const PUBLIC_KEY = "a019be01bbe664bae2db";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function response(status, message) {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
  });
}

function getDb() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error("Missing Firebase server credentials");
  if (!getApps().length) initializeApp({ credential: cert(JSON.parse(raw)) });
  return getFirestore();
}

function imageId(data) {
  const path = data.storagePath;
  if (typeof path === "string" && path.startsWith("uploadcare/")) {
    const id = path.slice("uploadcare/".length);
    return UUID.test(id) ? id : null;
  }
  const url = data.imageUrl || data.imageURL || data.image;
  if (typeof url !== "string") return null;
  const match = url.match(/^https:\/\/(?:ucarecdn\.com|26vsxjozef\.ucarecd\.net)\/([0-9a-f-]{36})(?:\/|$)/i);
  return match && UUID.test(match[1]) ? match[1] : null;
}

export default async (request) => {
  if (request.method !== "POST") return response(405, "Method not allowed");
  const token = /^Bearer (.+)$/i.exec(request.headers.get("authorization") || "")?.[1];
  if (!token) return response(401, "Authentication required");
  try {
    getDb();
    const user = await getAuth().verifyIdToken(token, true);
    const body = await request.json();
    const collection = body?.type === "post" ? "posts" : body?.type === "story" ? "stories" : null;
    const id = body?.id;
    if (!collection || typeof id !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(id))
      return response(400, "Invalid request");
    const ref = getFirestore().collection(collection).doc(id);
    const snapshot = await ref.get();
    if (!snapshot.exists) return response(404, "Not found");
    const data = snapshot.data();
    if (data.uid !== user.uid && user.uid !== OWNER_UID) return response(403, "Forbidden");
    const fileId = imageId(data);
    if (!fileId) return response(422, "No verified Uploadcare image on document");
    const secret = process.env.UPLOADCARE_SECRET_KEY;
    if (!secret) throw new Error("Missing Uploadcare server credentials");
    const upstream = await fetch(`https://api.uploadcare.com/files/${fileId}/`, {
      method: "DELETE",
      headers: {
        Authorization: `Uploadcare.Simple ${PUBLIC_KEY}:${secret}`,
        Accept: "application/vnd.uploadcare-v0.7+json"
      },
      signal: AbortSignal.timeout(12000)
    });
    if (!upstream.ok && upstream.status !== 404) {
      console.error("Uploadcare delete failed", upstream.status);
      return response(502, "Media deletion failed; document preserved");
    }
    await ref.delete();
    return response(200, "Deleted");
  } catch (error) {
    console.error("Delete media failed", error?.message);
    return response(500, "Unable to delete media");
  }
};
