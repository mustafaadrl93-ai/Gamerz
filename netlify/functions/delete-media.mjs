// Authenticated media removal from Gamerz Firestore and Uploadcare.
// No external UUID is accepted from the Android caller.
import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

const OWNER = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2";
const PUBLIC_KEY = "a019be01bbe664bae2db";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PREFIXES = ["https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"];
const reply = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
});

function database() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error("Firebase credential missing");
  if (!getApps().length) initializeApp({ credential: cert(JSON.parse(raw)) });
  return getFirestore();
}
function idFromPost(data) {
  const fromPath = typeof data.storagePath === "string" ?
    data.storagePath.match(/^uploadcare\/([0-9a-f-]{36})$/i)?.[1] : null;
  const url = typeof data.imageUrl === "string" ? data.imageUrl : "";
  const fromUrl = url.match(/^https:\/\/(?:ucarecdn\.com|26vsxjozef\.ucarecd\.net)\/([0-9a-f-]{36})\/(?:.*)?$/i)?.[1];
  if (fromPath && fromUrl && fromPath.toLowerCase() !== fromUrl.toLowerCase()) return null;
  const id = fromPath || fromUrl;
  return id && UUID.test(id) ? id.toLowerCase() : null;
}
async function hasOtherReference(db, id, targetPath) {
  const imageUrls = PREFIXES.map((prefix) => prefix + id + "/");
  const posts = await db.collection("posts").where("storagePath", "==", "uploadcare/" + id)
    .limit(5).get();
  if (posts.docs.some((d) => d.ref.path !== targetPath)) return true;
  const stories = await db.collection("stories").where("imageUrl", "in", imageUrls)
    .limit(5).get();
  if (stories.docs.some((d) => d.ref.path !== targetPath)) return true;
  const users = await db.collection("users").where("avatarStoragePath", "==", "uploadcare/" + id)
    .limit(5).get();
  return users.docs.some((d) => d.ref.path !== targetPath);
}
export default async (request) => {
  if (request.method !== "POST") return reply(405, { error: "POST required" });
  const bearer = /^Bearer (.+)$/i.exec(request.headers.get("authorization") || "")?.[1];
  if (!bearer) return reply(401, { error: "Authentication required" });
  try {
    const db = database();
    const identity = await getAuth().verifyIdToken(bearer, true);
    const body = await request.json();
    const type = body?.type;
    const id = body?.id;
    const collection = type === "post" ? "posts" : type === "story" ? "stories" :
      type === "avatar" ? "users" : null;
    if (!collection || typeof id !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(id))
      return reply(400, { error: "Invalid reference" });
    if (type === "avatar" && id !== identity.uid && identity.uid !== OWNER)
      return reply(403, { error: "Not authorized" });
    const ref = db.collection(collection).doc(id);
    const snapshot = await ref.get();
    if (!snapshot.exists) return reply(404, { error: "Content not found" });
    const data = snapshot.data();
    if (type !== "avatar" && data.uid !== identity.uid && identity.uid !== OWNER)
      return reply(403, { error: "Not authorized" });
    const fileData = type === "avatar" ?
      { storagePath: data.avatarStoragePath, imageUrl: data.avatarUrl } : data;
    const fileId = idFromPost(fileData);
    let imageDeleted = false;
    let retainedSharedImage = false;
    if (fileId) {
      retainedSharedImage = await hasOtherReference(db, fileId, ref.path);
      if (!retainedSharedImage) {
        const secret = process.env.UPLOADCARE_SECRET_KEY;
        if (!secret) return reply(503, { error: "Uploadcare not configured" });
        const result = await fetch("https://api.uploadcare.com/files/" + fileId + "/", {
          method: "DELETE",
          headers: {
            Authorization: "Uploadcare.Simple " + PUBLIC_KEY + ":" + secret,
            Accept: "application/vnd.uploadcare-v0.7+json"
          },
          signal: AbortSignal.timeout(15000)
        });
        if (!result.ok && result.status !== 404) {
          console.error("Uploadcare delete failure:", result.status);
          return reply(502, { error: "Could not delete media; content preserved" });
        }
        imageDeleted = true;
      }
    }
    if (type === "avatar") {
      await ref.update({ avatarUrl: FieldValue.delete(),
        avatarStoragePath: FieldValue.delete() });
    } else {
      await db.recursiveDelete(ref);
    }
    return reply(200, { deleted: true, imageDeleted, retainedSharedImage });
  } catch (error) {
    console.error("Media cleanup error", error?.message || "unknown");
    return reply(502, { error: "Media cleanup unavailable" });
  }
};
