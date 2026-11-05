const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

initializeApp();
const db = getFirestore();

exports.createPost = onCall({ enforceAppCheck: false }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const uid = request.auth.uid;
  const data = request.data || {};
  const gameName = typeof data.gameName === "string" ? data.gameName.trim() : "";
  const completion = data.completion;
  const imageUrl = typeof data.imageUrl === "string" ? data.imageUrl : "";
  const storagePath = typeof data.storagePath === "string" ? data.storagePath : "";
  const pros = typeof data.pros === "string" ? data.pros.trim().slice(0, 500) : "";
  const cons = typeof data.cons === "string" ? data.cons.trim().slice(0, 500) : "";
  if (!gameName || gameName.length > 80) throw new HttpsError("invalid-argument", "Game name is required (max 80 characters).");
  if (!Number.isInteger(completion) || completion < 0 || completion > 100) throw new HttpsError("invalid-argument", "Completion must be from 0 to 100.");
  if (!imageUrl || !storagePath || !storagePath.startsWith(`postImages/${uid}/`)) throw new HttpsError("invalid-argument", "A game image is required.");
  const userSnap = await db.collection("users").doc(uid).get();
  if (!userSnap.exists) throw new HttpsError("failed-precondition", "Complete your profile first.");
  const platform = userSnap.get("platform");
  if (!["PlayStation", "Xbox", "PC"].includes(platform)) throw new HttpsError("failed-precondition", "Choose a valid gaming platform.");
  const platinum = data.platinum === true;
  if (platinum && !(platform === "PlayStation" && completion === 100)) {
    throw new HttpsError("invalid-argument", "Platinum is only available to PlayStation users at 100% completion.");
  }
  const bucket = getStorage().bucket();
  const [file] = await bucket.file(storagePath).getMetadata().catch(() => [null]);
  if (!file || !String(file.contentType || "").startsWith("image/") || Number(file.size || 0) > 8 * 1024 * 1024) {
    throw new HttpsError("failed-precondition", "Upload a valid image (maximum 8 MB) before publishing.");
  }
  const post = {
    uid, platform, gameName, completion, imageUrl, storagePath, pros, cons,
    platinum, createdAt: FieldValue.serverTimestamp(), likes: 0, commentCount: 0
  };
  const ref = await db.collection("posts").add(post);
  return { postId: ref.id };
});
