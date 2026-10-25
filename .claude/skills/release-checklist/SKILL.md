---
name: release-checklist
description: Use before producing any TestFlight, App Store, internal testing or Google Play build of Tripinly — verifies store requirements, privacy, configuration, attribution and build settings.
---

# Release checklist

Go through every item; report which pass, which fail, and what the user must do (store console tasks are theirs).

## Configuration

- [ ] Production base URL, debug menus off, and no HTTP logging (it only runs in debuggable builds; check the release build is not debuggable).
- [ ] Google Maps API keys restricted to the app (Android package + SHA-1; iOS bundle ID) and not the same as the debug keys.
- [ ] Firebase production config files; APNs key uploaded (user).
- [ ] Version name/code and build number bumped.
- [ ] Android: R8/ProGuard rules for serialization, Ktor, Socket.IO; release signing config (user holds the keys).

## Store requirements

- [ ] **Sign in with Apple** available on iOS (required because Google sign-in is offered).
- [ ] **Account deletion** reachable in Settings, works end to end.
- [ ] **Report and block** on users, trips, markers, photos and comments.
- [ ] Terms and privacy policy links in onboarding and Settings.
- [ ] Permission texts (iOS `Info.plist`): location when in use, photo library (only if not using the picker), notifications. Explain why in plain language, localized.
- [ ] App Store privacy "nutrition label" and Google Play "Data safety" answers match what the app does (the user fills them in; give them the list: name, username, birth date, photos, user content, coarse/precise location used for features and not stored, device token, crash data if any).
- [ ] Purchases: restore purchases, subscription terms, links to manage subscriptions (all on the paywall).
- [ ] RevenueCat: real Play Store and App Store apps and products (`monthly`, `yearly`, `lifetime`) attached to `tripinly_pro` and the default offering; production `revenuecat.androidKey` / `revenuecat.iosKey` (not the Test Store key) in the release build; webhook pointed at production with `REVENUECAT_WEBHOOK_AUTH`; `DEV_ACCOUNTS_PRO` off.

## Legal and attribution

- [ ] The documentation, legal pages and decision audit stage is done (build order stage 13): handbook written, decision logs complete, Terms, Privacy Policy, Community Guidelines, Imprint and subscription terms published as versioned legal documents and **reviewed by Shahin's lawyer**.

- [ ] "© OpenStreetMap contributors" on map screens with our places/routes, and in Settings → About/Legal; "© openrouteservice.org" with routes.
- [ ] Google Maps logo and legal notices visible (not covered by sheets).
- [ ] Open-source licenses screen.

## Privacy and security

- [ ] No tokens, emails, birth dates, coordinates or signed URLs in logs or crash reports.
- [ ] Tokens only in secure storage; sign-out and deletion wipe local data.
- [ ] Photos have EXIF/GPS removed before upload.
- [ ] Location requested only in context; app works without it.

## Quality

- [ ] Both platforms: sign-in, onboarding, create trip, add marker by search / long press / from map place, upload photos, set cover, route with places along the way, like, comment, save a public trip and ask to join it (owner accepts), invite link, push tap, block, report, delete account.
- [ ] German and Hungarian UI checked for truncation.
- [ ] Tests pass.
