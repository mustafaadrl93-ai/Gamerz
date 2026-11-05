const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");

initializeApp();
const db = getFirestore();

// The sole project owner. Authorization is enforced server-side by Firebase UID.
const OWNER_UID = "NdoyBz3UMcWGWQ3DhuwrIbVbZvl2";

function requireUser(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  return request.auth.uid;
}
function requireAdmin(request) {
  const uid = requireUser(request);
  if (uid !== OWNER_UID) throw new HttpsError("permission-denied", "Owner only.");
}
function validUploadcare(url) {
  return typeof url === "string" && /^https:\/\/ucarecdn\.com\/[a-f0-9-]{20,40}\/$/.test(url);
}

exports.createPost = onCall({ enforceAppCheck: true }, async (request) => {
  const uid = requireUser(request);
  const data = request.data || {};
  const gameName = typeof data.gameName === "string" ? data.gameName.trim() : "";
  const completion = data.completion;
  const imageUrl = data.imageUrl;
  const storagePath = data.storagePath;
  const pros = typeof data.pros === "string" ? data.pros.trim().slice(0, 500) : "";
  const cons = typeof data.cons === "string" ? data.cons.trim().slice(0, 500) : "";
  if (!gameName || gameName.length > 80) throw new HttpsError("invalid-argument", "Game name is required (max 80 characters).");
  if (!Number.isInteger(completion) || completion < 0 || completion > 100) throw new HttpsError("invalid-argument", "Completion must be from 0 to 100.");
  const match = validUploadcare(imageUrl) ? /^https:\/\/ucarecdn\.com\/([a-f0-9-]{20,40})\/$/.exec(imageUrl) : null;
  if (!match || storagePath !== `uploadcare/${match[1]}`) throw new HttpsError("invalid-argument", "A valid Uploadcare image is required.");
  const userSnap = await db.collection("users").doc(uid).get();
  if (!userSnap.exists) throw new HttpsError("failed-precondition", "Complete your profile first.");
  const platform = userSnap.get("platform");
  if (!["PlayStation", "Xbox", "PC"].includes(platform)) throw new HttpsError("failed-precondition", "Choose a valid gaming platform.");
  const platinum = data.platinum === true;
  if (platinum && !(platform === "PlayStation" && completion === 100)) throw new HttpsError("invalid-argument", "Platinum is only for PlayStation at 100%.");
  const ref = await db.collection("posts").add({ uid, platform, gameName, completion, imageUrl, storagePath, pros, cons, platinum, createdAt: FieldValue.serverTimestamp(), likes: 0, commentCount: 0 });
  return { postId: ref.id };
});

exports.createStory = onCall({ enforceAppCheck: true }, async (request) => {
  const uid = requireUser(request);
  const data = request.data || {};
  if (!validUploadcare(data.imageUrl)) throw new HttpsError("invalid-argument", "A valid story image is required.");
  const caption = typeof data.caption === "string" ? data.caption.trim().slice(0, 180) : "";
  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000);
  const ref = await db.collection("stories").add({ uid, imageUrl: data.imageUrl, caption, createdAt: now, expiresAt });
  return { storyId: ref.id, expiresAt: expiresAt.toMillis() };
});

exports.reportPost = onCall({ enforceAppCheck: true }, async (request) => {
  const uid = requireUser(request);
  const data = request.data || {};
  const postId = typeof data.postId === "string" ? data.postId : "";
  const reason = typeof data.reason === "string" ? data.reason.trim().slice(0, 300) : "";
  if (!postId || !reason) throw new HttpsError("invalid-argument", "Post and report reason are required.");
  const post = await db.collection("posts").doc(postId).get();
  if (!post.exists) throw new HttpsError("not-found", "Post not found.");
  if (post.get("uid") === uid) throw new HttpsError("failed-precondition", "You cannot report your own post.");
  const existing = await db.collection("reports").where("postId", "==", postId).where("reporterUid", "==", uid).limit(1).get();
  if (!existing.empty) throw new HttpsError("already-exists", "You already reported this post.");
  const ref = await db.collection("reports").add({ postId, postOwnerUid: post.get("uid"), reporterUid: uid, reason, status: "open", createdAt: FieldValue.serverTimestamp() });
  return { reportId: ref.id };
});

exports.deletePost = onCall({ enforceAppCheck: true }, async (request) => {
  const uid = requireUser(request);
  const postId = typeof request.data?.postId === "string" ? request.data.postId : "";
  if (!postId) throw new HttpsError("invalid-argument", "Post ID required.");
  const ref = db.collection("posts").doc(postId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Post not found.");
  const isOwner = snap.get("uid") === uid;
  const isAdmin = uid === OWNER_UID;
  if (!isOwner && !isAdmin) throw new HttpsError("permission-denied", "You can delete only your own posts.");
  await ref.delete();
  return { deleted: true };
});

exports.adminDeleteStory = onCall({ enforceAppCheck: true }, async (request) => {
  requireAdmin(request);
  const storyId = typeof request.data?.storyId === "string" ? request.data.storyId : "";
  if (!storyId) throw new HttpsError("invalid-argument", "Story ID required.");
  await db.collection("stories").doc(storyId).delete();
  return { deleted: true };
});

exports.adminResolveReport = onCall({ enforceAppCheck: true }, async (request) => {
  requireAdmin(request);
  const reportId = typeof request.data?.reportId === "string" ? request.data.reportId : "";
  const status = request.data?.status;
  if (!reportId || !["reviewed", "dismissed"].includes(status)) throw new HttpsError("invalid-argument", "Valid report ID and status required.");
  await db.collection("reports").doc(reportId).update({ status, reviewedAt: FieldValue.serverTimestamp(), reviewedBy: request.auth.uid });
  return { updated: true };
});

exports.removeExpiredStories = onSchedule("every 60 minutes", async () => {
  const expired = await db.collection("stories").where("expiresAt", "<=", Timestamp.now()).limit(400).get();
  if (expired.empty) return;
  const batch = db.batch();
  expired.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});
