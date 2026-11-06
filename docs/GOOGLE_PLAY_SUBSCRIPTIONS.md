# Gamerz — Google Play subscriptions integration plan

**Current status:** NON-TRANSACTIONAL PREVIEW ONLY. No price, paid entitlement or checkout is activated in the Android APK. All users keep their core capabilities and earn ranks through participation. The founder level remains owner-only.

## Proposed products (not created in Play Console)
- `gamerz_wamda`: optional visual profile customization (frames and themes).
- `gamerz_sadeem`: optional wider visual customization and personal insights.
- Potential base plans: `monthly` and `yearly` depending on business choices.
- Choose real prices and countries **inside Play Console**, never hard-code or invent amounts. Do not claim a monthly price until it has been approved and activated.
- No feature may grant official platform verification or imply a relationship with a console brand.

## Receiving income in Bahrain
According to Google's supported-location list, **Bahrain supports developer and merchant registration**, and its merchant default currency is **USD**. Google also lists Bahrain as supported for bank-transfer payouts. Setup requires a matching Bahrain merchant payments profile, identification/verification as applicable, and a bank account in the registered country that can receive the required transfer currency. Verify all bank and tax details directly in Play Console before enabling transactions. Do not place bank details or personal identification documents in the public repository.

References:
- https://support.google.com/googleplay/android-developer/answer/9306917
- https://support.google.com/googleplay/android-developer/answer/2700656
- https://support.google.com/googleplay/android-developer/answer/7161440
- https://support.google.com/googleplay/android-developer/answer/3092739

## Steps
1. Finish Play Developer account setup (organization/individual), merchant/payments profile, supported payout bank account, and tax/identity verification as required by Google Play and the country of registration.
2. Upload an internal-test **signed release AAB** for `com.gamerz.app` using a secure private upload key (do not publish credentials in GitHub).
3. In https://play.google.com/console/ select your app > **Monetize with Play > Products > Subscriptions**. Create the subscription product IDs and set base plans, billing periods, country availability and prices. Review regional requirements and cancelation policy.
4. Add current supported Play Billing Library to the Android Gradle file, query `ProductDetails`, display actual store-supplied prices, and launch `BillingClient.launchBillingFlow`. Implement restore/manage/cancel links and transparent renewal terms.
5. Build a trusted backend (for example Firebase Cloud Functions with securely configured Google Play Developer API credentials; this may require enabling billing). Pass purchase tokens to the server, verify via `purchases.subscriptionsv2.get`, handle states and acknowledge purchases only after legitimate confirmation.
6. The server, not the Android client, writes the account entitlement in Firestore. The new `firestore.rules` already contains `/entitlements/{uid}`: the user may get only their own entitlements, queries and ALL client writes are denied. Firebase Admin SDK on the trusted server bypasses Firestore security rules and alone can grant/revoke subscription access. Do not trust a user-written `premium=true` field.
7. Configure Google Play RTDN via Google Cloud Pub/Sub and reconcile renewals, cancellation, expiration, refunds, grace/hold and plan changes with the backend.
8. Test purchases and cancellation with Play Console license testers and internal testing, including offline app restarts and multiple devices. Check Play Console order management and your merchant payments report.
9. When working and approved, replace the preview screen with live store products and localized actual prices, clear auto-renewal/cancellation notices, and controls for account status.

## Notes
Google Play's payments policy normally requires **Google Play Billing** for subscriptions/digital features in Play-distributed Android apps, subject to regional program exceptions. Do not add an unapproved external payment button inside the app. Subscription billing and merchant payouts are different: purchases are collected through Google Play and eligible merchant proceeds are paid to the configured payments profile/account according to Google's schedule.

Official documentation:
- https://support.google.com/googleplay/android-developer/answer/140504
- https://support.google.com/googleplay/android-developer/answer/9858738
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/backend
