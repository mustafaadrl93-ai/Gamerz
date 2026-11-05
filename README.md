# Gamerz | قيمرز

Native Android social app for gamers. Kotlin + Jetpack Compose, Firebase Authentication + Cloud Firestore, and Uploadcare for image hosting.

## Current implementation
- Android app package: `com.gamerz.app`.
- Email/password account creation and sign-in.
- Platform selection: PlayStation, Xbox, PC.
- Basic player profile with editable username.
- Community feed with game name, completion percentage, review text, and image.
- Image upload through Uploadcare using the public project key.
- Stories are shown while their expiry timestamp is within 24 hours.
- Users can report posts; only the configured owner UID can view and moderate reports.
- Users can delete their own posts; the owner can delete any post and stories.
- Firestore Security Rules are in `firestore.rules`; publish them in the Firebase Console.

## Free-tier architecture
- The app writes directly to Cloud Firestore; these flows do not require callable Cloud Functions.
- Firebase Storage is not used by the current app; uploaded images are hosted by Uploadcare.
- Stories become hidden after 24 hours, but expired Firestore documents are not automatically purged on the Spark plan.
- Deleting a Firestore post or story does not delete its Uploadcare image. A trusted server-side deletion process is still needed for full media deletion.
- The public Uploadcare key is embedded in the app. Never embed an Uploadcare secret key or Firebase service-account credentials in the Android app or repository.

## Not implemented yet
- Google Sign-In.
- Actual language switcher / complete Arabic-English UI localization (resource files exist, but most screen text is still hard-coded).
- Profile photo/avatar.
- Likes, comments, search, follow/block, and notifications.
- Automatic deletion of expired story records and Uploadcare files.
- Account deletion flow and complete user-data export/deletion handling.
- Firestore Emulator security-rule tests and verified two-account/device testing.
- App Check enforcement verification for both debug and Play Integrity release builds.
- Signed release APK / Android App Bundle and Google Play publication.
- Final privacy-policy contact details and public hosting of the policy.

## Build
GitHub Actions workflow: `.github/workflows/android-build.yml`
- Builds a **debug APK** on pushes to `main` and on manual dispatch.
- Artifact name: `Gamerz-debug-apk` (ZIP containing `app-debug.apk`).
- This debug APK is for testing, not a signed production release.

## Privacy policy
A draft is in `docs/privacy-policy.md`. Replace the support contact placeholder, review provider disclosures and retention practices, then publish it at a public URL before public release.

## Release readiness
This project is not yet production-ready. Test account creation/login, image uploads, post/story creation, deletion, reports, owner-only moderation, and security rules on real devices and with the Firestore Emulator before release.
