# Tripinly Client — Decision Log

Append-only. Newest at the bottom.

| Date | Decision | Why | By |
|---|---|---|---|
| 2026-09-24 | Backend: NestJS REST (`/v1`, JSON, OpenAPI) + Socket.IO, built by a separate agent; client consumes the contract | Product owner | Product owner |
| 2026-09-24 | Sign-in only with Google and Apple; JWT access + rotating refresh tokens | Product requirement | Product owner |
| 2026-09-24 | Minimum age 16; versioned consent at onboarding | EU/GDPR | Product owner |
| 2026-09-24 | "Add to my trips" = one button creating an independent editable copy, no photos/comments | Product requirement (design prototype shows a bookmark; brief wins) | Product owner |
| 2026-09-24 | Multiple photos per marker, one user-chosen cover shown inside the map pin, swipeable gallery | Product requirement (design shows one photo; brief wins) | Product owner |
| 2026-09-24 | Find people by username handle and invite links; no follow system in v1 | Keep v1 small | Product owner |
| 2026-09-24 | Report and block in v1 | Store requirements | Product owner |
| 2026-09-27 | Keep **Google Maps SDK** for drawing the map (Android + iOS); hide Google POIs; no Google Places/Directions/Geocoding | Familiar and stable; MapLibre Compose not stable yet; Google data can't be stored and costs money | Product owner + agent |
| 2026-09-27 | All places, search and routes come from the backend (OpenStreetMap data, Photon, openrouteservice); show OSM attribution | Free and storable | Product owner + agent |
| 2026-09-27 | Map browsing shows Tripinly places prominently and OSM places as dots when zoomed in | Map never empty | Product owner |
| 2026-09-27 | Routes show places along the way (attractions, cafés, restaurants) | Product requirement | Product owner |
| 2026-09-27 | Route lines and along-the-way are free; best route by real travel time is premium, straight-line best route is free | Product requirement | Product owner |
| 2026-09-28 | One agent builds both the backend and this client; contract requests are replaced by changing the backend first (own PR) and listing contract changes in each report | Simpler coordination | Product owner |
| 2026-09-28 | Premium purchases via **RevenueCat**: entitlement `tripinly_pro`, products `lifetime`, `yearly`, `monthly`; the client calls `Purchases.logIn(<backend user id>)`; premium state still comes from `GET /me` | Cross-platform billing with a KMP SDK | Product owner |
| 2026-09-28 | Working defaults accepted: Google only on Android, Google + Apple on iOS; launch languages en, de, hu; design follows the existing app, brief wins on behaviour; default map categories as listed in `05`; Activity = notification history; share links are deep links only | Not contested when asked | Product owner |
| 2026-10-03 | Split `:shared` into `core:*` and `feature:*` modules with convention plugins in `build-logic`; `shared` stays the umbrella and iOS framework | Gradle enforces boundaries; small, uniform build files | Product owner + agent |
| 2026-10-03 | Offline: read-only cache of the user's own trips; edits need a connection, with optimistic updates where listed in `07` | Full offline editing needs a sync queue and conflict handling | Product owner + agent |
| 2026-10-03 | API models are generated from the backend's `openapi.json` | The contract can't drift silently | Product owner + agent |
| 2026-10-03 | One PR per build-order stage, reviewed and merged before the next stage; CI on GitHub Actions (Android build + tests, iOS framework + simulator tests) must be green | Reviewable steps | Product owner + agent |
| 2026-10-03 | Networking libraries: Ktor client 3.6.0 (OkHttp / Darwin engines), OpenAPI Generator 7.14.0 (models only, build time), BuildKonfig 0.23.0 (base URL per environment), kotlinx-collections-immutable 0.5.2 | Ktor is the multiplatform HTTP client (Retrofit is JVM-only, so it can't serve iOS); the rest were named in `07-architecture.md` | Product owner + agent |
| 2026-10-03 | Request logging is our own small Ktor plugin that logs method, path and status only, instead of Ktor's Logging plugin | Query strings carry locations (Nearby, in-view places) and must never be logged | Agent (engineering) |
| 2026-10-03 | Any `401` maps to `DataError.Network.Unauthorized`; other API errors map to `DataError.Network.Api(status, code, message, details)` with the code as a string | Refresh is already handled inside the client, so a 401 that reaches a repository means "no session"; string codes keep working when the server adds codes | Agent (engineering) |
| 2026-10-03 | Token refresh compares the token the failed request sent with the stored one before calling `/auth/refresh` | Sending a refresh token twice revokes the whole session on the server (`REFRESH_TOKEN_REUSED`) | Agent (engineering) |
| 2026-10-03 | Environments are BuildKonfig flavors (`-Pbuildkonfig.flavor=local|staging|production`, staging by default); production has no URL until the domain exists and fails at startup if chosen; `-Ptripinly.useFakeApi=true` binds fakes | One switch for CI and local builds; a missing production URL can't ship silently | Agent (engineering) |
