# Gamerz | قيمرز

Native Android social app for gamers. Kotlin + Jetpack Compose, Firebase backend, Android / Google Play only.

## Phase 1 starter currently committed
- Platform selection: PlayStation, Xbox, PC.
- Email/password sign-up and login.
- User profile document stores username and selected platform.
- Create game-progress posts with a required image, game name and completion 0–100.
- Optional pros and cons.
- Platinum prompt appears only for PlayStation at 100%; server function rejects invalid Platinum claims.
- Images are compressed to JPEG before upload.
- Firestore feed displays the latest 30 posts, including images.
- Arabic and English string resource files; Android RTL support enabled.
- Trusted callable Cloud Function validates post fields, author's saved platform, and uploaded image metadata before creating posts.
- Firestore client-side post creation is denied; only the Admin SDK Cloud Function can create posts.

## Firebase setup (beginner steps)
1. Open https://console.firebase.google.com/ and create a Firebase project named Gamerz. Choose a region close to expected users; Firestore location cannot easily be changed later.
2. Add an Android app with package name `com.gamerz.app`.
3. Download `google-services.json` and place it inside the repository's `app/` folder. Do not upload service-account private keys.
4. In Firebase Authentication → Sign-in method, enable Email/Password.
5. In Firestore Database, create a database in the chosen region.
6. In Storage, create the default bucket.
7. Install Node.js 20 and Firebase CLI, sign in with `firebase login`, then from this project folder run `npm install --prefix functions`.
8. Deploy rules and the Phase 1 callable function with `firebase deploy --only firestore:rules,storage,functions`.
9. Open the project in Android Studio, sync Gradle, then run it on an Android device.

## Phase 1 test checklist
1. Create one account on PlayStation and another on Xbox.
2. Sign in with each account and create a post with an image.
3. Try publishing without an image or with completion 101; the app must reject it.
4. On PlayStation, set completion to 100 and verify the Platinum question appears. Select yes and verify the crown on the post.
5. On Xbox and PC, verify the Platinum question never appears.
6. Try calling `createPost` with an invalid platform / Platinum combination; the server must reject it.
7. Check the feed from both accounts.

## Not yet complete (planned follow-up)
- Google sign-in, unique username reservation enforced transactionally on the server, account deletion.
- App Check enforcement (enable after registering debug and Play Integrity providers).
- Server-side image safety checks, moderation, appeals, reporting, blocking and rate limits.
- Pull-to-refresh gesture, live feed listener, offline polish, full bilingual UI audit and accessibility.
- Firestore Emulator security-rule tests and two-account private-data tests.
- Pros/cons custom text styling, star rating, and additional post details.
- Premium, intentionally deferred until core features work.

## Security notes
- Never commit service-account credentials or API secrets. Never put service-account credentials in the Android app.
- The callable function currently has App Check enforcement disabled for initial setup; enable it before public release.
- Firestore and Storage rules are starter rules and must be reviewed with Emulator Suite tests before production launch.