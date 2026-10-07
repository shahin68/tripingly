# Tripinly Client — Architecture Guidelines

How the app is put together and the rules new code follows. `06-current-state.md` says what exists; this file says how to extend it. Where existing code still breaks a rule here, fix it when that code is next touched for a feature, not in unrelated PRs.

## Modules

```
androidApp ─┐
iosApp ─────┴─> shared ──> feature:trips, feature:map, (auth, places, photos, social, profile, settings …)
                              │
                              └──> core:common, core:model, core:data, core:database, core:designsystem,
                                   core:navigation, (core:network, core:storage, core:ui, core:testing …)
```

- **core** holds what several features share. `core:model` (pure domain types, no Compose), `core:common` (AppResult, errors, dispatchers, small platform services), `core:database` (Room), `core:data` (repositories shared by features), `core:designsystem` (theme tokens and stateless components), `core:navigation` (route keys), `core:network` (Ktor, auth, token refresh, error mapping), `core:storage` (secure token storage, settings), `core:ui` (UiText, `ObserveAsEvents`, shared error strings). Planned: `core:testing` (shared fakes, main-dispatcher rule). Create a planned module in the stage that first needs it.
- **feature** modules, one per product area, each with `data`, `domain` and `presentation` packages. A feature never depends on another feature (the build fails if it does). Shared pieces move down to core; cross-feature navigation goes through keys in `core:navigation`, wired in `shared/App.kt`.
- **shared** is the thin umbrella: `App()` with the nav graph, `initKoin()` listing every module's Koin module, and the iOS framework. No feature logic.
- **build-logic** convention plugins keep build files short and identical:
  - `tripinly.kmp.library`: Android + iOS targets, SDK levels, namespace from the module path, test libraries.
  - `tripinly.kmp.compose`: Compose, stability config (`compose-stability.conf`), module-specific `Res` package.
  - `tripinly.kmp.feature`: library + compose + core modules, Koin, lifecycle.
- Classes are `internal` by default; only a module's contract (Koin module, routes, repository interfaces, public composables) is public.
- Split further only when a boundary or build time asks for it.

## Where logic belongs

- **Domain:** models, repository interfaces, and rules (trip date math, "can this user edit", route mode limits). Use cases only when they combine repositories or hold a rule; no pass-through use cases.
- **Data:** DTOs and Room entities never leave the data layer. Repositories map them to domain models, map HTTP errors to typed domain errors by `error.code`, and own caching, retries and timeouts.
- **Presentation:** the ViewModel turns domain data into one screen state. Formatting (dates, distances, plurals) lives in presentation mappers that return `UiText`, never inside composables and never as hardcoded strings.
- **Platform code** only for what must be native (map view, sign-in SDKs, photo picker, push, secure storage), behind interfaces in `commonMain`, bound through `expect/actual` Koin modules.

## Source of truth and offline

- The server is the source of truth. Room caches what the user needs offline: their own trips, **read-only** (decided 2026-10-03). Show an offline banner and block edits while offline; marker actions on the map are the exception (tried at once and undone if they can't be sent, see below).
- Writes go to the API. Map marker actions (add, move, rename, delete, reorder) are optimistic and must feel instant (decided 2026-10-07): the app chooses the marker UUID, writes Room at once, sends the change in the background through a per-trip queue in the application scope, undoes it if the server refuses or the connection stays down, and shows a banner (Retry for connection problems). A trip reload never overwrites marker changes still in that queue. Trip create/edit, days, members, comments and likes wait for the server with a small loading indicator. The app is online-first, not offline-first: nothing is queued across app restarts.

## MVI contract

- **State:** one immutable `XUiState` per screen with defaults; `ImmutableList` for lists; derived values computed before they reach the UI; user-facing text as `UiText`, never raw exception messages.
- **Actions:** one sealed `XAction` and one `onAction(action)` entry point. Actions describe what the user did (`TitleChanged`, `SaveClicked`); the ViewModel decides what that means.
- **Effects:** anything that can be state is state (a dialog shown, a field error). True one-shots (navigate, open the share sheet) go through a `Channel`, collected with `ObserveAsEvents` on `Dispatchers.Main.immediate` while the lifecycle is started, so none are lost.
- **Reducers:** non-trivial transitions are pure `reduce(state, input)` functions, tested without coroutines.
- **Streams:** repository flows become state with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. ViewModels never expose `MutableStateFlow`, never hold platform objects and never pick dispatchers; dispatchers are injected where blocking work happens (data layer).

## Passing state down (property drilling)

- A stateful `XRoute` gets the ViewModel from Koin, collects state with `collectAsStateWithLifecycle`, handles effects, and calls a stateless `XScreen(state, onAction)`. The ViewModel is never passed further down.
- Inside a screen, feature sections may take `onAction`. Design-system components take plain values and specific lambdas (`onClick`, `onValueChange`), never feature actions, so they stay reusable and previewable.
- Drilling past about three levels is a smell. Fix it with slot APIs (content lambdas), a small remembered UI state holder for UI-only state (map camera, sheet state), or a smaller sub-state. `CompositionLocal` is only for ambient things (theme, image loader), never for data or callbacks.
- Parameters are stable: immutable models, remembered lambdas, method references.
- Every stateless screen and component has previews driven by a `PreviewParameterProvider` of sample states, including loading, empty and error.

## Errors, concurrency, DI and navigation

- `AppResult<T, E>` across layer boundaries; nothing throws across them, and `CancellationException` is always rethrown.
- Structured concurrency only: no `GlobalScope`; app-wide work runs in one injected application scope.
- One Koin module per Gradle module, `viewModelOf(::XViewModel)` with constructor injection, platform bindings through `expect/actual` modules.
- Navigation 3 with typed, serializable keys in `core:navigation`; ViewModels read arguments from their key; deep links map to keys in one place.

## Networking (`core:network`, built in stage 2)

- Ktor client (OkHttp engine on Android, Darwin on iOS) with ContentNegotiation and Auth (bearer + refresh). Endpoints are declared as **Ktorfit** interfaces (Retrofit-style annotations, code generated by KSP) whose suspend functions return `AppResult<T, DataError.Network>`. Request logging logs method, path and status only (no query strings, headers or bodies), and is off in production. kotlinx.serialization with `ignoreUnknownKeys = true`, `explicitNulls = false`.
- **API models are generated from the backend's `openapi.json`** (decided 2026-10-03) into `core:network`; repositories map them to domain models.
- Base URL per environment from BuildKonfig flavors (`-Pbuildkonfig.flavor=local|staging|production`, staging by default). Staging is `https://api-staging-4ade.up.railway.app/v1`; local is `http://10.0.2.2:3000/v1` on the Android emulator and `http://localhost:3000/v1` on the iOS simulator; production waits for the domain.
- Headers: `Authorization: Bearer`, `Accept-Language` (app language), `X-Client` (`android|ios`/app version).
- Token refresh: on `401` with `TOKEN_EXPIRED`, refresh once (single-flight: concurrent requests wait for the same refresh), retry; if refresh fails (`REFRESH_TOKEN_REUSED`, `ACCOUNT_SUSPENDED`, 401) sign out.
- Timeouts: 15 s default; uploads go directly to the pre-signed URL with longer timeouts.
- Pagination: cursor-based `{ items, nextCursor }` through `CursorPaginator`.
- Idempotency: content-creating POSTs (trips, trip copy, days, markers, add-to-trip, marker copy, comments, invites) send `Idempotency-Key`, one UUID per user action reused on its retries. A marker create uses the marker's own app-chosen ID as its key.
- Error mapping: the Ktorfit `AppResult` converter turns `{ error: { code, message, details } }` into `DataError.Network.Api` (code as a string, details flattened) and any 401 into `Unauthorized`; repositories map known codes to specific UI (see `03-api-contract.md`).

## Libraries

Prefer what the project already has. New ones are confirmed with the user when their stage starts:

| Need | Preferred |
|---|---|
| HTTP | Ktor client, endpoints declared with Ktorfit |
| Immutable state collections | kotlinx-collections-immutable |
| OpenAPI models | an OpenAPI generator with a Kotlin multiplatform target |
| Images | Coil 3 |
| Secure storage | Our own `SecureStore` in `core:storage`: Keychain (iOS), Keystore AES-GCM key (Android); no library (decided 2026-10-03) |
| Settings | multiplatform-settings or DataStore |
| Socket.IO | a maintained client per platform behind a shared interface |
| Push | Firebase Messaging per platform behind a shared `PushService` |
| Sign-in | Android Credential Manager + googleid; iOS GoogleSignIn (SPM) and AuthenticationServices, in Swift behind `NativeSignIn` (decided 2026-10-03) |
| Quality | detekt with Compose rules, ktlint, Konsist architecture tests, Roborazzi screenshots |

## Testing

- **ViewModels:** fakes (no mocking library, which also keeps tests running on iOS), Turbine, a test dispatcher set as Main. Every action and every error code the screen handles has a test.
- **Repositories:** Ktor `MockEngine` serving JSON copied from real staging responses, so tests break when the contract drifts.
- **Pure logic:** mappers, reducers and domain rules get plain unit tests in `commonTest`.
- **UI:** screenshot tests for design-system components and key screens on Android, plus a few end-to-end flows; no exhaustive UI tests.
- **Architecture:** module boundaries are enforced by Gradle; layer rules by Konsist once added.
- Test names read as behaviour: `save_withEmptyTitle_showsFieldError`.
- Platform UI is checked on both platforms for every UI change; the report says what was checked.

## Packaging and naming

- Package by module, then layer: `com.falcon.tripingly.feature.trips.presentation.detail`. The package root matches the module path.
- Names: `XRoute`, `XScreen`, `XViewModel`, `XUiState`, `XAction`, `XEffect`; `XDto`, `XEntity`, plain domain names; `XRepository` interface with `DefaultXRepository` or `OfflineFirstXRepository`.
- One public type per file, files under about 300 lines, no comments that restate code.
