# Tripinly — Rules the Client Must Reflect

The backend enforces these rules. The client must show them correctly and never work around them.

## Sign-in and onboarding

- Sign-in with **Google** (Android and iOS) and **Apple** (iOS; Apple on Android is an open question). No email/password.
- After first sign-in, `onboardingRequired: true` → onboarding flow: display name, **username** (live availability check), **birth date**, then **consents** (Terms + Privacy, current versions from `/legal/documents`).
- **Minimum age 16.** Validate locally for a quick message, but the server decides (`AGE_REQUIREMENT_NOT_MET`). On rejection show a neutral message and sign the user out; keep nothing locally.
- Any API call returning `ONBOARDING_INCOMPLETE` or `CONSENT_REQUIRED` sends the user back to the relevant onboarding/consent screen.

## Usernames

3–30 characters, lowercase `a–z`, `0–9`, `.` and `_`; no leading/trailing `.`, no `..`. Lowercase input automatically. Unique (server returns `USERNAME_TAKEN`).

## Trips

- Owner, editors (collaborators), title, dates, ordered days, visibility `public`/`private`. New trips default to the user's `defaultTripVisibility`.
- An optional destination (e.g. "Paris"), picked from our place search when creating the trip; never required. The map opens there while the trip has no stops, and never jumps to the device location then.
- **Owner only:** rename, change dates, change visibility, delete trip, add/remove members, create/revoke invite links.
- **Owner and editors:** add/edit/delete/reorder markers and days, upload/delete photos, set cover.
- **Everyone else** sees public trips read-only and can like, comment and copy.
- Hide controls the user can't use; still handle `FORBIDDEN`/`NOT_FOUND` gracefully (roles can change live).
- Private trips return `NOT_FOUND` to non-members; show "This trip isn't available".

## Markers and photos

- A marker belongs to a day, has a name, location, optional time, order.
- Several photos per marker; one **cover** shown inside the map pin; gallery is swipeable. Markers without photos are plain numbered pins.
- Limits: 30 photos per marker, 15 MB, JPEG/PNG/HEIC/WebP (server errors `PHOTO_LIMIT_REACHED`, `UPLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`).

## Add to my trips (copy)

- Only public trips/markers of other users. One button, no options: creates an independent editable copy owned by the user, **without** photos, comments or likes.
- Show "Copied from @handle" only when the response includes `copiedFrom` (it disappears if the original is deleted).

## Social

- Likes on trips, markers, photos, comments and places (optimistic UI, idempotent).
- Comments on markers only, flat list, 1–1000 characters. Author and trip owner can delete.
- Find people by **username search** and **invite links**. No follow system.
- **Report** (user, trip, marker, photo, comment) and **block** must be reachable from every place user content appears. After blocking, remove that user's content from the screen immediately.

## Places, map and routes

- The map shows Tripinly places (liked by users) prominently and OpenStreetMap places as smaller dots when zoomed in. See `05-maps-places-routing.md`.
- Route lines and places along the way are free. **Best route**: free = straight-line reordering; premium = real travel times with "N min shorter".

## Privacy

- Location permission only for Nearby, "center on me" and routes from current location. Ask in context, work without it.
- Send the user's location only in Nearby/search/route requests. Never store location history.
- Account deletion: explain what's deleted (everything they made; copies other people made of their trips stay), require a fresh sign-in if the server returns `REAUTH_REQUIRED`, then sign out and wipe all local data (tokens, caches, databases, images).
- Data export: button in settings; the file arrives by email.
- Settings include default trip visibility and per-type notification switches.

## Notifications

Push for: comments on your markers, being added to a trip, collaborator changes (batched), likes (grouped). Everything else is in-app only.
