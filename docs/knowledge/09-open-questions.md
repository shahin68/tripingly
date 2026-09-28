# Tripinly Client — Open Questions

Ask the user before building anything that depends on these. When answered, move the answer into the right knowledge file, add it to `08-decision-log.md`, and delete it here.

## Blocking

1. **Purchases:** how premium is bought (RevenueCat recommended, which has a KMP SDK; not confirmed), prices, monthly/yearly. *Blocks:* paywall and purchase flow. Until then, only show premium state from `GET /me`.
2. **Backend environments:** base URLs for local/staging/production and the app-link domain for invite/share links. *Blocks:* real networking beyond local, universal/app links.
3. **Keys and config:** Google Maps API keys (Android + iOS, restricted to the app IDs), Firebase config files, Google OAuth client IDs, Apple Sign in capability. The user provides these; never commit secrets that aren't meant to be in the app.

## Has a working default

4. **Sign in with Apple on Android?** Default: no — Google only on Android, Google + Apple on iOS.
5. **Supported languages at launch.** Default: English, German, Hungarian.
6. **Design source:** the "Wayspot App v3" prototype plus the existing app screens. Where they differ, follow the existing app unless the user says otherwise; the brief wins on behaviour (copy, multiple photos).
7. **Offline:** read-only cache of own trips, no offline editing.
8. **Map categories shown by default** and chip order. Default: attractions, museums, historic, cafés, restaurants, bars, parks, nature.
9. **Activity screen:** default = notification history.
10. **Share links for people without the app:** default = deep links only.
