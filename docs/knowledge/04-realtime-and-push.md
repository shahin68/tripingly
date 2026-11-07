# Tripinly — Real Time, Push and Deep Links (client view)

## Real time (Socket.IO)

- Namespace `/v1/realtime` on the API host. Send the access token in the handshake `auth.token`.
- Transport: **websocket only** (no long-polling).
- On connect you're in your personal room automatically. To get live updates for a trip, emit `trip.subscribe { tripId }` when a trip screen opens and `trip.unsubscribe { tripId }` when it closes. An `error { code }` means you may not view it.
- **Open views (2026-11-07):** a subscribed trip counts as open, and the comments sheet sends `comments.watch { markerId }` when it opens and `comments.unwatch { markerId }` when it closes (`RealtimeClient.watchComments`, counted like trips and sent again after every reconnect). While a user has a stop's comments open on any device the server sends them no pushes about those comments, and while a trip is open no pushes about collaborators' changes to it; the in-app notification and the red dot still come. Signed-out devices get no pushes at all.
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
| `photo.processing` / `photo.ready` / `photo.failed` / `photo.deleted` | Update gallery; show a placeholder while processing. For my photo on a place they come to my user room without a `tripId` (`RealtimeEvent.ToMe`) |
| `marker.cover_changed` | Update the pin thumbnail |
| `comment.created` / `comment.deleted` | Update the thread and count |
| `like.count_changed` | Update the count (keep your own `likedByMe`) |
| `place.hot_changed` | `placeId`, `hotCount`: update every cached stop at that place (keep your own `hotByMe`; skip your own echoes) |
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
- `RealtimeSync` (`core:data`) applies events to the Room cache, never the screens: others' `marker.*` and `markers.reordered` are saved from the payload (a `marker.*` without a cover keeps the image on the device, since the place's image depends on the viewer; a new stop without one loads itself) (if the trip has unsent changes of its own, the trip reloads once they're sent instead); `trip.updated`, `day.*` and `member.*` reload the trip, at most twice per burst; `trip.deleted` drops it; `trips.changed` reloads My Trips; photo events update open galleries, and a ready or deleted photo reloads its stop for the cover and count. Events this user caused are skipped where the app applied the change itself.
- After every reconnect but the first since launch or sign-in: My Trips, the watched trips and galleries with processing photos are reloaded.
- `comment.*` from others change a stop's comment count in Room, and `place.hot_changed` the hot count of every cached stop at that place (the server sends it to each trip with a stop there) (`RealtimeSync`), keeping my own `hotByMe`; an open comments sheet adds and drops comments and updates comment likes live (`SocialRepository.commentChanges`, `likeCounts`). Trip like counts (`likeCounts`) and my own trip likes from any screen (`SocialRepository.myLikes`) update every Explore and profile card showing the trip. Others' `photo` like counts update every open gallery showing the photo (`PhotoRepository`), keeping my own heart. My own comment, like and hot echoes are skipped (the server's answer already counted them; a late echo could be older).
- Not used yet: `notification.created` (stage 9 push).

### What is live, and what isn't (2026-10-18)

- **Live (socket, as it happens):** an open trip's stops, days, members, trip details, trip deletion; photos ready/failed/deleted and covers; comments; My Trips list changes; follows; my own place photos finishing.
- **Live too (2026-11-01):** like and flame counts in an open trip (stops' places, comments, trip photos), every change at once. **Near live:** in-app notifications and their pushes come at once (the same one again within a few seconds is dropped); map places in view refresh every `placesRefreshSeconds` (default 300 s).
- **Not live (loaded when the screen opens, pulled to refresh, or scrolled):** Explore, Nearby, profiles and Friends lists, search, place details and place pins, likes on place photos and Wikimedia images (no trip room), trip likes on Explore cards. Wikimedia images sync once a day.

## Push (Firebase Cloud Messaging)

**Push rule (Shahin, 2026-11-01, replaces the 2026-10-18 cost rule):** every notification pushes at once, one per like or change; the same one again from the same person within `NOTIFICATION_REPEAT_COOLDOWN_SECONDS` (backend worker, default 5) is dropped, so like, unlike, like pushes once. Unlikes, own likes and single flames never push (a spot's creator gets one push per hot milestone). Live like counts I caused are ignored by the app (my tap already shows them).


- Android: FCM directly. iOS: Firebase Messaging with APNs (APNs key uploaded to Firebase by the user).
- Register the device after sign-in and whenever the token changes: `PUT /me/devices/{fcmToken}` with platform and app language. On sign-out: `DELETE /me/devices/{fcmToken}` then `POST /auth/logout`.
- Ask for notification permission as Home first opens (after sign-in and onboarding), before the user taps anything; once per visit to Home, rotation included (Shahin, 2026-10-11).
- Push arrives already localized from the server. Data payload: `{ type, notificationId, deepLink }`.
- Tapping a push opens the `deepLink`. Mark it read via `POST /notifications/read`.
- `join_requested` (someone asks to join the owner's trip) links to `tripinly://trips/{id}/members`: the map opens with the members sheet open, with Accept and Decline.
- What pushes (Shahin, 2026-11-01; texts and the full table in the backend's `05-realtime-and-notifications.md`): likes on my trip, photo or comment ("Anna liked your photo"), saves of my trip, follows and new friends, join requests and accepted requests, being added to a trip, comments, collaborators' changes, and a spot I made getting hot (only its creator, at 5, 25, 100, 500, 1000 and 5000 flames). A single flame never pushes. Each Settings switch turns off its whole group; flames and saves are under Likes, follows under New followers.
- **Comment pushes (2026-11-07):** a main comment pushes everyone on the trip and the stop's earlier commenters; a reply only its thread (the main comment's author and its repliers); a mention only the person mentioned (`comment_reply`, `comment_mention`, both under the Comments switch). Everyone on the trip still gets the red dot.
- **Notification center (2026-11-07):** `feature:notifications` (`NotificationCenterRoute`), hosted in the Profile tab through a slot, opened with the bell at the end of my profile card (next to my avatar, above the Public trips tile; tap again goes back to my trips) (`ProfileRoute(notificationCenter = …)`), so it can move to another screen. Every notification shows there, newest first, with the actor's avatar, or for a group up to three overlapping, the latest in front (a bell for the app), `timeAgo` dates and a dot while unread; a tap opens its `deepLink` like a tapped push (`DeepLinks.open`, which marks it read). `notification.created` (`RealtimeEvent.NotificationCreated`, which also carries the stop marks) puts the entry on top live, replacing the one with the same id: the server groups at write time (a stop's comments, a thread's replies, a stop's changes, likes on one item, a trip's saves, follows) and a group keeps growing until it is tapped (read); mentions, friends and trip notices are one row each. `NotificationCenterRepository.hasUnseen` (from `unseenCount` at start and after every reconnect, true on each arrival) draws a red dot on the Profile tab and the bell; the center being on screen sends `POST /notifications/seen`. Pushes: likes on trips and photos and flames on a spot I made push only at the server's milestones (`APP_NOTIFICATION_MILESTONES`), saves never; a follow pushes once per person; a declined join request and being removed by the owner notify once.
- **New-activity marks:** pins of stops with notifications I haven't seen carry a red dot (`MapColors.NewActivity`, 16 dp in a white ring; `TripMarkerIcon(isNew)`, iOS `drawTripPin`/`drawCoverPin`), and their day tab a 10 dp red dot on the tab's top end corner. They load with `GET /trips/{id}/new-activity` when the map opens, come live with `notification.created` events that carry a `markerId` (`RealtimeEvent.NewActivity`), and clear per kind (`NewActivityRepository`, `MapNewActivity`): news about the stop's comments (`aboutComments`, `commentMarkerIds`) when its comments open (`POST /notifications/read { markerId, kind: "comments" }`), the rest when the stop card opens (`kind: "other"`). The mark goes once both are seen; news arriving while its view is open is read at once. The stop a push opened the map on is marked even though opening the push read that notification.

### In the app

- Firebase starts from four values in `local.properties` (`firebase.projectId`, `firebase.senderId`, `firebase.appId`, `firebase.apiKey`, copied from `google-services.json`, which stays out of the repo). Without them there's no push.
- iOS uses the same project and sender plus its own `firebase.ios.appId` and `firebase.ios.apiKey` (`GOOGLE_APP_ID` and `API_KEY` from `GoogleService-Info.plist`, which stays out of the repo too). It also needs a paid Apple Developer account, the APNs key uploaded to Firebase and the Push Notifications capability on the app; without them the app runs, only without a token.
- `PushRegistration` registers the device when the signed-in app starts and when FCM issues a new token, but asks the server only when the account, token or language changed since the last registration. Sign-out unregisters before `POST /auth/logout`.
- The permission (Android 13+, and iOS) is asked as Home opens. Pushes that arrive while the app is open are shown too: on Android by the app, in the same channel the system uses for the others; on iOS as a banner.
- A tap opens the `deepLink` (see Deep links; `tripinly://notifications` just opens the app until there's an Activity screen) and marks the notification read. The same trip already on top opens again on what the push is about.

## Deep links

| Link | Opens |
|---|---|
| `tripinly://trips/{id}` | Trip screen |
| `tripinly://trips/{id}/members` | Trip screen with the Members sheet open (join requests) |
| `tripinly://markers/{id}` | Trip screen on the stop's day, the camera on the stop (zoom 16), its card closed so its "new" mark shows; a stop that is gone opens the trip as usual |
| `tripinly://markers/{id}/comments` | The same, with the stop's comments sheet open (comments, replies, mentions, comment likes; 2026-11-07) |
| `tripinly://me/followers` | Several new followers: from the center, my Followers segment; a push just opens the app for now (2026-11-07) |
| `tripinly://users/{username}` | That person's profile (follows, new friends) |
| `https://<APP_LINK_BASE_URL>/invite/{token}` | Invite preview → accept (sign in first if needed) |
| `https://<APP_LINK_BASE_URL>/trips/{id}` and `/markers/{id}` | Shared links (same as above) |

Use Android App Links and iOS Universal Links for the https links (the domain config files are set up on the backend side; ask the user for the domain).
