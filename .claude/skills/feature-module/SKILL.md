---
name: feature-module
description: Use when creating a new Tripinly feature module (feature:<name>) or core module (core:<name>) end to end — Gradle registration, convention plugin, packages, Koin module, Route/Screen/ViewModel, fakes and tests.
---

# New module

Read `docs/knowledge/07-architecture.md` first. A **feature** module is one product area (`auth`, `places`, `photos`, `social`, `profile`, `settings`); a **core** module is something two or more features share. If a "feature" needs another feature's code, that code belongs in a core module.

## 1. Register and build file

`settings.gradle.kts`:
```kotlin
include(":feature:places")
```

`feature/places/build.gradle.kts` (feature = library + compose + core:common/model/designsystem + Koin + lifecycle):
```kotlin
plugins {
    id("tripinly.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
        }
    }
}
```

Core modules use `tripinly.kmp.library` (add `tripinly.kmp.compose` only for UI). Keep build files under ~15 lines; anything every module needs goes into a convention plugin in `build-logic`, not copy-pasted. Namespace and `Res` package come from the module path automatically (`com.falcon.tripingly.feature.places`).

## 2. Packages

```
feature/places/src/
├── commonMain/kotlin/com/falcon/tripingly/feature/places/
│   ├── data/{remote,mapper,repository}/
│   ├── domain/{model,repository,usecase}/
│   ├── presentation/<screen>/            XRoute, XScreen, XViewModel, XUiState, XAction, XEffect, XPreviewData
│   └── di/PlacesModule.kt
├── commonMain/composeResources/values/strings.xml   (+ values-de, values-hu)
├── commonTest/kotlin/...                            ViewModel, mapper, repository tests + fakes
├── androidMain/ and iosMain/                        only if platform code is unavoidable
```

Everything is `internal` except: the Koin module, the `XRoute` composables `shared` needs, and repository interfaces other modules consume (those belong in a core module anyway).

## 3. Navigation

- Add the key to `core/navigation/.../Route.kt` (`@Serializable data class PlaceDetail(val placeId: String) : Route`) and register it in `NavConfig`'s polymorphic block.
- Wire it in `shared/App.kt`: `is PlaceDetail -> PlaceDetailRoute(key = key, onBack = ...)`. Features receive navigation as lambdas from `App.kt`; they never import another feature's route composable.

## 4. Koin

```kotlin
val placesModule = module {
    singleOf(::DefaultPlacesRepository) bind PlacesRepository::class
    viewModelOf(::PlacesViewModel)
}
```
Add `placesModule` to `initKoin()` in `shared/src/commonMain/.../di/InitKoin.kt` and the module to `shared/build.gradle.kts` dependencies.

## 5. Screen

Follow `mvi-presentation`: Route (Koin, `collectAsStateWithLifecycle`, `ObserveAsEvents`) → stateless Screen with previews for content, loading, empty and error.

## 6. Data

Follow `api-integration`: generated API models stay in `data`, a repository maps them to domain models and errors to `DataError`, a fake implements the same interface for previews, tests and `USE_FAKE_API`.

## 7. Tests (same PR)

- `XViewModelTest`: every action, every handled error code, effects.
- Mapper tests; repository tests with `MockEngine` and recorded staging JSON.
- `./gradlew allTests` green; iOS simulator tests run in CI.

## 8. Docs

Update `06-current-state.md` (structure, screens table) and, if a rule changed, `07-architecture.md` and `08-decision-log.md`.
