# Gamerz Orbit — Beta validation checklist (2026-11-07)

## What's included
- Original Gamerz Orbit top/bottom navigation and redesigned achievements page.
- Nine rank names and nine different geometric vector sigils created with Compose Canvas; there are no third-party logos in these rank marks.
- First eight ranks are accessible through gameplay/community participation; the ninth, **مؤسس النواة**, belongs exclusively to Firebase Auth UID **NdoyBz3UMcWGWQ3DhuwrIbVbZvl2**. Other profiles cannot obtain this rank through XP, purchase or attendance.
- Cosmetic XP formula: 20 per post, 85 per distinct completed game, 150 per distinct claimed PlayStation platinum, 22 per recorded UTC check-in, 10 per current consecutive day (up to 30), 6 per follower (up to 300).
- Progress thresholds for levels 1–8: 0, 80, 250, 650, 1600, 3500, 7800, 18000 XP.
- Ordinary players' effective XP falls by **2% per inactive day after day three**, capped at 80% of the total. After returning, the penalty is recalculated; base achievements are not deleted. Founder rank never decays.
- Daily UTC attendance is a Firestore document with id `YEAR-DAY_OF_YEAR`, immutable and validated with server timestamp. This prevents repeated check-ins on one UTC day.
- Thirteen quests for daily/weekly attendance, publishing, completion, trophy claims, followership, and engagement on last six posts.
- Future membership preview page: no prices, transactions, subscriptions, paid rewards, or entitlement grants are enabled.
- Old on-screen note about the 500-post query cap is removed; the app still limits its internal rank query for beta-scale performance.

## REQUIRED: Firebase
Go to https://console.firebase.google.com/project/gamerz-84c61/firestore/rules
Replace **all** current Firestore Rules with:
https://raw.githubusercontent.com/mustafaadrl93-ai/Gamerz/main/firestore.rules
and press **Publish**.

The new `users/{uid}/checkins/{dayId}` collection is covered only in this rules revision. Check-in documents are normally immutable; account deletion removes the user profile and then its attendance records. Without publishing it, the attendance action will fail with permission denied.

## Test using Android only
1. Install the newest debug APK on top of the previous version without clearing user data.
2. Profile > **✦ مسار الرتب والتحديات**: confirm the new full-screen rank hero, nine unique sigils, XP progress, gold trophies and varied mission cards. There should be no old 500-post disclaimer and no permanent stale validation error below the cards.
3. Tap **سجّل حضوري**: confirm you receive today's check-in; reopening the screen must show **تم ✓** and may not create a second document. Verify the /users/<uid>/checkins/ collection in Firestore.
4. Open a second account: it must not have rank nine, and cannot create check-ins for someone else's UID. A stale account should lose effective XP after more than three days without posts or check-ins. New activity should restore its effective score.
5. Open the owner account: confirm founder title immediately, even with zero posts or no recent attendance.
6. Publish a game with 100% completion and a self-declared trophy: compare the trophy display and task completion. Self-reported trophy totals are not official certification.
7. Check challenges on Monday boundaries (UTC), and confirm existing post/feed/story/comment/follow/block flows still work.
8. Open Profile > **عضوية Gamerz**: the free tier should be marked free, and future tiers marked **قريبًا** with no payment buttons or invented pricing.
9. Verify text wraps without clipping on a small Android display and in Arabic RTL.

## Limitations before public release
- This is a client-calculated cosmetic rank, not a trustworthy server-awarded monetary or security credential. A determined attacker can generate repeated legitimate actions via the API; add server-side rate limits and moderation before public ranking competitions or rewards.
- Check-ins are visible to signed-in users to support public profile ranks; update the privacy policy accordingly.
- XP and challenge counts examine up to 500 user posts, 366 check-ins and six recent posts for engagement. At scale, migrate to trusted backend aggregates.
- Check-in days use UTC and can differ from the local midnight in Bahrain. The app labels the policy and challenge week accordingly.
- Google Play in-app billing is not active. See `docs/GOOGLE_PLAY_SUBSCRIPTIONS.md`.
