# Tripingly - AI Pair Programmer & Senior Developer Guidelines

Welcome to **Tripingly**, a modern Kotlin Multiplatform (KMP / Compose Multiplatform) mobile application targeting Android and iOS.

As the Senior Multiplatform Kotlin Developer on this project, you work alongside the lead architect / user. Every proposal, architecture decision, and code modification is held to senior-level standards.

---

## Git workflow
- Every change goes on its own feature branch and is merged into `develop` through a pull request. Never push to `master`.
- One PR per build-order stage (see `CLAUDE.md`); CI must be green before review.
- `CLAUDE.md` and `docs/knowledge/` are the source of truth; this file summarizes the engineering principles.

---

## 🏛️ Core Architectural Principles

### 1. SOLID & Clean Architecture
The codebase strictly adheres to Clean Architecture separated into 3 decoupled layers:
- **Presentation Layer**: UI (Compose Multiplatform), ViewModels, UI State, UI Actions (MVI).
- **Domain Layer**: Pure Kotlin business models, Repository interfaces, Use Cases / Interactors. Zero dependencies on UI frameworks or external SDKs.
- **Data Layer**: Repository implementations, Local Data Source, Remote Data Source, network/database models, and mappers.

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                    │
│   Composable Screens ──(Action)──> ViewModel            │
│         ▲                               │               │
│         └──────────(UiState)────────────┘               │
└────────────────────────────┬────────────────────────────┘
                             │ invokes
                             ▼
┌─────────────────────────────────────────────────────────┐
│                      Domain Layer                       │
│                   Use Cases / Interactors               │
│                             │ calls                     │
│                             ▼                           │
│                Repository Interfaces (Domain)           │
└────────────────────────────▲────────────────────────────┘
                             │ implements
┌────────────────────────────┴────────────────────────────┐
│                       Data Layer                        │
│             Repository Implementation (SSOT)            │
│                 ┌───────────┴───────────┐               │
│                 ▼                       ▼               │
│        Local Data Source        Remote Data Source      │
│      (Room/DataStore/Cache)        (Ktor/HTTP)          │
└─────────────────────────────────────────────────────────┘
```

### 2. Source of truth
- The server is the source of truth. Room caches what the user needs offline (their own trips, read-only); writes go to the API, with optimistic updates that roll back on failure.
- A repository coordinates the remote data source (Ktor) and the local cache, and exposes `Flow`s that read from the cache.
- The Domain layer interacts only with repository interfaces. Use cases exist only when they combine repositories or hold a rule; no pass-through use cases.

### 3. MVVM + MVI Presentation Architecture
- **State Management**: Owned strictly by `ViewModel` via private `MutableStateFlow<UiState>` exposed as public read-only `StateFlow<UiState>`.
- **User Actions / High-level Intents**: Dispatched through sealed interface `UiAction`. Composable screens emit actions to `viewModel.onAction(action)`.
- **Single-time Events / Side Effects**: Handled through a dedicated `Channel<UiEvent>` consumed via `Flow<UiEvent>` (e.g., navigation, toast notifications), ensuring side effects are not re-triggered upon recomposition.
- **Composable Stability**: State objects must be `@Immutable` or `@Stable`. Use immutable collections (`kotlinx.collections.immutable`) or primitive/read-only lists where possible to avoid unnecessary recompositions.

### 4. Structured Concurrency & Coroutines
- Always respect structured concurrency. Use `viewModelScope` in ViewModels or properly scoped `CoroutineScope`.
- Never launch unconfined coroutines or use `GlobalScope`.
- Inject `CoroutineDispatcher` (e.g. `Dispatchers.Default`, `Dispatchers.IO`) for testability.
- Use `supervisorScope` when child failures should not cancel siblings.

### 5. Robust Error Handling (Never Swallow Cancellation)
- **CRITICAL**: Never catch `CancellationException` or generic `Throwable` without rethrowing `CancellationException`:
  ```kotlin
  try {
      // coroutine work
  } catch (e: CancellationException) {
      throw e // Always rethrow to preserve coroutine cancellation
  } catch (e: Exception) {
      // Handle or map to DomainError
  }
  ```
- Use typed error representations (e.g. `AppResult<T, DataError>`) at the data/domain boundary rather than leaking network or database exceptions into the presentation layer.

### 6. Dependency Injection
- Strong, explicit dependency injection across `commonMain`, `androidMain`, and `iosMain` using modern KMP DI (e.g. Koin).
- Program to interfaces (domain interfaces in `domain`, implementations in `data`).
- One Koin module per Gradle module (`commonModule`, `databaseModule`, `dataModule`, `tripsModule`, `mapModule`, …), with platform bindings in `expect/actual` modules. `shared/di/InitKoin.kt` aggregates them.

### 6b. Gradle modules
- `androidApp`/`iosApp` → `shared` (umbrella) → `feature:*` → `core:*`. Features never depend on each other; shared pieces move down into `core`.
- Modules apply the convention plugins in `build-logic` (`tripinly.kmp.library`, `tripinly.kmp.compose`, `tripinly.kmp.feature`). See `docs/knowledge/07-architecture.md`.

### 7. Modern Compose Multiplatform Styling & Theming
- Centralized Material 3 Design System (`ColorSchemes`, `Typography`, `Shapes`, `Spacing`).
- Support light and dark themes with accessible contrast ratios.
- Use multiplatform resources (`Res.string`, `Res.drawable`) safely.

### 8. Testing Standards
- Unit test all ViewModels, Use Cases, and Repositories.
- Use `Turbine` for testing `StateFlow` and `Flow`.
- Use test coroutine dispatchers (`StandardTestDispatcher`, `runTest`).
- Use mock / fake implementations for local and remote data sources.

---

## 📂 Specialized Skills Reference
Detailed guidelines and templates are in `.claude/skills/` (mirrored in `.agents/skills/` for the general engineering skills):
- `kmp-clean-architecture`: Clean Architecture, Layering, and SSOT guidelines.
- `mvi-presentation`: MVVM + MVI, Actions, StateFlow, Composable stability, Side Effects.
- `concurrency-and-error-handling`: Structured concurrency, CancellationException handling, Result patterns.
- `kmp-dependency-injection`: KMP DI structuring, multiplatform modules, constructor injection.
- `styling-and-theming`: Material 3 Compose Multiplatform theme, typography, spacing, component foundations.
- `android-edge-to-edge`: Edge-to-edge display, WindowInsets, status/navigation bars, and IME padding.
- `kmp-testing-framework`: Multiplatform unit and UI testing, Turbine, test dispatchers, fake repositories.
- `feature-module`: Scaffold a new feature or core module end to end.
- `ui-component`: Add a design-system component.
