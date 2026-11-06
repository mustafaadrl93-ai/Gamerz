# Gamerz Privacy Policy (Draft)

**Last updated:** 6 November 2026

Gamerz is a social community for gamers. This draft explains the information the Android app is designed to process. It must be reviewed and completed with the operator's real support contact before public release.

## Information processed
- Account information: email address used for sign-in, player username, and selected gaming platform.
- User content: game posts, progress percentage, captions, and images that users choose to upload.
- Stories: images and optional captions, including batches of photos. Stories are hidden after 24 hours according to their expiry timestamp. The viewer advances every five seconds, but this does not delete the image.
- Social graph: whom users follow and who follows them. These relationships and aggregate follow counts are visible to authenticated users.
- Beta testing: user-submitted bug reports, suggestions, and feedback text, linked to their account for moderation and troubleshooting.
- Safety information: post and story reports submitted by users and moderation status.
- Technical information: Firebase authentication and service diagnostics may process device/app identifiers and security signals.

## How information is used
Information is used to create and secure accounts, show gaming posts and stories, operate profiles, and review reports and abusive content.

## Service providers
- Firebase Authentication and Cloud Firestore are used for account and app data.
- Uploadcare is used to store and deliver uploaded images.

These providers process data under their own terms and privacy documentation. Do not publish this policy until the operator has reviewed each provider's current terms and configured the project appropriately.

## Visibility and sharing
Posts and stories are visible to signed-in Gamerz users according to the app's current access rules. Post and story reports and beta feedback are restricted to the project owner. Stories and post images are delivered via shareable CDN URLs, which may be accessible to anyone with the URL even after a story expires inside the app. Sharing a story/post through another app sends the selected content or URL to that third-party app. Do not upload sensitive personal information.

## Retention and deletion
Stories are excluded from the app after 24 hours, but expired story records may remain in Firestore until separately cleaned up. Deleting a post or story record does not currently guarantee deletion of its image file from Uploadcare. Account deletion creates a request for separate media removal; fulfillment by an administrator is not automatic. Users can sign out in the app. Account and media deletion procedures must be completed and tested before public launch.

## Children's privacy
Gamerz is not intended for children under the minimum age required by applicable law. The operator must set and enforce an appropriate minimum age before release.

## Security
The project uses Firebase Authentication and Firestore Security Rules. No internet service can guarantee absolute security. The operator should test access rules and complete App Check setup before public launch.

## Changes
This policy may be updated as Gamerz features and providers change.

## Contact
**Before publication, replace this line with the real Gamerz privacy/support email address.**
