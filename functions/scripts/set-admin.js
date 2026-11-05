// Run from a trusted machine after Firebase Admin credentials are configured.
// Usage: GOOGLE_APPLICATION_CREDENTIALS=/secure/service-account.json node scripts/set-admin.js FIREBASE_USER_UID
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

async function main() {
  const uid = process.argv[2];
  if (!uid) throw new Error("Pass the owner's Firebase Auth UID.");
  initializeApp({ credential: applicationDefault() });
  const user = await getAuth().getUser(uid);
  await getAuth().setCustomUserClaims(uid, { ...(user.customClaims || {}), admin: true });
  console.log("Admin claim granted to UID:", uid);
}
main().catch((error) => { console.error(error); process.exit(1); });
