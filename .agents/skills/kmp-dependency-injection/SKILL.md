---
name: kmp-dependency-injection
description: >-
  Use this skill when configuring, creating, or refactoring Dependency Injection in Kotlin Multiplatform.
  Covers Koin / DI container architecture, module separation (core, network, database, feature modules),
  and platform-specific DI integration.
---

# Dependency Injection in Kotlin Multiplatform (Koin)

This guide defines the Dependency Injection architecture for `tripingly`.

---

## 1. Core DI Architecture & Strategy

Dependency Injection is used to achieve Inversion of Control (IoC) and enforce SOLID principles:
- High-level modules depend on abstractions (interfaces in `domain`).
- Low-level implementations (in `data`) are bound in DI modules.
- ViewModels and Use Cases receive their dependencies via constructor injection.

---

## 2. Module Organization

Organize DI modules strictly by layer and domain scope:

```
di/
├── CoreModule.kt            # Dispatchers, JSON serializers, Logger, App Settings
├── NetworkModule.kt         # Ktor HttpClient, Auth interceptors, Base URL
├── DatabaseModule.kt        # Room / SQLDelight database driver & DAOs
├── FeatureTripsModule.kt    # DataSources, Repositories, UseCases, ViewModels for Trips
└── InitKoin.kt              # Multiplatform initialization helper
```

---

## 3. Module Definitions Example

### Core & Network Modules
```kotlin
package com.falcon.tripingly.core.di

import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val coreModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
        }
    }
}

val networkModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(get())
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
}
```

### Feature Module (Data + Domain + Presentation)
```kotlin
package com.falcon.tripingly.feature.trips.di

import com.falcon.tripingly.data.datasource.local.TripLocalDataSource
import com.falcon.tripingly.data.datasource.local.TripRoomDataSource
import com.falcon.tripingly.data.datasource.remote.TripKtorDataSource
import com.falcon.tripingly.data.datasource.remote.TripRemoteDataSource
import com.falcon.tripingly.data.repository.TripRepositoryImpl
import com.falcon.tripingly.domain.repository.TripRepository
import com.falcon.tripingly.domain.usecase.GetTripsStreamUseCase
import com.falcon.tripingly.domain.usecase.SyncTripsUseCase
import com.falcon.tripingly.feature.trips.presentation.TripsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val featureTripsModule = module {
    // Data Sources
    singleOf(::TripRoomDataSource) bind TripLocalDataSource::class
    singleOf(::TripKtorDataSource) bind TripRemoteDataSource::class

    // Repositories (Bind interface to implementation)
    singleOf(::TripRepositoryImpl) bind TripRepository::class

    // Use Cases (factory = new instance each injection)
    factoryOf(::GetTripsStreamUseCase)
    factoryOf(::SyncTripsUseCase)

    // ViewModel (using androidx.lifecycle viewmodel DSL in KMP)
    viewModelOf(::TripsViewModel)
}
```

---

## 4. Multiplatform Initialization

### Common Entry Point (`commonMain`)
```kotlin
package com.falcon.tripingly.di

import com.falcon.tripingly.core.di.coreModule
import com.falcon.tripingly.core.di.networkModule
import com.falcon.tripingly.feature.trips.di.featureTripsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            coreModule,
            networkModule,
            featureTripsModule
        )
    }
}
```

### Android Integration (`androidMain`)
```kotlin
// In Android Application class:
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

### iOS Integration (`iosMain`)
```kotlin
// In iOS entry or Swift Main App:
fun initKoinIos() = initKoin()
```

---

## 5. DI Best Practices
1. **Never pass the DI container**: Avoid `get()` or service locator calls inside ViewModels or Use Cases. Always use constructor injection.
2. **Bind to interfaces**: Repositories must be bound to their domain interfaces (`bind TripRepository::class`).
3. **Keep `commonMain` pure**: Platform-specific dependencies (e.g. database drivers or Android Context) should be provided via platform modules and injected into common interfaces.
