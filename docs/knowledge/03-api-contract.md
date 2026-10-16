# Tripinly — API Contract (client copy)

This is a copy of the backend's API spec (`docs/knowledge/04-api-spec.md` in `shahin68/tripinly-backend`). **The source of truth is the backend's OpenAPI document at `/v1/openapi.json`** (staging: `https://api-staging-4ade.up.railway.app/v1/openapi.json`). If this file and the OpenAPI document disagree, the OpenAPI document wins; fix this file.

The same agent builds both sides. When the client needs a new endpoint or field, change the backend first (its own PR and its spec), then update this file, and list the change in the stage report.

REST over HTTPS, JSON, base path `/v1`.

## Conventions

- **Auth:** `Authorization: Bearer <accessToken>` on everything except `/auth/*`, `/legal/*`, `/health` and public share previews.
- **Language:** `Accept-Language` header selects the locale for error messages and any server-rendered text; falls back to the user's `locale`, then `en`.
- **Naming:** JSON fields in `camelCase`; paths in `kebab-case`, plural nouns.
- **IDs:** UUID strings. **Timestamps:** ISO 8601 UTC. **Dates:** `YYYY-MM-DD`. **Coordinates:** `{ "lat": 48.2, "lng": 16.37 }`.
- **Pagination:** cursor based. Request `?cursor=&limit=` (default 20, max 50). Response `{ "items": [...], "nextCursor": "..." | null }`.
- **Errors:**
  ```json
  { "error": { "code": "TRIP_NOT_FOUND", "message": "localized text", "details": {} } }
  ```
  `code` is stable and documented; clients switch on `code`, never on `message`. Validation errors use `VALIDATION_FAILED` with per-field `details`.
- **Status codes:** 200/201/204 success, 400 validation, 401 unauthenticated, 403 forbidden, 404 not found (also used for private resources the caller can't see — never reveal existence), 409 conflict (e.g. username taken), 422 domain rule violated, 429 rate limited.
- **Idempotency:** likes and unlikes are idempotent. `POST` creating content will accept an optional `Idempotency-Key` header (in the spec, not implemented on the backend yet).
- **Rate limits:** per user and per IP on auth, search, comment and upload endpoints.

## Endpoints

### Auth
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/google` | Body: Google ID token. Returns tokens + `onboardingRequired` |
| POST | `/auth/apple` | Body: Apple identity token + authorization code (for revocation later) + optional `givenName`/`familyName` (Apple sends the name only on first sign-in) |
| POST | `/auth/refresh` | Rotates refresh token; reuse of an old token revokes the whole family |
| POST | `/auth/logout` | Revokes current refresh token and removes the device |

### Legal and consent
| Method | Path | Notes |
|---|---|---|
| GET | `/legal/documents` | Current versions and URLs per locale |
| GET | `/me/consents` | |
| POST | `/me/consents` | Grant or withdraw, with document version |

### Me
| Method | Path | Notes |
|---|---|---|
| GET | `/me` | Profile, settings, onboarding state, entitlements |
| PATCH | `/me` | Display name, username (onboarding), birth date (onboarding only), locale, `defaultTripVisibility` |
| GET | `/me/stats` | Trips, markers, photos counts |
| GET | `/me/friends?cursor=&limit=` | The user's friends as user summaries, cursor paginated `{ items, nextCursor }`. The New trip sheet shows the first page (limit 50) as a row of avatars to add as members. Backend: tripinly-backend#32 (generated `UserPageDto`) |
| GET | `/me/following?cursor=&limit=`, `/me/followers?cursor=&limit=` | Same shape; my profile's Following and Followers lists (pages of 50) |
| GET | `/me/trips` | Owned, collaborating and saved (`role` `viewer`, `savedByMe` true), cursor paginated; each item has `role`, `saveCount`, `savedByMe`, `coverThumbUrl` (first cover photo in the trip, or null), `dayCount`, `markerCount`, `editorCount` (people it's shared with, owner not counted; backend#30) |
| PUT | `/me/devices/{fcmToken}` | Register/refresh push device. Body: `platform` (`android`\|`ios`), `locale` |
| DELETE | `/me/devices/{fcmToken}` | |
| POST | `/me/export` | Starts GDPR export job; email when ready |
| DELETE | `/me` | Starts account deletion (returns 202; requires a sign-in within the last 10 minutes, else `REAUTH_REQUIRED`) |

### Users
| Method | Path | Notes |
|---|---|---|
| GET | `/users/check-username?username=` | Availability during onboarding |
| GET | `/users/search?q=` | Prefix search on username and display name; `q` 2–50 characters, up to 20 results `{ items: [UserSummary] }` |
| GET | `/users/{username}?cursor=&limit=` | Profile: `user`, `followerCount`, `followingCount`, `isFollowing`, `followsMe`, `isFriend`, `publicTripCount`, `trips[]` (trip summaries with `likeCount`, `likedByMe`, `saveCount`, `savedByMe`), `nextCursor`. The app asks without `@`, pages of 20; `404 NOT_FOUND` for unknown or blocked either way |
| PUT | `/users/{id}/follow` | Follow (idempotent); the profile's Follow button, shown at once |
| DELETE | `/users/{id}/follow` | Unfollow |
| POST | `/users/{id}/block` | From a profile's menu, after a confirmation; the profile then closes |
| DELETE | `/users/{id}/block` | |
| GET | `/me/blocks` | |

### Trips
| Method | Path | Notes |
|---|---|---|
| POST | `/trips` | Title, dates, visibility, optional `destination` `{ name, location }`, optional initial `memberUsernames` (≤ 20, joined as editors; unknown → `400 VALIDATION_FAILED` with `fields["memberUsernames.<i>"] = ["notFound"]`, blocked → `["blocked"]`) |
| GET | `/trips/{id}` | Trip with days, markers (cover thumbnail URLs), members, save and join state |
| PATCH | `/trips/{id}` | Owner: title, dates, visibility, `destination` (null removes it) |
| DELETE | `/trips/{id}` | Owner |
| PUT | `/trips/{id}/save` | Save someone else's public trip to My Trips (read-only, the live original). Idempotent, 204 |
| DELETE | `/trips/{id}/save` | Remove it from My Trips. 204 |
| POST | `/trips/{id}/join-request` | Ask the owner to join as an editor; returns the request (repeating a pending one returns it). `409 JOIN_REQUEST_DECLINED` once declined |
| DELETE | `/trips/{id}/join-request` | Withdraw my pending request. 204 |
| GET | `/trips/{id}/join-requests?cursor=&limit=` | Owner: pending requests `{ id, tripId, user, status, createdAt }`, newest first. The members sheet asks only when `pendingJoinRequestCount > 0` |
| POST | `/join-requests/{id}/accept` | Owner: the person becomes an editor (member limit applies, `LIMIT_REACHED`); returns the member |
| POST | `/join-requests/{id}/decline` | Owner: 204; they can't ask again |
| GET | `/explore/trips?cursor=&limit=` | Public trips, mine included (`role` owner/editor: no Save or Report), ranked; Social → Explore, pages of 20 |

### Days
| Method | Path | Notes |
|---|---|---|
| POST | `/trips/{id}/days` | Append a day |
| DELETE | `/days/{id}` | Deletes its markers |
| DELETE | `/days/{id}/markers` | Clears the day: deletes all its markers in one request → 204 (backend#17) |
| PUT | `/days/{id}/marker-order` | Body: ordered marker IDs |
| POST | `/days/{id}/optimize` | Best route. Free: straight-line. Premium: travel times. Returns proposed order + `mode` + optional `savedMinutes`; `?apply=true` persists |

### Members and invites
| Method | Path | Notes |
|---|---|---|
| POST | `/trips/{id}/members` | Owner adds by username |
| POST | `/trips/{id}/members/batch` | Owner adds several people at once (`{ usernames }`, all or nothing; refused ones as `fields.usernames.<i>` = `notFound`/`blocked`). The Members sheet uses it for the friends picked in its avatar row. Backend: tripinly-backend#33 |
| DELETE | `/trips/{id}/members/{userId}` | Owner removes, or editor removes self (leave) |
| POST | `/trips/{id}/invites` | Owner creates invite link → `{ url, expiresAt }` |
| DELETE | `/trips/{id}/invites/{inviteId}` | Revoke |
| GET | `/invites/{token}` | Preview (trip title, owner) |
| POST | `/invites/{token}/accept` | Join as editor |

### Markers
| Method | Path | Notes |
|---|---|---|
| POST | `/days/{id}/markers` | Either `placeId`, or `name` + `location` (+ optional `osmType`/`osmId` from a Photon result), or `location` alone for a tap on the map: the server names the stop (our place within 25 m, else the address, else the coordinates) and the app takes that name from the answer (backend#28). Optional `time`. Optional `id`: a UUID the app chose so the marker shows before the server answers; `409 ID_CONFLICT` if that ID is taken (the app treats it as "already saved"). Google IDs not accepted |
| GET | `/markers/{id}` | Marker with photos, like state, comment count |
| PATCH | `/markers/{id}` | Name, time, location, move to another day of the same trip |
| DELETE | `/markers/{id}` | |
| POST | `/markers/{id}/copy` | Body: target `dayId` in one of my trips |

### Photos
| Method | Path | Notes |
|---|---|---|
| POST | `/markers/{id}/photos/upload-url` | Body: mime type, size. Returns photo ID + pre-signed PUT URL + `uploadHeaders` (a string map the PUT must send; typed as a map since backend#29). The PUT goes to storage without our `Authorization` header |
| POST | `/photos/{id}/complete` | Client confirms upload → processing job |
| GET | `/markers/{id}/photos` | Ordered gallery |
| PUT | `/markers/{id}/photo-order` | |
| PUT | `/markers/{id}/cover` | Body: `photoId` |
| DELETE | `/photos/{id}` | Uploader, trip owner or editor |

### Comments and likes
| Method | Path | Notes |
|---|---|---|
| GET | `/markers/{id}/comments` | Paginated, oldest first; the stop's comments sheet loads pages of 50 |
| POST | `/markers/{id}/comments` | `{ body }` (≤ 1000), with an `Idempotency-Key`; the sheet adds the answer and the cached stop counts it |
| DELETE | `/comments/{id}` | Author or trip owner |
| PUT | `/likes/{targetType}/{targetId}` | `trip`, `marker`, `photo`, `comment`, `place`; answers `{ liked, likeCount }`. The app shows a like at once and puts it back on a refusal (a stop's in the Room cache, the others in the screen's state) |
| DELETE | `/likes/{targetType}/{targetId}` | |

### Discovery
| Method | Path | Notes |
|---|---|---|
| GET | `/places/in-view?bbox=minLng,minLat,maxLng,maxLat&zoom=&categories=&limit=` | Places for the visible map area: Tripinly places first, OSM places when zoomed in, clusters when zoomed out |
| GET | `/places/tiles?tiles=level/x/y,…&zoom=&categories=&limit=` | The same places for up to 16 fixed map squares (side `360 / 2^level` degrees) in one request: `{ tiles: [{ tile, places[], clusters[] }], attribution }`. The app loads places this way (backend #26). Every place carries `prominence` (0–100, how well known; backend#36), and well-known places come first in each square so they stay as the map zooms in |
| GET | `/places/search?q=&lat=&lng=&scope=` | Our places + Photon addresses/cities merged. `scope=destination` (the Create Trip destination): only countries, regions, cities and districts, cities first |
| GET | `/places/nearby?lat=&lng=&cursor=&limit=` | Popular places around the user with `distanceMeters`; Social → Nearby, pages of 20, asked once per opening with the first location fix; location not stored |
| GET | `/places/popular?bbox=…&excludeTripId=` | Tripinly places only in the visible area, excluding places already in the trip |
| GET | `/places/{id}` | Place with popularity and its `images[]` (trip photos, then Wikimedia Commons images with their credit) |
| POST | `/places/{id}/add-to-trip` | Body: `dayId` → new marker (not used by the app: it sends `POST /days/{id}/markers` with `id` + `placeId` so the stop shows at once) |

### Routing
| Method | Path | Notes |
|---|---|---|
| GET | `/routes?from=lat,lng&to=lat,lng&mode=walking\|cycling\|driving&categories=` | Route line + places along the way |
| GET | `/days/{id}/route?mode=&categories=` | Route through the day's markers in order + places along the way |

Response shapes and behaviour: see `05-maps-places-routing.md`.

### Sharing
| Method | Path | Notes |
|---|---|---|
| GET | `/share/trips/{id}` | Share URL + preview data (respects visibility) |
| GET | `/share/markers/{id}` | |

### Notifications
| Method | Path | Notes |
|---|---|---|
| GET | `/notifications` | Paginated, newest first |
| POST | `/notifications/read` | Body: IDs or `all` |
| GET/PATCH | `/me/notification-settings` | Per-type push on/off |

### Moderation
| Method | Path | Notes |
|---|---|---|
| POST | `/reports` | Target type (`user`, `trip`, `marker`, `photo`, `comment`, `place_image`), ID, reason (incl. `copyright`), details |
| GET | `/admin/reports` | Admin only, filter by status |
| PATCH | `/admin/reports/{id}` | Dismiss, hide content, suspend user |

### Ops
| Method | Path | Notes |
|---|---|---|
| GET | `/app-config` | Settings that tune the app, no auth, read once per launch: `{ placesRefreshSeconds, photoMaxEdgePx, photoJpegQuality, photoParallelUploads, pinLargeMinProminence, pinMediumMinProminence, mapMaxPins }` (how old a kept map square may get before it's reloaded in the background, default 300; the long edge a photo is shrunk to before upload, default 2560; its JPEG quality, default 85; uploads at a time, default 2; backend#29; the prominence from which a place pin is drawn large, default 95, or medium, default 60; most place pins on the map at once, default 150; backend#36). Values change on the server's env vars without an app update; feature flags come here after v1 (backend #27) |
| GET | `/health` | Liveness (no auth) |
| GET | `/health/ready` | DB + Redis reachable |
| GET | `/.well-known/assetlinks.json`, `/.well-known/apple-app-site-association` | Android App Links / iOS Universal Links for invite and share URLs (served on the app-link domain) |

## Key response shapes

Field names the client relies on (full schemas in OpenAPI):

- **Trip** (`GET /trips/{id}`): `id`, `title`, `startDate`, `endDate`, `destination` (`{ name, location }` or null), `visibility`, `owner` (user summary), `myRole` (`owner`\|`editor`\|`viewer`), `members[]`, `likeCount`, `likedByMe`, `saveCount`, `savedByMe`, `myJoinRequest` (`pending`\|`declined`\|null), `pendingJoinRequestCount` (owner only, else 0), `days[]` → `{ id, position, date, markers[] }`.
- **Marker**: `id`, `dayId`, `placeId`, `name`, `location`, `time` (`HH:mm` or null), `position`, `coverPhotoId`, `coverThumbUrl`, `photoCount`, `likeCount`, `likedByMe`, `commentCount`, `createdBy` (user summary or null).
- **Photo**: `id`, `markerId`, `status` (`processing`\|`ready`\|`failed`), `thumbUrl`, `displayUrl` (signed, expire after ~1 h), `width`, `height`, `position`, `likeCount`, `likedByMe`, `uploader`.
- **User summary**: `id`, `username`, `displayName`.
- **Place** (in-view/search/along-the-way): see `05-maps-places-routing.md`. Every Place shape (tiles, nearby, detail) has `coverImageId`, `coverSource` (`trip`\|`commons`\|null) and `coverThumbUrl`; `coverPhotoId` is set only for a trip photo. Cache a cover by `coverImageId`.
- **Place detail** (`GET /places/{id}`): adds `tags` (`openingHours`, `website`, …) and `images[]` → `{ id, source (trip|commons), thumbUrl, displayUrl, width, height, credit }`. The first image is the cover. `credit` is null for trip photos; for Commons it's `{ author, title, license, licenseUrl, sourceUrl }` and must show under the image (`CommonsCredit`). `photoThumbUrls` is deprecated (backend#38).
- **Me**: profile fields, `onboarding` state, `defaultTripVisibility`, `locale`, `entitlements[]` (e.g. `best_route_realtime`).

## Stable error codes (starter set)

`UNAUTHENTICATED`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSED`, `ONBOARDING_INCOMPLETE`, `CONSENT_REQUIRED`, `AGE_REQUIREMENT_NOT_MET`, `USERNAME_TAKEN`, `USERNAME_INVALID`, `VALIDATION_FAILED`, `ID_CONFLICT`, `NOT_FOUND`, `FORBIDDEN`, `TRIP_NOT_COPYABLE` (marker copy), `JOIN_REQUEST_DECLINED`, `USER_BLOCKED`, `INVITE_EXPIRED`, `PHOTO_LIMIT_REACHED`, `UPLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `PREMIUM_REQUIRED`, `RATE_LIMITED`, `ACCOUNT_SUSPENDED`, `REAUTH_REQUIRED`, `ROUTING_UNAVAILABLE`, `BBOX_TOO_LARGE`.

Add new codes here when you introduce them.
