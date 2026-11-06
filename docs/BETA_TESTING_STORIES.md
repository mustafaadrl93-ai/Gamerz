# Gamerz — Beta story viewer and tester checklist

## What's new
- Edge-to-edge fullscreen story player with 5 seconds per image, segmented progress, tap to skip/back, press-and-hold pause, swipe down to dismiss, and close icon.
- Gear menu: story owner/admin may remove stories (confirmation required); other users may report a story for owner moderation. Sharing the story URL is available.
- Up to ten photos per upload batch; each is an independent 24-hour story, grouped by author in the story rail. Multiple batches are allowed.
- Following-only feed alongside Explore, player username search, and sharing game review images.
- In-app beta feedback from Profile, reviewed privately by owner under Admin.
- No previously published stories or posts are deleted by this update.

## Mandatory Firebase action (Gamerz Firebase project)
1. Open https://console.firebase.google.com/project/gamerz-84c61/firestore/rules
2. Copy the **entire** new file https://github.com/mustafaadrl93-ai/Gamerz/blob/main/firestore.rules (use Raw).
3. Replace the old rules in Firebase, then click Publish.
4. The updated rules add /storyReports and /feedback. Without them, in-app reporting and feedback will fail with PERMISSION_DENIED.
5. Existing post and user rules remain; new story uploads use the same /stories schema, same Uploadcare CDN.

## Beta testing from Android
1. Install the newest debug APK over the previous version, keeping app data.
2. Sign in with two different accounts on separate devices.
3. Post 3 photos as stories in one batch. Open the story bubble: each photo fills the screen for 5 seconds, then advances. After the final photo, the viewer closes (or advances to another player's active story).
4. Tap left/right to navigate, hold to pause, and swipe down to dismiss.
5. The story owner should see Delete only inside the gear menu; confirm Delete and ensure the story disappears.
6. The other account should see Report in the gear menu; submit a reason and verify it appears in the owner's Admin dashboard.
7. Verify photo uploads, old posts, title validation, follows/unfollows, profiles, search, likes, threaded comments and account deletion.
8. Open Profile > أرسل ملاحظة أو بلّغ عن مشكلة, submit a bug report, and verify it appears in Admin.
9. Test airplane-mode recovery, rapid tapping, long captions, up to ten pictures, slow mobile data, and Arabic RTL layout.

## Before launching publicly
- This GitHub Actions build is **debug signed**, not a production Play Store release.
- Provide a live privacy-policy URL and accurate data-safety declarations, test in-app deletion, support email, screenshot set and store assets.
- Validate Firebase security rules against malicious clients, image moderation and spam handling, plus Uploadcare trial/quota status.
- Check release signing, Play Integrity App Check, crash reporting, Google Sign-In setup if advertised, and Google Play review requirements.
- Do not store Uploadcare secret keys or service-account credentials in this public GitHub repository.

## Additional Gamerz 2.0 features
See [Gamerz 2.0 beta test guide](BETA_TESTING_GAMERZ_2.md) for the self-reported trophy confirmation, private library, weekly challenges and saved posts.
