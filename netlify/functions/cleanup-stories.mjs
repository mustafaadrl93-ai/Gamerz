// Hourly cleanup of expired story documents and their Uploadcare images.
// Scheduled functions cannot be called from the public web in production.
import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

const PUBLIC_KEY = "a019be01bbe664bae2db";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const config = { schedule: "@hourly" };

function database() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error("Firebase service account is missing");
  if (!getApps().length) initializeApp({ credential: cert(JSON.parse(raw)) });
  return getFirestore();
}
function fileId(url) {
  if (typeof url !== "string") return null;
  const match = /^https:\/\/(?:ucarecdn\.com|26vsxjozef\.ucarecd\.net)\/([0-9a-f-]{36})\//i.exec(url);
  return match && UUID.test(match[1]) ? match[1].toLowerCase() : null;
}
async function cleanOne(db, doc, secret) {
  const id = fileId(doc.get("imageUrl"));
  if (id) {
    const urls = ["https://ucarecdn.com/", "https://26vsxjozef.ucarecd.net/"]
      .map((prefix) => prefix + id + "/");
    const [stories, posts, users] = await Promise.all([
      db.collection("stories").where("imageUrl", "in", urls).limit(5).get(),
      db.collection("posts").where("storagePath", "==", "uploadcare/" + id).limit(1).get(),
      db.collection("users").where("avatarStoragePath", "==", "uploadcare/" + id).limit(1).get()
    ]);
    const shared = stories.docs.some((x) => x.id !== doc.id) ||
      !posts.empty || !users.empty;
    if (!shared) {
      const result = await fetch("https://api.uploadcare.com/files/" + id + "/", {
        method: "DELETE",
        headers: {
          Authorization: "Uploadcare.Simple " + PUBLIC_KEY + ":" + secret,
          Accept: "application/vnd.uploadcare-v0.7+json"
        },
        signal: AbortSignal.timeout(10000)
      });
      if (!result.ok && result.status !== 404)
        throw new Error("Uploadcare deletion rejected: " + result.status);
    }
  }
  await db.recursiveDelete(doc.ref);
}

export default async () => {
  try {
    const db = database();
    const secret = process.env.UPLOADCARE_SECRET_KEY;
    if (!secret) throw new Error("Uploadcare secret is missing");
    const batch = await db.collection("stories")
      .where("expiresAt", "<=", Timestamp.now())
      .orderBy("expiresAt")
      .limit(30).get();
    let removed = 0, errors = 0;
    // Four concurrent remote requests keep the hourly operation within budget.
    for (let index = 0; index < batch.docs.length; index += 4) {
      const results = await Promise.allSettled(
        batch.docs.slice(index, index + 4).map((doc) => cleanOne(db, doc, secret))
      );
      for (const result of results) {
        if (result.status === "fulfilled") removed++;
        else { errors++; console.error("Story cleanup error:", result.reason?.message); }
      }
    }
    console.log("Expired stories processed:", removed, "errors:", errors);
    return new Response(null, { status: 204 });
  } catch (error) {
    console.error("Scheduled story cleanup failed:", error.message);
    return new Response(null, { status: 500 });
  }
};
