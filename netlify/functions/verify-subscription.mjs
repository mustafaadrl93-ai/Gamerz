// Server-trusted verification of Google Play subscriptions.
// No paid entitlement is ever granted from an Android-provided "premium" flag.
import { createHash } from "node:crypto";
import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { GoogleAuth } from "google-auth-library";

const PACKAGE = "com.gamerz.app";
const PRODUCT_IDS = new Set(["gamerz_wamda", "gamerz_sadeem"]);
const API_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const sha256 = (s) => createHash("sha256").update(s).digest("hex");
const reply = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" }
});

function initialize() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON missing");
  const credentials = JSON.parse(raw);
  if (!getApps().length) initializeApp({ credential: cert(credentials) });
  return credentials;
}

export default async (request) => {
  if (request.method !== "POST") return reply(405, { error: "POST required" });
  if (!request.headers.get("content-type")?.includes("application/json"))
    return reply(415, { error: "JSON required" });
  const authHeader = request.headers.get("authorization") || "";
  const token = /^Bearer (.+)$/i.exec(authHeader)?.[1];
  if (!token) return reply(401, { error: "Sign in required" });
  try {
    const credentials = initialize();
    const identity = await getAuth().verifyIdToken(token, true);
    const body = await request.json();
    const purchaseToken = body?.purchaseToken;
    if (typeof purchaseToken !== "string" || purchaseToken.length < 20 ||
        purchaseToken.length > 4096 || !/^[A-Za-z0-9._-]+$/.test(purchaseToken)) {
      return reply(400, { error: "Invalid purchase reference" });
    }
    const auth = new GoogleAuth({ credentials, scopes: [API_SCOPE] });
    const access = await auth.getAccessToken();
    if (!access) return reply(503, { error: "Play Developer API not configured" });
    const url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
      PACKAGE + "/purchases/subscriptionsv2/tokens/" + encodeURIComponent(purchaseToken);
    const google = await fetch(url, {
      headers: { Authorization: "Bearer " + access, Accept: "application/json" },
      signal: AbortSignal.timeout(15000)
    });
    if (!google.ok) {
      console.error("Google Play verification rejected", google.status);
      return reply(503, { error: "Google Play verification unavailable or purchase invalid" });
    }
    const purchase = await google.json();
    const accountId = purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId;
    if (!accountId || accountId !== sha256(identity.uid)) {
      return reply(403, { error: "Purchase is not linked to this Gamerz account" });
    }
    const line = (purchase.lineItems || []).find((p) =>
      PRODUCT_IDS.has(p.productId) && Date.parse(p.expiryTime || "") > Date.now());
    const eligible = new Set([
      "SUBSCRIPTION_STATE_ACTIVE",
      "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
      "SUBSCRIPTION_STATE_CANCELED"
    ]);
    const active = eligible.has(purchase.subscriptionState) && Boolean(line);
    const db = getFirestore();
    const hash = sha256(purchaseToken);
    const receipt = db.collection("billingPurchases").doc(hash);
    const entitlement = db.collection("entitlements").doc(identity.uid);
    await db.runTransaction(async (tx) => {
      const previous = await tx.get(receipt);
      if (previous.exists && previous.get("uid") !== identity.uid) {
        throw new Error("Receipt already linked to another account");
      }
      if (active) {
        tx.set(receipt, {
          uid: identity.uid,
          productId: line.productId,
          verifiedAt: FieldValue.serverTimestamp(),
          expiresAt: new Date(line.expiryTime)
        }, { merge: true });
        tx.set(entitlement, {
          tier: line.productId,
          productId: line.productId,
          active: true,
          expiresAt: new Date(line.expiryTime),
          verifiedAt: FieldValue.serverTimestamp(),
          receiptHash: hash,
          source: "google_play"
        });
      } else if (previous.exists) {
        const current = await tx.get(entitlement);
        if (current.exists && current.get("receiptHash") === hash)
          tx.delete(entitlement);
      }
    });
    // Google Play returns current subscription status; the app must not trust a local flag.
    return reply(200, {
      verified: true,
      active,
      productId: active ? line.productId : null,
      expiresAt: active ? line.expiryTime : null
    });
  } catch (error) {
    console.error("Play verification error:", error.message);
    return reply(503, { error: "Verification could not be completed" });
  }
};
