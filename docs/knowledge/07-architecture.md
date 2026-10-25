# Tripinly Client — Architecture Guidelines

How the app is put together and the rules new code follows. `06-current-state.md` says what exists; this file says how to extend it. Where existing code still breaks a rule here, fix it when that code is next touched for a feature, not in unrelated PRs.

**Consistency over novelty.** New code follows how the codebase already does the same thing. A new style (helper extensions, wrappers, config switches, test hooks in production classes) needs a reason and is applied everywhere at once, decided with the owner; otherwise it is tech debt and doesn't go in.

## Modules

```
androidApp ─┐
iosApp ─────┴─> shared ──> feature:trips, feature:map, (auth, places, photos, social, profile, settings …)
                              │
                              └──> core:common, core:model, core:data, core:database, core:designsystem,
                                   core:navigation, core:location, (core:network, core:storage, core:ui, core:testing …)
```

- **core** holds what several features share. `core:model` (pure domain types, no Compose), `core:common` (AppResult, errors, dispatchers, small platform services), `core:database` (Room), `core:data` (repositories shared by features), `core:designsystem` (theme tokens and stateless components), `core:navigation` (route keys), `core:network` (Ktor, auth, token refresh, error mapping), `core:storage` (secure token storage, settings, and the `DataStore<Preferences>` for small UI state such as each trip's last map view), `core:ui` (UiText, `ObserveAsEvents`, shared error strings), `core:location` (current location and the location permission, expect/actual; since 2026-10-11). `core:testing` (the in-memory fakes, for tests only: added to `commonTest`, never to an app build; since 2026-10-24).
- **feature** modules, one per product area, each with `data`, `domain` and `presentation` packages. A feature never depends on another feature (the build fails if it does). Shared pieces move down to core; cross-feature navigation goes through keys in `core:navigation`, wired in `shared/App.kt`.
- **shared** is the thin umbrella: `App()` with the nav graph, `initKoin()` listing every module's Koin module, and the iOS framework. No feature logic.
- **build-logic** convention plugins keep build files short and identical:
  - `tripinly.kmp.library`: Android + iOS targets, SDK levels, namespace from the module path, test libraries.
  - `tripinly.kmp.compose`: Compose, stability config (`compose-stability.conf`), module-specific `Res` package.
  - `tripinly.kmp.feature`: library + compose + core modules, Koin, lifecycle.
- Classes are `internal` by default; only a module's contract (Koin module, routes, repository interfaces, public composables) is public.
- Split further only when a boundary or build time asks for it.

### Shared UI components (Shahin, 2026-10-09)

- **Ripples follow the shape.** Every clickable surface (cards, chips, rows with rounded backgrounds) clips its touch ripple to its own shape. With a modifier click, clip first: `.clip(shape).combinedClickable(…)`. Material components that take `onClick` (`Card(onClick)`, `Surface(onClick)`) already do this. A square ripple over rounded corners is a bug.
- **Shared building blocks.** Any UI element that looks the same on several screens comes from one component in `core:designsystem`, built with the `ui-component` skill: cards (a clickable card with click, optional long click and a clipped ripple), buttons (primary CTA, secondary, text), FABs, loading spinners, icons, text styles (e.g. title, body and caption texts that already apply the theme's typography and colors): anything reusable, following common design-system practice. Features use these instead of styling Material components themselves, so look and behaviour are fixed in one place. When a screen needs something new, add a variant to the shared component; don't copy styling into a feature. Planned: built as each stage first needs one, moving existing screens over as they're touched. So far: `MessageBanner` (see Errors below), `AppDropdownMenu`, `FloatingSearchBar`, `OsmAttribution`, `SuggestionItem`, `AvatarCircle`/`AvatarChoice`, `SegmentedToggle`, `PillChip`, `PrimaryButton`, `RoundedTextField`, `SectionLabel`, `LikeButton`, `ReportDialog`, `PlaceCategory.icon`.

### Lists (Shahin, 2026-10-24)

- **Lazy lists, horizontal and vertical, show data with `items(list, key = { it.id })`, one item per data entry, and that item's root takes `Modifier.animateItem()`.** Adds, removes and reorders (Best order, a deleted comment, a new photo) then animate once with the default specs. Never build items in a `forEach` loop, and never split one entry into several items (a label and its chip go in one `Row` inside one item), since separate items animate apart. Composables used as list items take a `modifier: Modifier = Modifier` and apply it to their root.
- Single `item { }` headers, footers and empty texts, and fixed lists of enum options (filter pills), need no `animateItem()`. Short non-lazy `Column`/`Row` lists inside a dialog or form stay as they are.
- Spacing and padding in lists use `MaterialTheme.spacing` tokens, not raw `dp`.

### Landscape (Shahin, 2026-10-11)

- **Every screen works in landscape too.** Layouts fit the short height (scroll or rearrange instead of clipping), cards and overlays over the map stay reachable, and nothing is sized for portrait only.
- **A rotation changes nothing the user sees except the layout.** Android recreates the activity, so state that must survive it lives in the view model or in `rememberSaveable`, never only in `remember`: the map keeps its camera and selection, one-off effects (framing the stops, opening a card) don't rerun, and scroll positions stay.
- Check portrait → landscape → portrait on every UI change, on both platforms, and say in the report what was checked.

## Where logic belongs

- **Domain:** models, repository interfaces, and rules (trip date math, "can this user edit", route mode limits). Use cases only when they combine repositories or hold a rule; no pass-through use cases.
- **Data:** DTOs and Room entities never leave the data layer. Repositories map them to domain models, map HTTP errors to typed domain errors by `error.code`, and own caching, retries and timeouts.
- **Presentation:** the ViewModel turns domain data into one screen state. Formatting (dates, distances, plurals) lives in presentation mappers that return `UiText`, never inside composables and never as hardcoded strings.
- **Platform code** only for what must be native (map view, sign-in SDKs, photo picker, camera, push, secure storage), behind interfaces in `commonMain`, bound through `expect/actual` Koin modules.

## Source of truth and offline

- The server is the source of truth. Room caches what the user needs offline: their own trips, **read-only** (decided 2026-10-03). Show an offline banner and block edits while offline; marker actions on the map are the exception (tried at once and undone if they can't be sent, see below).
- Writes go to the API. Map marker actions (add, move, rename, delete, clear day, reorder) are optimistic and must feel instant (decided 2026-10-07): the app chooses the marker UUID, writes Room at once, sends the change in the background through a per-trip queue in the application scope, undoes it if the server refuses or the connection stays down, and shows a banner (Retry for connection problems). A trip reload never overwrites marker changes still in that queue. Every request is made only as often as it's needed: no reload after a change the device already shows, one request for a bulk action (2026-10-08). Trip create/edit, days, members, comments and likes wait for the server with a small loading indicator. The app is online-first, not offline-first: nothing is queued across app restarts.

## Values the server controls (Shahin, 2026-10-10)

Values that tune app behaviour (cache and refresh times, limits, intervals) come from `GET /app-config` through `AppConfigRepository` (`core:data`), so they change on the server without an app update. The app reads the settings once per launch and uses its built-in defaults until they arrive or if the call fails. Feature flags will come the same way after v1. See the `api-integration` skill.

## MVI contract

- **State:** one immutable `XUiState` per screen with defaults; `ImmutableList` for lists; derived values computed before they reach the UI; user-facing text as `UiText`, never raw exception messages.
- **Actions:** one sealed `XAction` and one `onAction(action)` entry point. Actions describe what the user did (`TitleChanged`, `SaveClicked`); the ViewModel decides what that means.
- **Effects:** anything that can be state is state (a dialog shown, a field error). True one-shots (navigate, open the share sheet) go through a `Channel`, collected with `ObserveAsEvents` on `Dispatchers.Main.immediate` while the lifecycle is started, so none are lost.
- **Reducers:** non-trivial transitions are pure `reduce(state, input)` functions, tested without coroutines.
- **Lifetime (scoping allowed, Shahin 2026-10-19):** a screen's ViewModels are scoped to its Navigation 3 back-stack entry (`rememberViewModelStoreNavEntryDecorator` from `lifecycle-viewmodel-navigation3` on the `NavDisplay` in `App.kt`). Leaving the screen clears them; signing out removes the whole back stack, so every signed-in screen starts fresh for the next account. Rotation keeps them. Screens outside the back stack (sign-in, onboarding) reset themselves from the session. Scoping is a normal tool, not a workaround: data correctness still lives in repositories, and a Koin `single` holding user data still needs a `LocalDataCleaner`.
- **Optimistic switches (Shahin 2026-10-19):** every button, toggle or switch whose server call may be slow shows the tap at once and keeps it while the server answers. A newer tap on the same control cancels the change still on its way (the latest tap wins; server answers never move the control again), and only an error moves it back, to the value the server last confirmed, with the error banner. Reference: `SettingsViewModel.setVisibility` and `setNotification`.
- **Streams:** repository flows become state with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. ViewModels never expose `MutableStateFlow`, never hold platform objects and never pick dispatchers; dispatchers are injected where blocking work happens (data layer).

## Screen state and UI structure (Shahin, 2026-10-21)

A screen must stay readable: from the state alone you can tell which UI element draws what, and the composables only lay out and draw.

- **State is grouped by UI element.** A screen's `State` holds one small `@Immutable` sub-state per element it draws (header, tabs, a card, a sheet, a dialog, the map's layers), and each sub-state holds only what that element shows and needs to decide. A section composable takes its own sub-state, never the whole screen state. A flat state that grows past about 15 fields is split. Nullable sub-states say "this element is closed" (`stopCard: StopCardState?`) instead of a separate `isXShown` flag next to the data.
- **Actions are grouped the same way** (Shahin, 2026-10-22). A screen's `Action` is a sealed interface of one nested sealed group per UI element (`MapAction.Header`, `.Days`, `.Plan`, `.StopCard`, `.Dialog`, …), not one long flat list; the ViewModel's `onAction` only routes each group to its own small handler.
- **One state shape.** The ViewModel keeps its private state in the same grouping and maps it once; no second flat `UiState` that repeats the public fields one by one.
- **Composables don't calculate.** Layout sizes stay in layout code. They are never measured into `remember` variables and fed into business logic or camera math. What always covers the map goes to the map as content padding. Every fit is turned into a plain camera move by one function, `fittedCamera` (the SDKs' own Web Mercator fit, unit-tested), from the map's size, its padding and how far the plan sheet is pulled up right now (Shahin, 2026-10-22); the map views only move the camera and hold no fit state. No other projection math.
- **`remember` is for UI objects only:** focus requesters, scroll, sheet, pager and animation state, and remembered lambdas. Never for business values, derived data (the ViewModel computes it), or "keep the last value while it animates out" tricks (`remember { mutableStateOf(x) }.apply { … }.value`). Content that animates out uses `AnimatedContent`/`AnimatedVisibility` with the closing sub-state kept by the ViewModel until the element is gone, or the element keeps its own target state.
- **Camera and other platform views** are driven by commands from the ViewModel, not by state the view compares against its own to avoid jitter. The map's are `CameraCommand`s (`MoveTo`, `Fit(bounds)`) on the ViewModel's own `cameraCommands` flow, apart from navigation events because only the map view consumes them, each once. The view owns its camera, flies or fits by itself, and reports where it rests; the ViewModel keeps that only as the start for a new map view (rotation, reopening).
- **Shorter code wins (Shahin, 2026-10-22).** Whenever a change can make code shorter without hiding what it does, take it: less code is less tech debt. Prefer designs where adding a thing adds a file, not lines to a central class. Examples: a list of small handlers injected as a set (Koin `getAll<StartupTask>()`, run one by one) instead of a class that takes every dependency and calls each by hand; a map view that only moves the camera instead of one that fits, holds and refits. A class that grows with every feature (constructor parameters, `when` branches, init calls) is a review finding.
- **Size limits are real limits.** Screens, ViewModels and components stay under about 300 lines; a ViewModel that handles several UI elements splits their logic into small internal state holders or reducers in their own files.
- **State holders** (2026-10-23) are plain `internal` classes in the screen's package that take the ViewModel's scope, its state flow, the repositories they need and callbacks for events and messages, and update only their own sub-state: `HomeCreateTrip` (the New trip form), `SocialExplore` and `SocialNearby`. Behaviour two screens share is one holder both delegate to, never a copy: `PublicTripActions` likes, saves and reports public trip cards on Explore and on profiles.
- **What a person typed or opened survives rotation.** A dialog's draft, a picker or menu that is open, and a selected item live in the ViewModel or in `rememberSaveable`, never in plain `remember`.

## Passing state down (property drilling)

- A stateful `XRoute` gets the ViewModel from Koin, collects state with `collectAsStateWithLifecycle`, handles effects, and calls a stateless `XScreen(state, onAction)`. The ViewModel is never passed further down.
- Inside a screen, feature sections may take `onAction`. Design-system components take plain values and specific lambdas (`onClick`, `onValueChange`), never feature actions, so they stay reusable and previewable.
- Drilling past about three levels is a smell. Fix it with slot APIs (content lambdas), a small remembered UI state holder for UI-only state (map camera, sheet state), or a smaller sub-state. `CompositionLocal` is only for ambient things (theme, image loader), never for data or callbacks.
- Parameters are stable: immutable models, remembered lambdas, method references.
- Every stateless screen and component has previews driven by a `PreviewParameterProvider` of sample states, including loading, empty and error.

## Errors, concurrency, DI and navigation

- `AppResult<T, E>` across layer boundaries; nothing throws across them, and `CancellationException` is always rethrown.
- Messages for the user (Shahin, 2026-10-16): the whole app has **one floating banner** (`MessageBanner` in `core:designsystem`, provided by `TripinglyTheme`, drawn by `MessageBannerHost` at the app root). Screens keep `message: UiText?` in their state and hand it over with `ShowMessage(state.message?.asString(), onShown = { clear it })`; never draw a banner inside a screen. It hides after 4 s (8 s with an action such as Retry), swipes away sideways, shows one message at a time, and a newer one replaces it after 1.5 s; the same text isn't repeated. Confirmations and notes ("Saved to My Trips.", "Thanks. We’ll take a look.", the offline note) are `UiText.Note` and show in neutral colors (`isError = state.message !is UiText.Note`); everything else uses the error colors. Every modal sheet and full-screen dialog puts its own `MessageBannerHost` on top of its content (a sheet is a window above the app); only the newest host draws. Errors say what failed, then why: `error.toUiText(Res.string.x_error_…)` with our sentence ("Couldn’t load your trips."), followed by the server's message or the connection problem; never a bare "Something went wrong". Form field errors stay inline in their form.
- Structured concurrency only: no `GlobalScope`; app-wide work runs in one injected application scope.
- Search as you type (Shahin, 2026-10-09): a search that calls the server is debounced 300 ms with coroutines in the ViewModel (each keystroke cancels the pending `Job`, then `delay` and call), so fast typing sends one request and never runs into rate limits; a cleared field clears results at once. Searching local data (e.g. filtering My Trips) is cheap and runs on every keystroke.
- One Koin module per Gradle module, `viewModelOf(::XViewModel)` with constructor injection, platform bindings through `expect/actual` modules.
- Navigation 3 with typed, serializable keys in `core:navigation`; ViewModels read arguments from their key; deep links map to keys in one place.

## Networking (`core:network`, built in stage 2)

- Ktor client (OkHttp engine on Android, Darwin on iOS) with ContentNegotiation and Auth (bearer + refresh). Endpoints are declared as **Ktorfit** interfaces (Retrofit-style annotations, code generated by KSP) whose suspend functions return `AppResult<T, DataError.Network>`. Debug builds log every call in full (Ktor `Logging`, OkHttp format, `LogLevel.ALL`); release builds log nothing. kotlinx.serialization with `ignoreUnknownKeys = true`, `explicitNulls = false`.
- **API models are generated from the backend's `openapi.json`** (decided 2026-10-03) into `core:network`; repositories map them to domain models.
- Base URL per environment from BuildKonfig flavors (`-Pbuildkonfig.flavor=local|staging|production`, staging by default). Staging is `https://api-staging-4ade.up.railway.app/v1`; local is `http://10.0.2.2:3000/v1` on the Android emulator and `http://localhost:3000/v1` on the iOS simulator; production waits for the domain. Plain HTTP is allowed only in Android debug builds and only to `10.0.2.2` and `localhost` (`androidApp/src/debug`); release builds are HTTPS only.
- Headers: `Authorization: Bearer`, `Accept-Language` (app language), `X-Client` (`android|ios`/app version).
- Token refresh: on `401` with `TOKEN_EXPIRED`, refresh once (single-flight: concurrent requests wait for the same refresh), retry; if refresh fails (`REFRESH_TOKEN_REUSED`, `ACCOUNT_SUSPENDED`, 401) sign out.
- Timeouts: 15 s default; uploads go directly to the pre-signed URL with longer timeouts.
- Pagination: cursor-based `{ items, nextCursor }` through `CursorPaginator`.
- Idempotency: content-creating POSTs (trips, days, markers, add-to-trip, marker copy, comments, invites) send `Idempotency-Key`, one UUID per user action reused on its retries. A marker create uses the marker's own app-chosen ID as its key.
- Error mapping: the Ktorfit `AppResult` converter turns `{ error: { code, message, details } }` into `DataError.Network.Api` (code as a string, details flattened) and any 401 into `Unauthorized`; repositories map known codes to specific UI (see `03-api-contract.md`).

## Libraries

Prefer what the project already has. New ones are confirmed with the user when their stage starts:

| Need | Preferred |
|---|---|
| HTTP | Ktor client, endpoints declared with Ktorfit |
| Immutable state collections | kotlinx-collections-immutable |
| OpenAPI models | an OpenAPI generator with a Kotlin multiplatform target |
| Images | Coil 3; our photos are cached by photo ID + size (`PhotoImage`, `photoRequest`), never by their signed URL |
| Camera | CameraX with its Compose viewfinder (`camera-compose`) on Android, not `camera-view` (it pulls in CameraX video, Guava and AppCompat); AVFoundation on iOS |
| Secure storage | Our own `SecureStore` in `core:storage`: Keychain (iOS), Keystore AES-GCM key (Android); no library (decided 2026-10-03) |
| Settings | multiplatform-settings or DataStore |
| Small UI state | `androidx.datastore:datastore-preferences-core` (KMP) in `core:storage`, file `ui_state.preferences_pb`; holds only what rebuilds a screen (e.g. a trip's day, camera, Route on/off), wiped at sign-out by a `LocalDataCleaner` (2026-10-19) |
| Socket.IO | our own Socket.IO client in shared code over Ktor's WebSockets (`SocketIoRealtimeClient` in `core:network`) behind `RealtimeClient`; no platform library (decided 2026-10-11) |
| Purchases | RevenueCat's native SDKs behind `StorePurchases` (`core:data`), bound by the app shell like push: Android `com.revenuecat.purchases:purchases` in `androidApp`, iOS the `RevenueCat` Swift package through `NativePurchases` (Swift) handed to `MainViewController`. Keys from `local.properties` (`revenuecat.androidKey`, `revenuecat.iosKey`); without one the build has no purchases. Not the KMP wrapper, which would link RevenueCat's iOS library into the shared framework and its tests (decided 2026-10-20) |
| Push | Firebase Messaging per platform behind `PushMessaging` (`core:data`), bound by the app shell (`androidApp` today; iOS once APNs is set up); Firebase starts from `local.properties` values, no `google-services.json` in the repo and no Google Services plugin (decided 2026-10-11) |
| Sign-in | Android Credential Manager + googleid; iOS GoogleSignIn (SPM) and AuthenticationServices, in Swift behind `NativeSignIn` (decided 2026-10-03) |
| Quality | detekt with Compose rules, ktlint, Konsist architecture tests, Roborazzi screenshots |

## Testing

- **ViewModels:** fakes from the test-only `core:testing` module (no mocking library, which also keeps tests running on iOS; no app build contains them), Turbine, a test dispatcher set as Main. Every action and every error code the screen handles has a test.
- **Repositories:** Ktor `MockEngine` serving JSON copied from real staging responses, so tests break when the contract drifts.
- **Pure logic:** mappers, reducers and domain rules get plain unit tests in `commonTest`.
- **UI:** screenshot tests for design-system components and key screens on Android, plus a few end-to-end flows; no exhaustive UI tests. Flows are Compose UI tests in the feature's `androidHostTest` on Robolectric (`runComposeUiTest`, see `MapFlowTest`): the real route and view model against the `Fake*Backend`, driven by visible text and content descriptions.
- **Architecture:** module boundaries are enforced by Gradle; layer rules by Konsist once added.
- Test names read as behaviour: `save_withEmptyTitle_showsFieldError`.
- Platform UI is checked on both platforms for every UI change; the report says what was checked.

## Packaging and naming

- Package by module, then layer: `com.falcon.tripingly.feature.trips.presentation.detail`. The package root matches the module path.
- Names: `XRoute`, `XScreen`, `XViewModel`, `XUiState`, `XAction`, `XEffect`; `XDto`, `XEntity`, plain domain names; `XRepository` interface with `DefaultXRepository` or `OfflineFirstXRepository`.
- One public type per file, files under about 300 lines, no comments that restate code.
