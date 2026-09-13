---
name: concurrency-and-error-handling
description: >-
  Use this skill when implementing coroutines, background operations, flow streams,
  dispatchers, structured concurrency, or exception and error handling across KMP layers.
---

# Structured Concurrency & Error Handling in KMP

This guide sets the rules for Kotlin coroutines, concurrency lifecycles, and exception handling in `tripingly`.

---

## 1. Structured Concurrency Rules

### Rule 1: Always Anchor to a Lifecycle Scope
- **ViewModels**: Always use `viewModelScope`.
- **Composables**: Use `rememberCoroutineScope()` for user-triggered gestures or `LaunchedEffect` for state-driven side effects.
- **Never use `GlobalScope`** or orphan `CoroutineScope()` without a bound `Job`.
- Repository & DataSource methods must be `suspend` functions or return `Flow<T>`, running on the caller's context or explicitly switching dispatchers.

### Rule 2: Coroutine Dispatcher Injection
Never hardcode `Dispatchers.IO` or `Dispatchers.Default` directly inside business logic or repositories. Always inject dispatchers via an interface or abstraction for deterministic unit testing:

```kotlin
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

### Rule 3: Use `supervisorScope` for Independent Operations
When launching parallel tasks where failure of one child should not cancel other sibling tasks, use `supervisorScope`:

```kotlin
suspend fun syncAllData() = supervisorScope {
    val tripsDeferred = async { syncTrips() }
    val userProfileDeferred = async { syncProfile() }

    // Individual await handling
}
```

---

## 2. Exception & Error Handling Rules

### Rule 1: NEVER Swallow `CancellationException`
In Kotlin coroutines, cooperative cancellation relies on `CancellationException`. Catching `Throwable` or `Exception` without re-throwing `CancellationException` breaks structured concurrency, prevents jobs from being cancelled, and leads to memory leaks:

```kotlin
// ❌ DANGEROUS: SWALLOWS CANCELLATION
try {
    apiClient.fetchData()
} catch (e: Exception) {
    logger.e(e) { "Error fetching data" }
}

// ✅ CORRECT: RE-THROWS CANCELLATION
import kotlin.coroutines.cancellation.CancellationException

try {
    apiClient.fetchData()
} catch (e: CancellationException) {
    throw e // Must rethrow to allow coroutine cancellation to propagate!
} catch (e: Exception) {
    // Safely handle domain/network errors
    return AppResult.Error(DataError.Network.fromException(e))
}
```

Alternatively, use a helper extension:
```kotlin
inline fun <T, R> T.runCatchingNonCancellation(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
```

---

## 3. Typed Error Modeling (`AppResult`)

Never throw exceptions for normal control flow or leak raw network/HTTP exceptions into Domain or UI layers. Use typed sealed results:

```kotlin
package com.falcon.tripingly.core.domain.result

sealed interface AppResult<out D, out E : RootError> {
    data class Success<out D>(val data: D) : AppResult<D, Nothing>
    data class Error<out E : RootError>(val error: E) : AppResult<Nothing, E>
}

inline fun <T, E : RootError> AppResult<T, E>.onSuccess(action: (T) -> Unit): AppResult<T, E> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T, E : RootError> AppResult<T, E>.onError(action: (E) -> Unit): AppResult<T, E> {
    if (this is AppResult.Error) action(error)
    return this
}

fun <D> D.asSuccess(): AppResult.Success<D> = AppResult.Success(this)
fun <E : RootError> E.asError(): AppResult.Error<E> = AppResult.Error(this)
```

### Typed Error Hierarchy
```kotlin
package com.falcon.tripingly.core.domain.error

sealed interface RootError

sealed interface DataError : RootError {
    sealed interface Network : DataError {
        data object RequestTimeout : Network
        data object Unauthorized : Network
        data object ServerError : Network
        data object NoInternet : Network
        data class Unknown(val message: String?) : Network
    }

    sealed interface Local : DataError {
        data object DiskFull : Local
        data object NotFound : Local
        data class Unknown(val message: String?) : Local
    }
}
```

---

## 4. Error Mapping at Layer Boundaries

```
[ Remote Data Source ] (Throws Ktor / Socket Exceptions)
         │
         ▼ (Catches non-cancellation exceptions & maps to DataError.Network)
[ Repository Implementation ] (Returns AppResult<DomainModel, DataError>)
         │
         ▼ (Evaluates business logic)
[ Domain Use Case ] (Returns AppResult<DomainModel, DomainError>)
         │
         ▼ (Translates DomainError to localized UI Text / Snackbar message)
[ Presentation ViewModel ] (Exposes UiState.errorMessage to Composable)
```
