# Tripinly — Real Time, Push and Deep Links (client view)

## Real time (Socket.IO)

- Namespace `/v1/realtime` on the API host. Send the access token in the handshake `auth.token`.
- Transport: **websocket only** (no long-polling).
- On connect you're in your personal room automatically. To get live updates for a trip, emit `trip.subscribe { tripId }` when a trip screen opens and `trip.unsubscribe { tripId }` when it closes. An `error { code }` means you may not view it.
- Every event: `{ event, tripId, actorId, at, data }`. `data` uses the same shapes as the REST API.
- **Events are hints, not the source of truth.** Apply them to local state when you can; after any reconnect, refetch the open trip via REST.
- Dedupe by entity ID (your own actions are echoed back).
- Token expiry: the server disconnects with `TOKEN_EXPIRED` → refresh the token, reconnect, resubscribe.

### Trip room events

| Event | Client action |
|---|---|
| `trip.updated` | Update title/dates/visibility |
| `trip.deleted` | Close the trip screen, remove from lists, show a message |
| `day.created` / `day.deleted` | Update days |
| `marker.created` / `marker.updated` / `marker.deleted` | Update markers; refetch the day route if shown |
| `markers.reordered` | Apply order; refetch the day route if shown |
| `photo.processing` / `photo.ready` / `photo.failed` / `photo.deleted` | Update gallery; show a placeholder while processing |
| `marker.cover_changed` | Update the pin thumbnail |
| `comment.created` / `comment.deleted` | Update the thread and count |
| `like.count_changed` | Update the count (keep your own `likedByMe`) |
| `member.added` / `member.removed` | Update members; if *you* were removed, close the trip |

### Personal room events

| Event | Client action |
|---|---|
| `notification.created` | Update the badge and Activity list |
| `trips.changed` | Refresh My Trips |
| `follows.changed` | `{ followerId, followeeId, following }`, to both people (backend#34): an open profile it concerns reloads its header (counts, "Follows you", Follow button), skipping my own tap on that profile; open Friends/Following/Followers lists reload |
| `account.suspended` | Sign out with a message |

### In the app

- `RealtimeClient` (`core:network`) is our own Socket.IO client over Ktor WebSockets (Engine.IO 4, Socket.IO 5). It connects while the user is signed in and the app is in the foreground (`MainNavigation`), reconnects after 1 s doubling up to 5 s (±50 %), and treats a silence longer than the server's ping interval plus timeout as a dead connection.
- `TOKEN_EXPIRED` (at the handshake or as `error` before the server disconnects) refreshes through the same refresher as the REST client, so the refresh token is never sent twice, then reconnects at once. `ACCOUNT_SUSPENDED` (refusal or `account.suspended`) signs out. Other refusals stop until the app comes back to the foreground.
- A trip is subscribed while `TripRepository.observeTrip` is collected (counted, so two screens on one trip share it), and every subscription is sent again after a reconnect.
- `RealtimeSync` (`core:data`) applies events to the Room cache, never the screens: others' `marker.*` and `markers.reordered` are saved from the payload (if the trip has unsent changes of its own, the trip reloads once they're sent instead); `trip.updated`, `day.*` and `member.*` reload the trip, at most twice per burst; `trip.deleted` drops it; `trips.changed` reloads My Trips; photo events update open galleries, and a ready or deleted photo reloads its stop for the cover and count. Events this user caused are skipped where the app applied the change itself.
- After every reconnect but the first since launch or sign-in: My Trips, the watched trips and galleries with processing photos are reloaded.
- `comment.*` and `like.count_changed` from others change a stop's comment and like counts in Room (`RealtimeSync`), keeping my own `likedByMe`; an open comments sheet adds and drops comments and updates comment likes live (`SocialRepository.commentChanges`, `likeCounts`). My own comment echoes are skipped (the server's answer already counted them).
- Not used yet: `notification.created` (stage 9 push).

## Push (Firebase Cloud Messaging)

- Android: FCM directly. iOS: Firebase Messaging with APNs (APNs key uploaded to Firebase by the user).
- Register the device after sign-in and whenever the token changes: `PUT /me/devices/{fcmToken}` with platform and app language. On sign-out: `DELETE /me/devices/{fcmToken}` then `POST /auth/logout`.
- Ask for notification permission as Home first opens (after sign-in and onboarding), before the user taps anything; once per visit to Home, rotation included (Shahin, 2026-10-11).
- Push arrives already localized from the server. Data payload: `{ type, notificationId, deepLink }`.
- Tapping a push opens the `deepLink`. Mark it read via `POST /notifications/read`.

### In the app (Android; iOS waits for an Apple Developer account)

- Firebase starts from four values in `local.properties` (`firebase.projectId`, `firebase.senderId`, `firebase.appId`, `firebase.apiKey`, copied from `google-services.json`, which stays out of the repo). Without them there's no push.
- `PushRegistration` registers the device when the signed-in app starts and when FCM issues a new token, but asks the server only when the account, token or language changed since the last registration. Sign-out unregisters before `POST /auth/logout`.
- The permission (Android 13+) is asked right after the user creates a trip. Pushes that arrive while the app is open are shown by the app in the same channel the system uses for the others.
- A tap opens the `deepLink` (`tripinly://trips/{id}`, `tripinly://markers/{id}` opens the stop's trip; `tripinly://notifications` just opens the app until there's an Activity screen) and marks the notification read.

## Deep links

| Link | Opens |
|---|---|
| `tripinly://trips/{id}` | Trip screen |
| `tripinly://markers/{id}` | Trip screen with that marker's sheet open |
| `https://<APP_LINK_BASE_URL>/invite/{token}` | Invite preview → accept (sign in first if needed) |
| `https://<APP_LINK_BASE_URL>/trips/{id}` and `/markers/{id}` | Shared links (same as above) |

Use Android App Links and iOS Universal Links for the https links (the domain config files are set up on the backend side; ask the user for the domain).
