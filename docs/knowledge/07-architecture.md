# Tripinly Client — Architecture Guidelines

**The existing codebase wins.** Use this file for the parts that don't exist yet, and to decide how new layers fit in. If something here conflicts with established patterns in the repo, follow the repo and note the difference in `06-current-state.md`.

## Layers

```
UI (Compose / platform views)
  └── state holders / view models (shared)        — screen state as immutable data + events
        └── repositories (shared)                   — single source of data per domain
              ├── remote data source (Ktor)         — DTOs matching 03-api-contract.md
              ├── realtime source (Socket.IO)       — events → repository updates
              ├── local cache (optional)            — for offline reading of own trips
              └── fake data source (debug only)     — for building before the backend exists
```

- Repository interfaces in shared code; implementations injected by the project's DI.
- DTOs (API shapes) are separate from domain models; map in the repository layer. This keeps existing UI models stable when API fields differ.
- Expose `Flow`/`StateFlow` from repositories; one-off calls as `suspend` functions returning a result type (success / domain error with `code` / network error).

## Preferred libraries (only if the repo doesn't already have an equivalent)

| Need | Preferred |
|---|---|
| HTTP | Ktor client (OkHttp engine on Android, Darwin on iOS) with ContentNegotiation, Auth (bearer + refresh), Logging (headers redacted, debug only) |
| JSON | kotlinx.serialization (`ignoreUnknownKeys = true`, `explicitNulls = false`) |
| Dates | kotlinx-datetime (`Instant` for timestamps, `LocalDate` for dates) |
| Images | Coil 3 (multiplatform) |
| Settings | multiplatform-settings or DataStore |
| Secure storage | Keychain (iOS) and Keystore-backed encryption (Android) behind an `expect/actual` `SecureStore` |
| Socket.IO | A maintained Socket.IO client: Android `io.socket:socket.io-client`, iOS `Socket.IO-Client-Swift`, bridged behind a shared interface (or a KMP Socket.IO library if the user approves) |
| Push | Firebase Messaging on each platform, bridged behind a shared `PushService` |
| Sign-in | Android Credential Manager (Google ID token); iOS GoogleSignIn SDK and AuthenticationServices (Apple) |
| API client generation | Optional: generate DTOs from `/v1/openapi.json` (for example with openapi-generator's Kotlin multiplatform target). Hand-written Ktor + DTOs is fine if kept in sync; ask the user which they prefer if nothing exists |

## Networking rules

- Base URL per environment: local, staging, production (build config). Never hardcode production in debug.
- Headers: `Authorization: Bearer`, `Accept-Language` (app language), `X-Client` (`android|ios`/app version) for diagnostics.
- Token refresh: on `401` with `TOKEN_EXPIRED`, refresh once (single-flight: concurrent requests wait for the same refresh), retry, and if refresh fails (`REFRESH_TOKEN_REUSED`, `ACCOUNT_SUSPENDED`, 401) sign out.
- Timeouts: 15 s default; uploads go directly to the pre-signed URL with longer timeouts.
- Pagination: cursor-based `{ items, nextCursor }` → a shared paging helper.
- Idempotency: send an `Idempotency-Key` (UUID) on content-creating POSTs so retries don't duplicate.
- Error mapping: parse `{ error: { code, message, details } }` into a domain error; map known codes to specific UI (see `03-api-contract.md`).

## Optimistic UI

Use optimistic updates for likes, comment posting, marker reorder and cover changes; roll back on error. Everything else waits for the server.

## Offline

Not a v1 goal. Cache the user's own trips for reading when offline (show a banner); block edits while offline unless the user asks for offline editing later.

## Testing

- `commonTest`: repositories with Ktor `MockEngine`, token refresh single-flight, error mapping, polyline decoding, username validation, age check, state holders.
- Platform UI checked manually on both platforms for every UI change. Note in your report what you checked.
