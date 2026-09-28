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

## 2. Dependency Injection Architecture Organization

Organize DI declarations strictly according to their feature, layer, and semantic architectural boundaries:

```
di/
├── KoinModules.kt           # Expect declaration for platformModule() & common modules
├── KoinModules.android.kt   # Actual declaration providing Android components (e.g., Context)
├── KoinModules.ios.kt       # Actual declaration providing iOS component definitions
└── InitKoin.kt              # Common multiplatform initialization entry helper
```

---

## 3. Module Definitions & Conversions

### Common Feature Module Definitions
Use constructor injection DSL (`singleOf`, `factoryOf`, `viewModelOf`) along with explicit interface binding (`bind`) to map your implementations to core domain contracts cleanly:

```kotlin
package com.falcon.tripingly.di

import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.feature.map.data.repository.LocationRepository
import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect fun platformModule(): Module

val coreModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
}

val mapModule = module {
    // Repositories bound strictly to their domain contracts
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    
    // Factories for Use Cases
    factoryOf(::GetCurrentLocationUseCase)
    
    // ViewModels using standard Lifecycle ViewModel DSL in KMP
    viewModelOf(::MapViewModel)
}
```

### Platform Module Initializations

#### Android Setup (`androidMain`)
```kotlin
package com.falcon.tripingly.di

import com.falcon.tripingly.feature.map.data.datasource.AndroidLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    // Injects Android Application Context automatically via get()
    single { AndroidLocationDataSource(get()) } bind LocationDataSource::class
}
```

#### iOS Setup (`iosMain`)
```kotlin
package com.falcon.tripingly.di

import com.falcon.tripingly.feature.map.data.datasource.IosLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    singleOf(::IosLocationDataSource) bind LocationDataSource::class
}
```

---

## 4. Multiplatform Bootstrapping Workflow

### Common Init Helper (`commonMain`)
```kotlin
package com.falcon.tripingly.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            coreModule,
            platformModule(),
            mapModule
        )
    }
}
```

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
