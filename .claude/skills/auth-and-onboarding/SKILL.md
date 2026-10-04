---
name: auth-and-onboarding
description: Use for Tripinly sign-in (Google on Android/iOS, Apple on iOS), token storage and refresh, onboarding (name, username, birth date, 16+ check), consent screens, sign-out and session-expiry handling.
---

# Auth and onboarding

Rules: `docs/knowledge/02-client-rules.md` → Sign-in and onboarding.

## Where it lives

- `core:data` `SessionRepository` (session state, restore, sign-in/out) and `AccountRepository` (onboarding calls); `FakeAccountBackend` for `-Ptripinly.useFakeApi=true` and tests.
- `feature:auth`: `AuthGate` wraps the app in `App.kt`; `SignIn` and `Onboarding` screens; `SocialSignIn` is the platform seam (`CredentialManagerSignIn` on Android, `NativeSocialSignIn` over the Swift `AppNativeSignIn` on iOS).
- `core:storage` `SecureStore` + `core:network` `SecureTokenStore` hold the tokens.
- Developer sign-in (`POST /auth/dev`) shows in local/staging builds; staging's secret is `tripinly.devAuthSecret` in the root `local.properties`, never committed. `tripinly.googleWebClientId` goes there too.

## Sign-in

- **Android Google:** Credential Manager with Google ID option (`GetGoogleIdOption`, server client ID = the web/backend client ID) → ID token → `POST /auth/google`.
- **iOS Google:** GoogleSignIn SDK → ID token → `POST /auth/google`.
- **iOS Apple:** `ASAuthorizationAppleIDProvider` requesting name + email → identity token **and** authorization code → `POST /auth/apple`. Apple sends the name only the first time; pass it along so onboarding can prefill it.
- Wrap platform SDKs in a shared `expect`/interface `SocialSignIn` returning a token result; the shared auth repository does the API call.
- Response: access + refresh tokens + `onboardingRequired`. Save tokens in `SecureStore` only.

## Onboarding flow

1. **Profile:** display name (prefilled), username (lowercase, live check with `GET /users/check-username`, debounced 400 ms), birth date (date picker, no default age).
2. **Age:** if under 16 locally → neutral message, sign out, delete local data. Server may still return `AGE_REQUIREMENT_NOT_MET`; handle the same way.
3. **Consent:** load `/legal/documents` in the app language; show Terms and Privacy links; one clear checkbox or button per required document; `POST /me/consents` with document type + version.
4. `PATCH /me` with the profile → `GET /me` → home.

On app start: tokens present → `GET /me` → if not onboarded, resume the step; if `CONSENT_REQUIRED`, show only the updated documents.

## Sign-out

`DELETE /me/devices/{fcmToken}` → `POST /auth/logout` → clear `SecureStore`, caches, image cache, local DB → disconnect socket → sign-in screen. Also triggered by refresh failure (without the network calls).

Every way a session ends (sign-out, refused refresh, suspended or under-age account, a `401` on restore, a sign-in whose `/me` fails, a cold start without tokens) goes through `SessionRepositoryImpl.endSession`: tokens first, then every `LocalDataCleaner` bound in Koin, then the `SignedOut` state. Anything new that stores user data (a cache, a table, images, a provider SDK's remembered account) binds its own cleaner with a `named(...)` qualifier in its module, e.g. `single(named("photosCache")) { LocalDataCleaner { ... } }`. Bound today: Room trips + markers (`dataModule`), Google credential state (Android `authPlatformModule`, iOS `MainViewController` → `NativeSignIn.signOut()`). Nothing user-related is kept across sign-out unless a flow explicitly needs it and the decision log says so.

## Re-authentication

`DELETE /me` may return `REAUTH_REQUIRED`: run the sign-in again (same provider), then retry the deletion.

## Tests

Username validation, age calculation (birthday edge cases, leap years), refresh single-flight, onboarding routing from `GET /me` states, sign-out clears storage.
