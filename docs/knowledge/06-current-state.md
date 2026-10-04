# Tripinly Client — Current State

The map of the existing code. Keep it factual and short, and update it whenever structure changes.

Last audit: 2026-10-03, on `develop` at `7cfb1ab` plus the foundation PR (module split). Repo: `shahin68/tripingly`, package `com.falcon.tripingly`.

## Project structure

```
androidApp/            Android shell: MainActivity, TripinglyApplication (starts Koin), manifest with MAPS_API_KEY
iosApp/                Xcode project; calls MainViewController() from the Shared framework
shared/                Umbrella: App() with Navigation 3, initKoin(), iOS framework "Shared" (static)
build-logic/           Convention plugins: tripinly.kmp.library, tripinly.kmp.compose, tripinly.kmp.feature
core/common            AppResult, RootError/DataError, CoroutineDispatchers, DateUtils, ShareManager (expect/actual via Koin)
core/model             Domain models shared across features (Trip). No Compose dependency
core/database          Room database (TripinglyDatabase v2), TripDao, MarkerDao, entities, platform builders
core/data              TripRepository (+ Room-backed TripRepositoryImpl); SessionRepository (session state, sign-in/out) and AccountRepository (onboarding) over the API, FakeAccountBackend for useFakeApi
core/storage           SecureStore: iOS Keychain (cleared on a fresh install), Android Keystore AES-GCM key + encrypted SharedPreferences
core/designsystem      TripinglyTheme (colors, typography, shapes, spacing), AppDropdownMenu, ErrorBanner, FloatingSearchBar
core/navigation        Route keys (Home, TripMap) and the saved-state serializers config
core/network           Ktor HttpClient (auth, refresh, headers), Ktorfit with an AppResult converter (error mapping), AuthApi, AccountApi, TokenStore (in memory), SessionEvents, CursorPaginator, idempotency keys, API models generated from openapi.json, environments via BuildKonfig
feature/auth           AuthGate (launch / sign-in / onboarding until signed in), SignIn, Onboarding (profile, consents), SocialSignIn: Credential Manager on Android; on iOS the Swift NativeSignIn (iosApp/NativeSignIn.swift) handed to MainViewController
feature/trips          Home screen (My Trips + Social tab), create/rename/reschedule dialogs, trip use cases
feature/map            Trip map: day tabs, markers, location, Google Maps (Android) / MapKit (iOS)
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
- Use cases wrap every repository call, most of them pass-through.
- Data is local only (Room); there is no backend connection yet.

## Screens and features

| Screen / feature | Location in code | Data source today | Status | Needed backend endpoints |
|---|---|---|---|---|
| Launch / sign-in / onboarding (`AuthGate` wraps the app in `App.kt`) | `feature/auth/.../presentation/` | API (`/auth/*`, `/me`, `/users/check-username`, `/legal/documents`, `/me/consents`) | Works with developer sign-in on local/staging; Google reports "not set up" until the client IDs exist, Apple until the Apple Developer account and capability exist; tokens in Keychain / Keystore | (in use) |
| Home: My Trips list, search box, long-press menu (rename, reschedule, share, delete), FAB | `feature/trips/.../presentation/screen/HomeScreen.kt`, `HomeViewModel.kt` | Room `trips` table | Works locally; search field is not wired to filtering | `GET /me/trips`, `PATCH /trips/{id}`, `DELETE /trips/{id}` |
| Create trip dialog (name + date range) | `feature/trips/.../component/CreateTripDialog.kt` | Room | Works locally; ID is `trip_<random int>` | `POST /trips` |
| Rename / reschedule dialogs | `feature/trips/.../component/` | Room | Works locally | `PATCH /trips/{id}` |
| Share trip | `core/common/.../util/ShareManager*` | Text only, hardcoded English text in platform code | Works | Invite/share links (`/trips/{id}/invites`) later |
| Social tab | Inside `HomeScreen.kt` | none | "Coming soon" placeholder | `GET /explore/trips`, `/places/nearby`, users |
| Trip map: day tabs, tap map to add numbered stop, marker list, clear day, my-location | `feature/map/.../MapScreen.kt`, `MapViewModel.kt` | Room `markers` table (by `tripId` + `dayIndex`) | Works locally; days are derived from trip dates, not stored | `GET /trips/{id}`, days and markers endpoints, `/places/in-view` |

## Map integration

- Common `expect fun GoogleMapView(...)` in `feature/map`.
- **Android:** `maps-compose` `GoogleMap`, `MarkerComposable` with the numbered `TripMarkerIcon`, my-location layer. No map style, so **Google POIs are visible** (must be hidden per rule 4).
- **iOS:** the "GoogleMapView" actual is **Apple MapKit** (`MKMapView` with `MKMarkerAnnotationView`), not the Google Maps SDK. The unlinked `GoogleMaps` import and `GMSServices` setup were removed from `iOSApp.swift` (stage 3) so the app target compiles; the Google Maps iOS SDK comes with the map stage.
- Location: `LocationDataSource` with fused location (Android) and `CLLocationManager` (iOS); permission launcher is `expect/actual`.
- No OSM attribution yet; no places from the backend.

## Local models vs API contract

| Local | API (`03-api-contract.md`) | Mapping needed |
|---|---|---|
| `Trip(id: String "trip_123", name, startDate, endDate: LocalDate, imageUrl)` | UUID `id`, `title`, `startDate`/`endDate` `YYYY-MM-DD`, `visibility`, `role`, `coverThumbUrl`, `dayCount`, `markerCount` | `name`→`title`; IDs become server UUIDs; add visibility/role; `imageUrl`→`coverThumbUrl` |
| Days: implicit `dayIndex` from trip dates | Days are entities with UUIDs (`POST /trips/{id}/days`) | Store days; markers reference `dayId` |
| `MapMarker(id, position: Coordinates(latitude, longitude), title, orderNumber, snippet, color)` | Marker with UUID, `dayId`, `position {lat, lng}`, `title`, `note`, `placeId`, `order`, cover thumb | `latitude/longitude`→`lat/lng`; `orderNumber`→server order; drop `color` or keep it client-side |
| Room stores dates as epoch millis at UTC midnight | `YYYY-MM-DD` strings | Use `LocalDate` end to end |

## Build and run

- Android: `./gradlew :androidApp:assembleDebug`. Needs `MAPS_API_KEY=...` in `local.properties` (empty key builds, map tiles don't load).
- Local config lives in the untracked root `local.properties` (a Gradle property with the same name is the fallback, e.g. `-P` in CI): `MAPS_API_KEY`, `tripinly.useFakeApi`, `tripinly.devAuthSecret` (staging's developer sign-in secret, never committed) and `tripinly.googleWebClientId` (the backend's Google Web client ID; not secret). iOS Google sign-in uses the GoogleSignIn-iOS package (SPM) with the iOS client ID as `GIDClientID` and its reversed form as a URL scheme in `iosApp/iosApp/Info.plist` (client IDs are not secret, so they are committed). The iOS bundle ID is `com.falcon.tripingly`, matching the Android package; Sign in with Apple needs the capability on the app ID.
- Unit tests: `./gradlew allTests` (Android host tests + common) on any OS; `./gradlew iosSimulatorArm64Test` on macOS.
- iOS framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`; Xcode runs `:shared:embedAndSignAppleFrameworkForXcode`. iOS deployment target 18.2. There is no iOS Maps key yet (the map is MapKit until the map stage).
- CI (`.github/workflows/ci.yml`): Android assemble + `allTests` on Ubuntu, then iOS framework link + simulator tests on macOS, for PRs into `develop`/`master` and pushes to `develop`.
- The Claude cloud sandbox cannot download from Google Maven (`dl.google.com` is blocked), so Gradle builds run in CI, not in the sandbox.

## Tests

- `feature/map/src/androidHostTest/.../MapViewModelTest.kt`: Robolectric + Turbine, with fakes for location, `MarkerDao` and `TripRepository`.
- No tests for `HomeViewModel`, repositories, or iOS.

## Gaps and risks

1. **Only sign-in and onboarding use the API** (stage 3); trips are still local. Tokens are in Keychain / Keystore, so the session survives restarts. Everything is still local Room data with client-generated IDs.
2. **Room is the source of truth**: moving to "server is truth, Room caches own trips" changes repositories, IDs and the schema (stage 4).
3. **iOS map is MapKit.** Decide on the Google Maps iOS SDK via SPM in the map stage.
4. **Google POIs visible on Android**; no OSM attribution.
5. **Hardcoded user-facing text in code**: share message ("Check out my trip…"), "Unknown Trip" fallback in `MapViewModel`, English-only month names in `DateUtils`. Only English `strings.xml` exists (de and hu are missing).
6. **Layer leaks**: map use cases call `MarkerDao` directly (domain → data), and ViewModels hold `errorMessage: String` resolved from resources instead of a `UiText`. Fixed when each feature moves to the API.
7. **No architecture or lint checks** beyond module boundaries (no Konsist, detekt or ktlint yet).
