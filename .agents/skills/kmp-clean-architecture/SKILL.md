---
name: kmp-clean-architecture
description: >-
  Use this skill when designing, organizing, or refactoring the Clean Architecture layers
  (Data, Domain, Presentation), implementing Single Source of Truth (SSOT), or applying
  SOLID principles in a Kotlin Multiplatform (KMP) project.
---

# KMP Clean Architecture, SOLID, & Feature-Based Modularization

This guide governs how the `tripingly` project structures its code across architectural boundaries, enforces SOLID principles, implements feature-based self-containment, and maintains code hygiene.

---

## 0. Development Workflow & Code Hygiene

**CRITICAL**: After completing all technical tasks and modifications, always perform a final pass to optimize code hygiene:
1. **Optimize Imports**: Organize all imports according to standard Kotlin conventions.
2. **Remove Unused Imports**: Scan all modified files and remove any unused import directives to keep the codebase clean.
3. **Avoid Useless Comments**: Do not add comments that state the obvious or merely label common components (e.g., `// 1. Google Map View`, `// 2. Error Banner`). Code should be self-documenting. Use comments only to explain "why" something non-obvious is being done, not "what" a standard view is.
4. **Verify Build**: Ensure that hygiene changes do not break compilation.

---

## 1. Gradle Modules, Feature Self-Containment & Layers

Boundaries are Gradle modules, not just packages (see `docs/knowledge/07-architecture.md`):

```
androidApp / iosApp  ->  shared (umbrella: App(), initKoin(), iOS framework)
                          ->  feature:<name>   (one per product area; never depends on another feature)
                                ->  core:*      (common, model, data, database, designsystem, navigation, network, ...)
```

- New modules apply a convention plugin from `build-logic`: `tripinly.kmp.library` (plain shared code), `+ tripinly.kmp.compose` (UI), or `tripinly.kmp.feature` (feature = library + compose + core modules + Koin + lifecycle). Use the `feature-module` skill.
- Something two features need moves **down** into a core module; a feature never imports another feature. Cross-feature navigation uses keys in `core:navigation`, wired in `shared/App.kt`.
- Classes are `internal` by default; a module's public surface is its contract (Koin module, routes, repository interfaces, public composables).

Inside a feature module, package by layer:

```
feature/map/src/commonMain/kotlin/com/falcon/tripingly/feature/map/
├── data/
│   ├── remote/                 # Ktor calls via core:network (generated API models stay here)
│   ├── local/                  # Feature-only cache access, if any
│   ├── mapper/                 # API model / entity  <->  domain model
│   └── repository/             # DefaultXRepository / OfflineFirstXRepository
├── domain/
│   ├── model/                  # Feature-only domain models (shared ones live in core:model)
│   ├── repository/             # Repository interfaces
│   └── usecase/                # Only when combining repositories or holding a rule
├── presentation/
│   ├── component/              # Feature-specific composables
│   └── <screen>/               # XRoute, XScreen, XViewModel, XUiState, XAction, XEffect
└── di/                         # The module's single Koin module
```

### Strict Layer Dependency Direction
- **Presentation Layer** depends on **Domain Layer** (renders domain models, calls repositories or use cases).
- **Data Layer** depends on **Domain Layer** (implements repository interfaces, maps API models and entities to domain models).
- **Domain Layer** depends on **NO OTHER LAYER**: no Compose, Room, Ktor or platform types. Domain models in `core:model` carry no Compose annotations; `compose-stability.conf` marks them stable.

---

## 2. Enforcing SOLID Principles

1. **Single Responsibility Principle (SRP)**: Each class must have exactly one reason to change. ViewModels manage UI state transitions; Use Cases execute a single business workflow; Repositories orchestrate data synchronization.
2. **Open/Closed Principle (OCP)**: Software artifacts must be open for extension but closed for modification. Implement behavior modifications by creating new Use Cases or extending Domain contracts rather than altering core repository flows.
3. **Liskov Substitution Principle (LSP)**: Derived types must be completely substitutable for their base types. Production code and mock/fake Data Sources or Repositories must act identically under the contract of their shared interface.
4. **Interface Segregation Principle (ISP)**: Clients should not be forced to depend on interfaces they do not use. Break large, bloated repository interfaces into small, focused sub-contracts if consumers require a specialized subsection.
5. **Dependency Inversion Principle (DIP)**: High-level policy components (Domain, Presentation) must never depend on low-level detailed components (SQL databases, HTTP client implementations). Both must rely upon pure abstractions.

---

## 3. Source of Truth: the Server, with Room as a Cache

The server is the source of truth. Room caches what the user needs offline (their own trips, **read-only offline**, decided 2026-10-03). Writes go to the API; the cache is updated from the response.

- Repositories expose `Flow`s that read from the cache and a `refresh()` (or refresh on subscription) that fetches from the API and writes into the cache.
- Writes call the API first. Optimistic updates (likes, comment posting, marker reorder, cover changes) update the cache immediately and roll back on error.
- While offline, reads come from the cache and the UI shows an offline banner; edits are blocked.
- API models and Room entities never leave the data layer.

### Repository Interface (Domain Layer)
```kotlin
package com.falcon.tripingly.feature.trips.domain.repository

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeMyTrips(): Flow<List<Trip>>
    suspend fun refreshMyTrips(): AppResult<Unit, DataError.Network>
    suspend fun renameTrip(id: String, title: String): AppResult<Trip, DataError.Network>
}
```

### Implementation (Data Layer)
```kotlin
internal class OfflineFirstTripRepository(
    private val api: TripsApi,
    private val tripDao: TripDao,
) : TripRepository {

    override fun observeMyTrips(): Flow<List<Trip>> =
        tripDao.observeOwnTrips().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshMyTrips(): AppResult<Unit, DataError.Network> =
        api.getMyTrips().map { page -> tripDao.replaceOwnTrips(page.items.map { it.toEntity() }) }

    override suspend fun renameTrip(id: String, title: String): AppResult<Trip, DataError.Network> =
        api.updateTrip(id, title = title).map { dto ->
            tripDao.upsert(dto.toEntity())
            dto.toDomain()
        }
}
```

---

## 4. Use Cases Only When They Earn Their Place

- Write a use case when it **combines several repositories** or **holds a business rule** (trip date math, "can this user edit", premium gating on the client side of a flow). Name it verb + noun with `operator fun invoke`.
- **No pass-through use cases** that only forward one repository call; the ViewModel calls the repository interface directly.
- Use cases are pure Kotlin and unit-tested in `commonTest`.

```kotlin
package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.model.Trip
import kotlinx.datetime.LocalDate

class ValidateTripDatesUseCase {
    operator fun invoke(start: LocalDate, end: LocalDate): TripDatesError? = when {
        end < start -> TripDatesError.EndBeforeStart
        start.daysUntil(end) > Trip.MAX_DAYS -> TripDatesError.TooLong
        else -> null
    }
}
```
