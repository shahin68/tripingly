# Tripinly Client — Current State

The map of the existing code. Keep it factual and short, and update it whenever structure changes.

Last audit: 2026-10-03, on `develop` at `7cfb1ab` plus the foundation PR (module split). Updated 2026-10-04 for stage 4 (trips on the API). Repo: `shahin68/tripingly`, package `com.falcon.tripingly`.

## Project structure

```
androidApp/            Android shell: MainActivity, TripinglyApplication (starts Koin), manifest with MAPS_API_KEY
iosApp/                Xcode project; calls MainViewController() from the Shared framework
shared/                Umbrella: App() with Navigation 3, initKoin(), iOS framework "Shared" (static)
build-logic/           Convention plugins: tripinly.kmp.library, tripinly.kmp.compose, tripinly.kmp.feature
core/common            AppResult, RootError/DataError, CoroutineDispatchers, DateUtils (dates in the device's locale via platform skeleton formats), ShareManager (expect/actual via Koin)
core/model             Domain models shared across features (account, trip: Trip, TripDetails, TripDay, TripMarker, TripMember, TripInvite, changes). No Compose dependency
core/database          Room database (TripinglyDatabase v3, destructive migration): read-only cache of trips, days, markers and members behind one TripDao; platform builders
core/data              trips/: TripRepository, MarkerRepository, TripInviteRepository over the API with Room as cache (OfflineFirstTripRepository, DefaultMarkerRepository, DefaultTripInviteRepository), FakeTripBackend for useFakeApi; SessionRepository (session state, sign-in/out; every session end runs all Koin-bound `LocalDataCleaner`s) and AccountRepository (onboarding) over the API, FakeAccountBackend for useFakeApi
core/storage           SecureStore: iOS Keychain (cleared on a fresh install), Android Keystore AES-GCM key + encrypted SharedPreferences
core/ui                UiText (resource or server text) with DataError.Network.toUiText(), ObserveAsEvents, shared error strings (en/de/hu)
core/designsystem      TripinglyTheme (colors, typography, shapes, spacing), AppDropdownMenu, ErrorBanner, FloatingSearchBar
core/navigation        Route keys (Home, TripMap) and the saved-state serializers config
core/network           Ktor HttpClient (auth, refresh, headers), Ktorfit with an AppResult converter (error mapping), AuthApi, AccountApi, TripsApi, MarkersApi, InvitesApi, TokenStore (in memory), SessionEvents, CursorPaginator, idempotency keys, API models generated from openapi.json, environments via BuildKonfig
feature/auth           AuthGate (launch / sign-in / onboarding until signed in), SignIn, Onboarding (profile, consents), SocialSignIn: Credential Manager on Android; on iOS the Swift NativeSignIn (iosApp/NativeSignIn.swift) handed to MainViewController
feature/trips          Home screen (My Trips + Social tab), create/rename/reschedule/delete/leave dialogs, members sheet (members, invites)
feature/map            Trip map: day tabs (add/delete day), markers, location, Google Maps (Android) / MapKit (iOS)
```

Dependency direction: `androidApp`/`iosApp` → `shared` → `feature:*` → `core:*`. Features never depend on each other (the `tripinly.kmp.feature` plugin fails the build if they do). Each module has one Koin module (`commonModule`, `databaseModule`, `storageModule`, `networkModule`, `dataModule`, `authModule`, `tripsModule`, `mapModule`); `shared/di/InitKoin.kt` lists them.

Every module has `commonMain`, plus `androidMain`/`iosMain` only where platform code is needed: ShareManager and Koin bindings in `core:common`, DB builders in `core:database`, the HTTP engine and locale in `core:network`, location, permission and map view in `feature:map`.

## Tech stack in use

| Area | Library | Version |
|---|---|---|
| Kotlin | Kotlin Multiplatform | 2.4.10 |
| UI | Compose Multiplatform (shared UI on both platforms) | 1.11.1, Material 3 1.11.0-alpha07 |
| Build | AGP (KMP library plugin), Gradle | 9.0.1, 9.1.0 |
| Navigation | JetBrains Navigation 3 | 1.1.1 |
| DI | Koin (core, compose, compose-viewmodel, android) | 4.2.2 |
| Persistence | Room KMP + bundled SQLite, KSP | 2.8.5, 2.6.2, 2.3.10 |
| Lifecycle | JetBrains lifecycle viewmodel/runtime compose | 2.11.0-beta01 |
| Coroutines / datetime / serialization | kotlinx | 1.10.1 / 0.8.0 / 1.11.0 |
| Maps (Android) | play-services-maps, maps-compose, play-services-location | 19.1.0, 6.5.1, 21.3.0 |
| Maps (iOS) | MapKit via `UIKitView` (not Google Maps) | system |
| Networking | Ktor client (OkHttp on Android, Darwin on iOS), content negotiation, auth | 3.6.0 |
| API models | OpenAPI Generator (`kotlin`, `multiplatform`), build time only | 7.14.0 |
| Build config | BuildKonfig (base URL per environment) | 0.23.0 |
| State collections | kotlinx-collections-immutable (in feature modules) | 0.5.2 |
| Tests | kotlin-test, coroutines-test, Turbine, JUnit 4, Robolectric, Ktor MockEngine | 1.2.0 Turbine, 4.17 Robolectric |

| In-app browser | androidx.browser Custom Tabs (Android), SFSafariViewController (iOS); `rememberInAppUriHandler()` in core:designsystem, provided as `LocalUriHandler` in `App.kt` | 1.8.0 |
| Sign-in | androidx.credentials + googleid (Android); AuthenticationServices and GoogleSignIn-iOS via SPM (iOS) | 1.5.0, 1.1.1, GoogleSignIn-iOS 10.x |

Not present yet: image loading (Coil), Firebase, Socket.IO, detekt/ktlint.

## Architecture pattern

- Clean layers inside each feature: `data` / `domain` / `presentation` packages.
- MVVM + MVI: each screen has `XRoute` (gets the ViewModel from Koin, collects `uiState` with `collectAsStateWithLifecycle`, handles events) and a stateless `XScreen(state, onAction)`. ViewModels expose `StateFlow<State>`, take a sealed `Action` via `onAction`, and send one-shot `Event`s through a `Channel`. `State`, `Action` and `Event` are nested inside the ViewModel class.
- ViewModels call repositories directly; use cases only where they hold a rule (`GetCurrentLocationUseCase`).
- The server is the source of truth. Trips are read from Room (`observe…`) and refreshed from the API; trip, day, member and invite writes go to the API first and the response is saved to Room. Marker add, change, delete, clear day and reorder are optimistic (decided 2026-10-07): `DefaultMarkerRepository` writes them to Room at once with an app-chosen marker UUID, sends them one at a time per trip from the app scope (clearing a day is one `DELETE /days/{id}/markers`), retries a dropped connection twice, and undoes a change that fails and reports it on `MarkerRepository.failures`. It doesn't reload the trip afterwards; the place the server matched is taken from its answer (2026-10-08). `PendingTripWrites` stops a trip reload from overwriting changes still on their way.

## Screens and features

| Screen / feature | Location in code | Data source today | Status | Needed backend endpoints |
|---|---|---|---|---|
| Launch / sign-in / onboarding (`AuthGate` wraps the app in `App.kt`) | `feature/auth/.../presentation/` | API (`/auth/*`, `/me`, `/users/check-username`, `/legal/documents`, `/me/consents`) | Works with developer sign-in on local/staging; Google reports "not set up" until the client IDs exist, Apple until the Apple Developer account and capability exist; tokens in Keychain / Keystore | (in use) |
| Home: My Trips list (owned + shared), search filter, pull to refresh, long-press menu by role (owner: rename, reschedule, private/public, delete; editor: leave; all: members, share), FAB | `feature/trips/.../presentation/screen/HomeScreen.kt`, `HomeViewModel.kt` | `GET /me/trips` cached in Room | Works; offline shows the cached list with a banner | (in use) |
| Create / rename / reschedule / delete / leave dialogs | `feature/trips/.../component/` | `POST /trips`, `PATCH /trips/{id}`, `DELETE /trips/{id}`, `DELETE /trips/{id}/members/{me}` | Works; server errors (e.g. `daysNotEmpty` on reschedule) shown in the dialog | (in use) |
| Members sheet: members, add by username, remove, invite links (create, share, revoke) | `feature/trips/.../presentation/members/` | `GET /trips/{id}`, `/trips/{id}/members`, `/trips/{id}/invites` | Works; accepting an invite has a repository but no UI until deep links (stage 9) | `GET/POST /invites/{token}` (UI later) |
| Share trip | `core/common/.../util/ShareManager*` (`share(text)`), text from trips strings | Text only | Works | — |
| Copy trip / marker | `TripRepository.copyTrip`, `MarkerRepository.copyMarker` | `POST /trips/{id}/copy`, `POST /markers/{id}/copy` | Repository only; UI comes with Explore (stage 7) | (ready) |
| Social tab | Inside `HomeScreen.kt` | none | "Coming soon" placeholder | `GET /explore/trips`, `/places/nearby`, users |
| Trip map: day tabs from server days, add/delete day (waits for the server), tap map to add a numbered stop, remove and clear day (with confirmation) shown at once, failed stop changes undone with a banner (Retry when the connection failed), tapping a stop chip selects its pin, centers the map and opens its info window, chips and pins say "Stop #N" by position, my-location button that asks again after a denial (an explanation first when Android wants one, an Open settings dialog when the system won't show its prompt) and moves to the user once allowed; read-only for viewers | `feature/map/.../MapScreen.kt`, `MapViewModel.kt` | `GET /trips/{id}` cached in Room; markers and days endpoints | Works | `/places/in-view` (stage 5) |

## Map integration

- Common `expect fun GoogleMapView(...)` in `feature/map`.
- **Android:** `maps-compose` `GoogleMap`, `MarkerComposable` with the numbered `TripMarkerIcon`, my-location layer. No map style, so **Google POIs are visible** (must be hidden per rule 4).
- **iOS:** the "GoogleMapView" actual is **Apple MapKit** (`MKMapView` with `MKMarkerAnnotationView`), not the Google Maps SDK. The unlinked `GoogleMaps` import and `GMSServices` setup were removed from `iOSApp.swift` (stage 3) so the app target compiles; the Google Maps iOS SDK comes with the map stage.
- Location: `LocationDataSource` with fused location (Android) and `CLLocationManager` (iOS); the location permission is an `expect/actual` `LocationPermissionController` (`status()`, `request()`, `openSettings()`), re-read whenever the map resumes; the view model decides when to ask, explain or send the user to Settings.
- No OSM attribution yet; no places from the backend.

## Local models vs API contract

Domain models in `core/model/.../trip/` follow the API (UUIDs, `LocalDate` dates, `role`, `visibility`, server-ordered days and markers). DTO ↔ domain mapping is in `core/data/.../trips/TripMapping.kt`; entity ↔ domain mapping in `RoomTripLocalDataSource`. Room keeps dates as `YYYY-MM-DD` strings and `updatedAt` as epoch millis.

## Build and run

- Android: `./gradlew :androidApp:assembleDebug`. Needs `MAPS_API_KEY=...` in `local.properties` (empty key builds, map tiles don't load).
- Local config lives in the untracked root `local.properties` (a Gradle property with the same name is the fallback, e.g. `-P` in CI): `MAPS_API_KEY`, `tripinly.useFakeApi`, `tripinly.devAuthSecret` (staging's developer sign-in secret, never committed) and `tripinly.googleWebClientId` (the backend's Google Web client ID; not secret). Debug builds (Android debuggable, iOS debug binary) log every HTTP call in full, OkHttp-style, through Ktor's `Logging` plugin; release builds don't log. iOS Google sign-in uses the GoogleSignIn-iOS package (SPM) with the iOS client ID as `GIDClientID` and its reversed form as a URL scheme in `iosApp/iosApp/Info.plist` (client IDs are not secret, so they are committed). The iOS bundle ID is `com.falcon.tripingly`, matching the Android package; Sign in with Apple needs the capability on the app ID.
- Unit tests: `./gradlew allTests` (Android host tests + common) on any OS; `./gradlew iosSimulatorArm64Test` on macOS.
- iOS framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`; Xcode runs `:shared:embedAndSignAppleFrameworkForXcode`. iOS deployment target 18.2. There is no iOS Maps key yet (the map is MapKit until the map stage).
- CI (`.github/workflows/ci.yml`): Android assemble + `allTests` on Ubuntu, then iOS framework link + simulator tests on macOS, for PRs into `develop`/`master` and pushes to `develop`.
- The Claude cloud sandbox cannot download from Google Maven (`dl.google.com` is blocked), so Gradle builds run in CI, not in the sandbox.

## Tests

- `core/data` commonTest: `TripRepositoryTest` (MockEngine + in-memory cache: paging, caching, error codes, optimistic marker add/delete/clear day/reorder with undo, no reload after sending, retry after a dropped connection, `ID_CONFLICT` and `NOT_FOUND` treated as done, reload kept off unsent changes).
- `feature/trips` commonTest: `HomeViewModelTest`, `TripMembersViewModelTest` over `FakeTripBackend`.
- `feature/map/src/androidHostTest/.../MapViewModelTest.kt`: Robolectric, with a fake location source and `FakeTripBackend`.

## Gaps and risks

1. **No proactive offline detection.** Offline is noticed when a call fails; writes need a connection (no outbox).
2. **iOS map is MapKit.** Decide on the Google Maps iOS SDK via SPM in the map stage.
3. **Google POIs visible on Android**; no OSM attribution.
4. **Two message types:** `feature:auth` still has its own `UiMessage`; move it to `core:ui`'s `UiText` when auth is next touched.
5. **No marker rename/reorder UI yet** (repository supports both); invite accept and copy have no UI yet.
6. **No architecture or lint checks** beyond module boundaries (no Konsist, detekt or ktlint yet).
