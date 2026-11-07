// Gamerz server-side media deletion. Never put Uploadcare secret in Android or GitHub.
// POST /.netlify/functions/delete-media
// Authorization: Firebase ID token; ownership checked against the Firestore document.
// This endpoint deletes the Uploadcare file first, then its Firestore record.
// It deliberately does NOT accept arbitrary file UUIDs from the caller.
const PROJECT_ID = "gamerz-84c61";
const OWNER_UID = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2";
const PUBLIC_KEY = "a019be01bbe664bae2db";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function reply(statusCode, body) {
  return { statusCode, headers: { "content-type": "application/json", "cache-control": "no-store" }, body: JSON.stringify(body) };
}

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") return reply(405, { error: "Method not allowed" });
  const apiKey = process.env.FIREBASE_WEB_API_KEY;
  const secret = process.env.UPLOADCARE_SECRET_KEY;
  if (!apiKey || !secret) return reply(503, { error: "Media deletion not configured" });

  const token = /^Bearer (.+)$/.exec(event.headers.authorization || event.headers.Authorization || "")?.[1];
  if (!token) return reply(401, { error: "Authentication required" });
  let data;
  try { data = JSON.parse(event.body || "{}"); } catch { return reply(400, { error: "Invalid JSON" }); }
  const { type, id } = data;
  if (!["posts", "stories"].includes(type) || typeof id !== "string" || !/^[a-zA-Z0-9]{1,128}$/.test(id)) {
    return reply(400, { error: "Invalid media reference" });
  }

  try {
    // Google's Identity Toolkit validates the Firebase ID token and returns its UID.
    const authResponse = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(apiKey)}`,
      { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ idToken: token }) }
    );
    if (!authResponse.ok) return reply(401, { error: "Invalid or expired sign-in" });
    const user = (await authResponse.json()).users?.[0];
    if (!user?.localId) return reply(401, { error: "Invalid sign-in" });
    // Safety gate: unsigned Uploadcare uploads do not prove file ownership. Until
    // signed uploads and a trusted upload registry are added, only the project
    // owner may trigger remote deletion to prevent cross-user file removal.
    if (user.localId !== OWNER_UID) return reply(403, { error: "Media deletion is not enabled for this account yet" });

    const docUrl = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/${type}/${id}`;
    const firestoreHeaders = { authorization: `Bearer ${token}` };
    const docResponse = await fetch(docUrl, { headers: firestoreHeaders });
    if (docResponse.status === 404) return reply(404, { error: "Content not found" });
    if (!docResponse.ok) return reply(502, { error: "Unable to verify content ownership" });
    const doc = await docResponse.json();
    const uid = doc.fields?.uid?.stringValue;
    if (!uid || (uid !== user.localId && user.localId !== OWNER_UID)) return reply(403, { error: "Not your content" });

    const imageUrl = doc.fields?.imageUrl?.stringValue || "";
    const storedPath = doc.fields?.storagePath?.stringValue || "";
    const uuidFromUrl = /^https:\/\/(?:ucarecdn\.com|26vsxjozef\.ucarecd\.net)\/([0-9a-f-]{36})\/(?:.*)?$/i.exec(imageUrl)?.[1];
    const uuidFromPath = /^uploadcare\/([0-9a-f-]{36})$/i.exec(storedPath)?.[1];
    const uuid = uuidFromPath || uuidFromUrl;
    if (!uuid || !UUID_RE.test(uuid) || (uuidFromPath && uuidFromUrl && uuidFromPath !== uuidFromUrl)) {
      return reply(422, { error: "Image reference is not valid" });
    }

    const uploadcareResponse = await fetch(`https://api.uploadcare.com/files/${uuid}/`, {
      method: "DELETE",
      headers: {
        Authorization: `Uploadcare.Simple ${PUBLIC_KEY}:${secret}`,
        Accept: "application/vnd.uploadcare-v0.7+json"
      }
    });
    if (!uploadcareResponse.ok && uploadcareResponse.status !== 404) {
      return reply(502, { error: "Image deletion failed; content kept for retry" });
    }

    // Firestore rules enforce owner/admin deletion independently.
    const deleteResponse = await fetch(docUrl, { method: "DELETE", headers: firestoreHeaders });
    if (!deleteResponse.ok && deleteResponse.status !== 404) {
      // Uploadcare deletion already happened; a retry will be safe (404 on image).
      return reply(502, { error: "Image deleted, but content deletion needs retry" });
    }
    return reply(200, { deleted: true });
  } catch (error) {
    console.error("Media deletion error", error?.message || "unknown");
    return reply(502, { error: "Temporary deletion error" });
  }
};
