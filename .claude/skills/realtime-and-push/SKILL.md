---
name: realtime-and-push
description: Use for Tripinly live updates (Socket.IO connection, trip subscriptions, applying events to state), push notifications (FCM on Android, Firebase/APNs on iOS, permission, device registration) and deep links / app links.
---

# Real time and push

Event catalogue and deep links: `docs/knowledge/04-realtime-and-push.md`.

## Socket connection

- One shared `RealtimeClient` (`core:network/realtime`): `start`/`stop`, counted `watchTrip`/`unwatchTrip`, `events: Flow<RealtimeEvent>`, `connections`. The implementation is our own Socket.IO client over Ktor WebSockets in common code (`SocketIoRealtimeClient`, decided 2026-10-11); `FakeRealtimeClient` for useFakeApi and tests. A new event = a `RealtimeEvent` subclass + a case in `parseRealtimeEvent` + its handling in `RealtimeSync`, with tests.
- Connect when the app is in the foreground and signed in; disconnect in the background (push covers background).
- Handshake `auth.token` = current access token. On `TOKEN_EXPIRED`: refresh through the API client's own refresher (`HttpClient.refreshExpiredToken`, never a second refresh path), reconnect, resubscribe watched trips.
- Reconnect with backoff; after reconnect, repositories refetch open trips (events may have been missed).
- Parse events into a sealed class; unknown events are ignored (forward compatible).
- Repositories apply events to their state; screens never talk to the socket directly.

## Push

- Android: Firebase Messaging service → shared handler. Android 13+: request `POST_NOTIFICATIONS` in context.
- iOS: Firebase Messaging + APNs, `UNUserNotificationCenter` permission in context, forward the FCM token to shared code.
- Register with `PUT /me/devices/{fcmToken}` (platform, language) after sign-in, on token refresh, and on language change.
- Foreground push: show an in-app banner instead of a system notification (or both, following platform conventions).
- Tap → parse `deepLink` → navigate → `POST /notifications/read`.
- One notification channel per type on Android (comments, trip activity, likes) so users can mute them in system settings; mirror server settings from `/me/notification-settings`.

## Deep links

- Custom scheme `tripinly://` plus https app links / universal links on the app-link domain (ask the user for it; see open questions).
- Invite links: if signed out, keep the pending invite, sign in / onboard, then show the invite preview (`GET /invites/{token}`) and accept.
- Unknown or unavailable targets (`NOT_FOUND`) → a friendly "not available" screen.

## Tests

Event parsing (including unknown events), repository reducers for each event, deep link parsing, pending-invite flow state.
