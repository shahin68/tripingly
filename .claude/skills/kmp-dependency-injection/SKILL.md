---
name: kmp-dependency-injection
description: >-
  Use this skill when configuring, creating, or refactoring Dependency Injection in Kotlin Multiplatform.
  Covers Koin / DI container architecture, module separation (core, network, database, feature modules),
  and platform-specific DI integration.
---

# Dependency Injection in Kotlin Multiplatform (Koin)

This guide defines the standards for Dependency Injection (DI) using Koin within the `tripingly` mobile codebase.

---

## 1. Modular DI & Architectural Separation

Dependency Injection is deployed to enforce SOLID principles and cleanly implement feature self-containment:
- High-level modules interact purely with abstractions (Domain Interfaces).
- Low-level modules (Data implementations) are encapsulated and bound within their respective feature DI files.
- Feature components are self-contained. Every feature completely **owns its own feature configuration**, layers, and dependency registration.
- Platform-specific instances (e.g., specific native data source configurations) are defined using `expect`/`actual` module mechanisms.

---

## 2. One Koin Module per Gradle Module

Each Gradle module owns exactly one public Koin module in its `di` package, named after the module. Platform bindings sit in an `internal expect val` that the common module `includes(...)`:

```
core/common/src/commonMain/.../core/common/di/CommonModule.kt          val commonModule (+ internal expect val commonPlatformModule)
core/common/src/androidMain/.../core/common/di/CommonModule.android.kt internal actual val commonPlatformModule
core/common/src/iosMain/.../core/common/di/CommonModule.ios.kt         internal actual val commonPlatformModule
core/database/.../di/DatabaseModule.kt                                 val databaseModule (Room builder per platform)
core/data/.../di/DataModule.kt                                         val dataModule
feature/trips/.../di/TripsModule.kt                                    val tripsModule
feature/map/.../di/MapModule.kt                                        val mapModule (+ platform location source)
shared/src/commonMain/.../di/InitKoin.kt                               initKoin() lists every module once
```

---

## 3. Module Definitions

Constructor injection DSL (`singleOf`, `factoryOf`, `viewModelOf`) with explicit interface binding:

```kotlin
package com.falcon.tripingly.feature.map.di

val mapModule = module {
    includes(mapPlatformModule)
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    viewModelOf(::MapViewModel)
}

internal expect val mapPlatformModule: Module
```

```kotlin
// androidMain
internal actual val mapPlatformModule: Module = module {
    single { AndroidLocationDataSource(get()) } bind LocationDataSource::class   // get() resolves the Android Context
}

// iosMain
internal actual val mapPlatformModule: Module = module {
    singleOf(::IosLocationDataSource) bind LocationDataSource::class
}
```

- Implementations stay `internal` to their module; only the Koin module value and the interfaces are public.
- A ViewModel that needs navigation arguments takes its typed key: `viewModel { params -> TripDetailViewModel(key = params.get(), ...) }` and `koinViewModel { parametersOf(key) }` in the Route.

---

## 4. Bootstrapping

```kotlin
package com.falcon.tripingly.di

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(commonModule, databaseModule, dataModule, tripsModule, mapModule)
    }
}
```

- Android calls `initKoin { androidContext(this@TripinglyApplication) }` in `TripinglyApplication`.
- iOS calls `initKoin()` once from `MainViewController()` in `shared/src/iosMain`.
- Adding a module means adding its Koin module to this list in the same PR.

### Tests

- ViewModel and repository tests construct classes directly with fakes; they don't start Koin.
- One `checkModules`-style test in `shared` (once `core:testing` exists) verifies the whole graph resolves, with platform pieces replaced by fakes.

### Android Native Application Binding
```kotlin
package com.falcon.tripingly

import android.app.Application
import com.falcon.tripingly.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TripinglyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TripinglyApplication)
            androidLogger()
        }
    }
}
```

---

## 5. Idiomatic DI Practices
1. **No Service Locating**: Never invoke `get()` or pass the Koin container instance directly inside ViewModels, Use Cases, or Screen components. Stick purely to clean constructor injection.
2. **Bind to Domain Abstractions**: Always map data layer implementations to domain layer interfaces (e.g., `bind LocationRepository::class`).
3. **Pure Presentation Architecture**: In Compose Multiplatform screens, leverage the idiomatic `koinViewModel<T>()` utility function to resolve ViewModels directly inside your composable hierarchy.
