# Gamerz 2.0 — Android beta acceptance checklist

## Mandatory: publish current Firestore rules
- Open https://console.firebase.google.com/project/gamerz-84c61/firestore/rules
- Copy the *entire* updated https://raw.githubusercontent.com/mustafaadrl93-ai/Gamerz/main/firestore.rules
- Replace the previous rules and click **Publish**.
- This revision is essential: old rules reject platinum=true and deny new private library/bookmark/block documents.
- No cloud-side automatic deployment is configured. GitHub rules do NOT change Firebase until the console update.

## Core platinum scenario 🏆
1. Create or use a profile with PlayStation selected. On Compose choose **PlayStation** as the *post* platform.
2. Set completion 99%. There must be **no platinum question**.
3. Set completion **100%**. Check that **"🏆 هل حققت البلاتينيوم؟"** appears.
4. Leave unchecked: publish. No trophy should be awarded.
5. Check it for another PlayStation 100% game: publish. Its post gets 🏆 and the golden cup count appears in your profile and on public profiles.
6. Publish the same game title twice with platinum checked: trophy counter should count **one distinct game**.
7. Publish Xbox or PC game at 100%: no platinum question or trophy.
8. Check rules from a non-owner test account: try to create a post with platinum=true + 90% or Xbox; Firestore must deny that write. Verify approved PlayStation + 100% works.
9. Change account-selected platforms and confirm posts can use any profile platform.

**Important:** Platinum is a voluntary self-reported accomplishment, not Sony PSN trophy verification; the UI and privacy text must never imply official validation.

## Social and engagement
1. Profile > 🎮 مكتبتي: add an upcoming, playing, and completed game; filter them; delete one.
2. Profile > 🔖 المحفوظات: bookmark a home-feed post, open the saved screen, remove it, then refresh.
3. Profile > 🏆 achievements: see posts, distinct games, completed games, platinum cups, and weekly cards. Check weekly reset (new week starts Monday).
4. Another player's profile: follow, then block. Their posts/stories/search results should disappear after refresh; the follow link should be removed; new follows should be denied by rules. Unblock as needed.
5. Activity: view incoming likes/comments/followers and tap "تحديث النشاط". This is **not a push notification**.
6. Discover: filter PlayStation/Xbox/PC, toggle "completed only", and search player names. Search is limited to loaded feed posts and prefix matches for players.
7. Retest existing stories (full screen, five-second slides, multiple photos, swipe-down), report/messaging, account deletion, new-user registration, photo uploads, account profile changes.

## Android QA
- Test Arabic RTL on small and large devices, long game titles, slow networks and offline reopening.
- Confirm APK upgrades with existing signature without uninstalling (keep app data).
- Confirm 100% posting does not submit platinum=true unless explicitly checked.
- Use two users to test that private libraries and bookmarks are unreadable to the other account.
- Verify delete-account removes library, saved, and block list documents (Uploadcare deletion requests still need manual fulfilment).

## Not finished / launch gates
- Push notifications: needs trusted FCM sender/backend and token handling.
- Strict blocking across all reads, likes, comments, and existing interactions needs additional server-side enforcement.
- Automatic image storage deletion, server-side rate limiting/spam, moderation, and production Firebase App Check require further hardening.
- Profile trophy totals and weekly challenges currently inspect up to 500 posts; they are not the platform's authoritative scalable counts.
- Google Play release requires signed **AAB**, privacy policy with real contact, support, accurate Data Safety form, testing, reviews, and store assets.
- Debug APK is only for testers; Play Store release is NOT ready.
