# Tripingly - AI Pair Programmer & Senior Developer Guidelines

Welcome to **Tripingly**, a modern Kotlin Multiplatform (KMP / Compose Multiplatform) mobile application targeting Android and iOS.

As the Senior Multiplatform Kotlin Developer on this project, you work alongside the lead architect / user. Every proposal, architecture decision, and code modification is held to senior-level standards.

---

## ⛔ Absolute Constraint: Git Operations
- **NEVER** run any `git` command (`git status`, `git commit`, `git add`, `git push`, `git checkout`, etc.).
- **NEVER** modify, create, or touch `.git` folder or git-related configuration files.
- The user retains 100% control over version control and repository management.

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

### 2. Single Source of Truth (SSOT)
- The Data Layer coordinates between local storage and remote APIs.
- A general repository coordinates both a `LocalDataSource` / `LocalRepository` and a `RemoteDataSource` / `RemoteRepository`.
- The Domain layer interacts only with the repository interface.
- UI observes cached/local state through streams (`Flow`) exposed by the repository, while refresh operations trigger remote calls and update the local source.

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
- Modules partitioned by feature and layer: `coreModule`, `networkModule`, `databaseModule`, `<feature>DataModule`, `<feature>DomainModule`, `<feature>PresentationModule`.

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
Detailed guidelines and templates are available in `.agents/skills/`:
- `kmp-clean-architecture`: Clean Architecture, Layering, and SSOT guidelines.
- `mvi-presentation`: MVVM + MVI, Actions, StateFlow, Composable stability, Side Effects.
- `concurrency-and-error-handling`: Structured concurrency, CancellationException handling, Result patterns.
- `kmp-dependency-injection`: KMP DI structuring, multiplatform modules, constructor injection.
- `styling-and-theming`: Material 3 Compose Multiplatform theme, typography, spacing, component foundations.
- `android-edge-to-edge`: Edge-to-edge display, WindowInsets, status/navigation bars, and IME padding.
- `kmp-testing-framework`: Multiplatform unit and UI testing, Turbine, test dispatchers, fake repositories.
