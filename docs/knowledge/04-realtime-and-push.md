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
| `account.suspended` | Sign out with a message |

## Push (Firebase Cloud Messaging)

- Android: FCM directly. iOS: Firebase Messaging with APNs (APNs key uploaded to Firebase by the user).
- Register the device after sign-in and whenever the token changes: `PUT /me/devices/{fcmToken}` with platform and app language. On sign-out: `DELETE /me/devices/{fcmToken}` then `POST /auth/logout`.
- Ask for notification permission in context (for example after creating the first trip or being added to one), not on first launch.
- Push arrives already localized from the server. Data payload: `{ type, notificationId, deepLink }`.
- Tapping a push opens the `deepLink`. Mark it read via `POST /notifications/read`.

## Deep links

| Link | Opens |
|---|---|
| `tripinly://trips/{id}` | Trip screen |
| `tripinly://markers/{id}` | Trip screen with that marker's sheet open |
| `https://<APP_LINK_BASE_URL>/invite/{token}` | Invite preview → accept (sign in first if needed) |
| `https://<APP_LINK_BASE_URL>/trips/{id}` and `/markers/{id}` | Shared links (same as above) |

Use Android App Links and iOS Universal Links for the https links (the domain config files are set up on the backend side; ask the user for the domain).
