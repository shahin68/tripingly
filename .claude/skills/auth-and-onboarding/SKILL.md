---
name: auth-and-onboarding
description: Use for Tripinly sign-in (Google on Android/iOS, Apple on iOS), token storage and refresh, onboarding (name, username, birth date, 16+ check), consent screens, sign-out and session-expiry handling.
---

# Auth and onboarding

Rules: `docs/knowledge/02-client-rules.md` → Sign-in and onboarding.

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

## Re-authentication

`DELETE /me` may return `REAUTH_REQUIRED`: run the sign-in again (same provider), then retry the deletion.

## Tests

Username validation, age calculation (birthday edge cases, leap years), refresh single-flight, onboarding routing from `GET /me` states, sign-out clears storage.
