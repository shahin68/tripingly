# Tripinly Client — Open Questions

Ask the user before building anything that depends on these. When answered, move the answer into the right knowledge file, add it to `08-decision-log.md`, and delete it here.

## Blocking

1. **Production environment and app-link domain:** staging exists (`https://api-staging-4ade.up.railway.app/v1`); production and the domain for invite/share links do not yet. *Blocks:* release builds, App Links / Universal Links.
2. **Keys and accounts:** Google Maps API keys (Android + iOS, restricted to the app IDs), Firebase project and config files, Google OAuth client IDs, Apple Developer account (Sign in with Apple, push). None exist yet; ask for each one in the stage that needs it. Never commit secrets that aren't meant to be in the app.
3. **iOS map SDK:** the iOS map is MapKit today while the decision is Google Maps on both platforms. Adding the Google Maps iOS SDK (SPM) needs an iOS Maps key. *Blocks:* the map stage on iOS.

## Has a working default

4. **Paywall prices and placement:** RevenueCat is decided; prices and where the paywall appears come with the paywall stage.
