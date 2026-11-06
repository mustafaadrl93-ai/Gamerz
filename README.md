# Gamerz | قيمرز

Native Android social app for gamers: Kotlin + Jetpack Compose, Firebase Authentication / Firestore and Uploadcare images.

## Implemented in Android source

- Email/password registration and login (Google Sign-In intentionally not used).
- PlayStation, Xbox and PC onboarding and profile badges.
- Neon dark-purple UI with a bottom navigation bar.
- Personal profile, avatar upload, a gallery of the player's recent posts, and a 🏆 golden trophy count shown on every player's profile.
- PlayStation post completion at 100% asks "هل حققت البلاتينيوم؟"; the user can claim a platinum, counted once per distinct game title. The claim is self-reported and not verified by Sony.
- Private manual game library (planned / playing / completed); private saved post bookmarks; player blocking/hiding from local feed and stories. Existing follows are removed when blocking.
- Weekly challenges for making a post, completing a game and achieving a PlayStation platinum; earned badge cards based on player posts.
- Profile shortcuts to achievements, game library, and bookmarks.
- Game posts with required title (2–100 chars), required image, **required 1–5 star rating**, required 0–100% completion, colored progress bar, and optional review.
- Feed with pull-to-refresh, Explore/Following filters, shareable game review cards, story rings and 24-hour story display.
- Immersive edge-to-edge stories: five seconds per photo, segmented progress, tap to navigate, hold to pause, pull down to close, and settings gear for deletion/reporting/sharing.
- Publish up to ten photos in one story batch; additional batches can be added throughout the day.
- Story owner and administrator delete controls.
- Discover/search by games, post titles and platform, plus username prefix discovery, platform and completion filters, and player profiles with follow/unfollow counts.
- Firestore-backed per-account likes and moderated comment threads.
- In-app activity tab showing recent likes and comments on the user's loaded posts (not push notifications).
- Reporting of posts and stories, in-app beta feedback submission, and owner-only moderation panels.
- In-app account deletion with password reauthentication and removal of the user's Firebase profile, post and story documents. A protected **pending media removal request** is created for Uploadcare images.

## Critical: publish Firestore rules

The Android APK does **not** automatically publish database rules to Firebase. If the rules on the Firebase project are older, creating a rated post, seeing stories, commenting, changing avatars and account deletion can fail with PERMISSION_DENIED.

On a phone:
1. Open [Firestore Rules (project gamerz-84c61)](https://console.firebase.google.com/project/gamerz-84c61/firestore/rules).
2. Copy the **entire** [firestore.rules](firestore.rules) file from the repository's main branch.
3. Replace the existing rules with the latest file and tap **Publish**.
4. Test using two actual Firebase users: post, image, story, view, delete, comment, like, report and account deletion.

The new rules authorize self-reported platinum claims only for PlayStation posts marked 100% completed; provide private user game library, bookmarks and block lists; and prevent new follows between users who have blocked each other. See [Gamerz 2.0 beta checklist](docs/BETA_TESTING_GAMERZ_2.md). The existing story rules authorize signed-in reads while hiding expired stories in the app and let a story's owner delete it. Story expiration does not automatically delete Uploadcare files or Firestore documents.

## APK builds

GitHub Actions builds a debug APK on PRs and main. PRs are merged only after a successful debug build. Main releases publish an APK download asset. The workflow caches its **test-only debug signing key**, so repeated builds normally install over one another; the first cached build may require uninstalling an older APK if the prior signature differs. The build run number increases Android versionCode/versionName.

Debug builds are **not** Play Store release builds. A private production signing key and signed release AAB are still needed.

## Security and data deletion

Firebase security rules validate owners and fields. Client UI hiding is not a security boundary.

Account deletion removes the user's authentication, profile, posts and stories from Firebase on a best-effort basis after password verification, but **does not automatically purge Uploadcare files**. These files require a trusted, separately configured deletion workflow using a private API credential. User interactions on other people's posts, abuse reports, and backup retention also need a complete audited deletion/retention process before launch. Never place Uploadcare secret keys or Firebase service-account credentials in the public repository or Android app.

## Still required before public release

- Deploy and test the latest Firestore rules and configure App Check correctly for release.
- Complete English/Arabic resource localization, RTL testing, accessibility and device test coverage.
- True push notifications need FCM and trusted server-side event handling; the activity tab is **not** push. The current block list hides accounts in the client and prevents new follows, but is not a complete privacy or anti-harassment boundary. Server-side moderation, global search, stronger block enforcement and anti-spam limits remain.
- Complete account/data/media deletion, including interactions on other users' posts, and remove orphaned uploaded files.
- Final privacy policy and verified contact address (the current [policy](docs/privacy-policy.md) is a draft).
- Test the Firebase and Uploadcare quotas, abuse/rate limits and account recovery.
- Prepare a private signing key, release AAB, store listing and Play Console compliance review. The beta now targets Android API 36 (Google Play new-app requirement since August 2026).
- For debug APK beta distribution, Firebase App Check uses its debug provider; if App Check enforcement is enabled, tester devices must be allowed/configured for it, or use a correctly signed Play internal-test release with Play Integrity.
- Use the phone-friendly [beta test checklist](docs/BETA_TESTING_STORIES.md) to validate every feature before launch.

