---
name: concurrency-and-error-handling
description: >-
  Use this skill when implementing coroutines, background operations, flow streams,
  dispatchers, structured concurrency, or exception and error handling across KMP layers.
---

# Structured Concurrency & Error Handling in KMP

This guide outlines the rules for Kotlin Coroutines, asynchronous lifecycles, structured concurrency, and exception safety in `tripingly`.

---

## 1. Rules of Structured Concurrency

### Rule 1: Anchor Scopes to Proper lifecycles
- **ViewModels**: Always utilize `viewModelScope` for execution.
- **UI Layer / Composables**: Always rely on `rememberCoroutineScope()` for user-triggered micro-tasks or `LaunchedEffect` for state-driven lifecycle effects.
- **Anti-Pattern**: Never use `GlobalScope` or instantiate a raw, unbound `CoroutineScope()` without a managed lifecycle parent `Job`.

### Rule 2: Explicit Coroutine Dispatcher Injection
Do not hardcode `Dispatchers.IO` or `Dispatchers.Default` within repositories, data sources, or use cases. Always inject a `CoroutineDispatchers` abstraction. This preserves structured concurrency boundary constraints and enables deterministic synchronous verification during unit testing:

```kotlin
package com.falcon.tripingly.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

interface CoroutineDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DefaultCoroutineDispatchers : CoroutineDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
```

### Rule 3: Use `supervisorScope` to Isolate Failures
When launching independent concurrent sub-tasks where a fault in one child should not cancel neighboring sibling routines, wrap execution using `supervisorScope`:

```kotlin
suspend fun syncModuleData() = supervisorScope {
    val mapCacheJob = launch { syncMapMetadata() }
    val userProfileJob = launch { syncUserProfile() }
}
```

---

## 2. Robust Exception Management (Never Swallow Cancellation)

### Rule 1: Always Re-throw `CancellationException`
Kotlin coroutines rely on cooperative cancellation via `CancellationException`. Catching generic `Throwable` or broad `Exception` instances without explicitly re-throwing `CancellationException` breaks structured concurrency mechanics, leaves orphan routines processing indefinitely, and results in severe memory leaks:

```kotlin
// ❌ ANTI-PATTERN: DESTROYS STRUCTURED CONCURRENCY
try {
    locationDataSource.getLastKnownOrCurrentLocation()
} catch (e: Exception) {
    logger.e(e) { "Failed getting location" }
}

// ✅ MANDATORY RE-THROW PATTERN
import kotlin.coroutines.cancellation.CancellationException

try {
    return locationDataSource.getLastKnownOrCurrentLocation()
} catch (e: CancellationException) {
    throw e // Must explicitly re-throw to allow structured teardown!
} catch (e: Exception) {
    return AppResult.Error(DataError.Location.Unknown(e.message))
}
```

---

## 3. Pure Domain Modeling via `AppResult`

Never lean on standard exception throws to govern ordinary control logic flows or propagate low-level network/database crashes directly into domain or presentation code. Always utilize a strongly typed result envelope:

```kotlin
package com.falcon.tripingly.core.common.result

import com.falcon.tripingly.core.common.error.RootError

sealed interface AppResult<out D, out E : RootError> {
    data class Success<out D>(val data: D) : AppResult<D, Nothing>
    data class Error<out E : RootError>(val error: E) : AppResult<Nothing, E>
}

fun <D> D.asSuccess(): AppResult.Success<D> = AppResult.Success(this)
fun <E : RootError> E.asError(): AppResult.Error<E> = AppResult.Error(this)
```

---

## 4. Architectural Error Mapping Flow
Low-level frameworks or platform execution wrappers are transformed instantly at data layer boundaries, isolating upper layers from concrete package frameworks:

```
[ Concrete SDK / HttpClient ] ── Throws raw platform exceptions
              │
              ▼ (Data Boundary: Catch non-cancellation errors & map to DataError)
[ Repository Implementation ] ── Returns AppResult<DomainModel, DataError>
              │
              ▼ (Domain Layer: Pure business evaluation)
[ Domain Use Case / Interactor ]
              │
              ▼ (Presentation Layer: Maps Error type to localized UI message strings)
[ Presentation ViewModel ] ── Updates UI State with readable text strings
```

---

## Tripinly specifics

- **One error taxonomy.** `AppResult<T, E : RootError>` and `DataError` in `core:common` are the only result and error types across layers. Network errors carry the server's stable `error.code` mapped to a typed `DataError.Network` case (see the `api-integration` skill); nothing above the data layer sees exceptions or HTTP status codes.
- **Application scope.** Work that must outlive a screen (token refresh, cache sync, upload queue) runs in one injected `CoroutineScope(SupervisorJob() + Dispatchers.Default)` registered in `commonModule`, never `GlobalScope`.
- **Dispatchers belong to the data layer.** Repositories and data sources switch with injected `CoroutineDispatchers`; ViewModels use `viewModelScope` as is.
- **Retries and timeouts** live in repositories (Ktor timeouts, bounded retry with backoff for idempotent calls only).
