# Gamerz | قيمرز

Native Android social app for gamers: Kotlin + Jetpack Compose, Firebase Authentication / Firestore and Uploadcare images.

## Implemented in Android source

- Email/password registration and login (Google Sign-In intentionally not used).
- PlayStation, Xbox and PC onboarding and profile badges.
- Neon dark-purple UI with a bottom navigation bar.
- Personal profile, avatar upload and a gallery of the player's recent posts.
- Game posts with required image, **required 1–5 star rating**, required 0–100% completion, colored progress bar, and optional review.
- Feed with Instagram-style pull-to-refresh, story rings, full-screen story viewer and 24-hour story display.
- Story owner and administrator delete controls.
- Discover/search page for recently loaded game posts.
- Firestore-backed per-account likes and moderated comment threads.
- In-app activity tab showing recent likes and comments on the user's loaded posts (not push notifications).
- Reporting and owner-only moderation panel.
- In-app account deletion with password reauthentication and removal of the user's Firebase profile, post and story documents. A protected **pending media removal request** is created for Uploadcare images.

## Critical: publish Firestore rules

The Android APK does **not** automatically publish database rules to Firebase. If the rules on the Firebase project are older, creating a rated post, seeing stories, commenting, changing avatars and account deletion can fail with PERMISSION_DENIED.

On a phone:
1. Open [Firestore Rules (project gamerz-84c61)](https://console.firebase.google.com/project/gamerz-84c61/firestore/rules).
2. Copy the **entire** [firestore.rules](firestore.rules) file from the repository's main branch.
3. Replace the existing rules with the latest file and tap **Publish**.
4. Test using two actual Firebase users: post, image, story, view, delete, comment, like, report and account deletion.

The new story rules authorize signed-in reads while hiding expired stories in the app and let a story's owner delete it. Story expiration does not automatically delete Uploadcare files or Firestore documents.

## APK builds

GitHub Actions builds a debug APK on PRs and main. PRs are merged only after a successful debug build. Main releases publish an APK download asset. The workflow caches its **test-only debug signing key**, so repeated builds normally install over one another; the first cached build may require uninstalling an older APK if the prior signature differs. The build run number increases Android versionCode/versionName.

Debug builds are **not** Play Store release builds. A private production signing key and signed release AAB are still needed.

## Security and data deletion

Firebase security rules validate owners and fields. Client UI hiding is not a security boundary.

Account deletion removes the user's authentication, profile, posts and stories from Firebase on a best-effort basis after password verification, but **does not automatically purge Uploadcare files**. These files require a trusted, separately configured deletion workflow using a private API credential. User interactions on other people's posts, abuse reports, and backup retention also need a complete audited deletion/retention process before launch. Never place Uploadcare secret keys or Firebase service-account credentials in the public repository or Android app.

## Still required before public release

- Deploy and test the latest Firestore rules and configure App Check correctly for release.
- Complete English/Arabic resource localization, RTL testing, accessibility and device test coverage.
- Push notifications, user search and wider discovery, follow/block tools (the activity tab is not real-time push).
- Complete account/data/media deletion, including interactions on other users' posts, and remove orphaned uploaded files.
- Final privacy policy and verified contact address (the current [policy](docs/privacy-policy.md) is a draft).
- Test the Firebase and Uploadcare quotas, abuse/rate limits and account recovery.
- Prepare a private signing key, release AAB, store listing and Play Console compliance review.

