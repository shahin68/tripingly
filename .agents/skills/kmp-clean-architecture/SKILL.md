---
name: kmp-clean-architecture
description: >-
  Use this skill when designing, organizing, or refactoring the Clean Architecture layers
  (Data, Domain, Presentation), implementing Single Source of Truth (SSOT), or applying
  SOLID principles in a Kotlin Multiplatform (KMP) project.
---

# KMP Clean Architecture, SOLID, & Feature-Based Modularization

This guide governs how the `tripingly` project structures its code across architectural boundaries, enforces SOLID principles, and implements feature-based self-containment.

---

## 1. Feature Self-Containment & Architecture Layers

Every feature module or package should completely **own its own features** and layers. Features should not spread implementation details leaking across unrelated modules. Cross-feature communication must happen strictly via public domain interfaces and models.

```
feature/map/
├── data/
│   ├── datasource/
│   │   ├── local/              # Local data storage (e.g. Room, DataStore)
│   │   └── remote/             # Remote API communication (e.g. Ktor Client)
│   ├── dto/                    # Data Transfer Objects & Database Entities
│   ├── mapper/                 # Two-way mappers between DTOs and Domain models
│   └── repository/             # Repository Implementations (SSOT coordination)
├── domain/
│   ├── model/                  # Pure Kotlin Business Models
│   ├── repository/             # Repository Interfaces (Abstractions)
│   └── usecase/                # Single-responsibility Use Cases / Interactors
└── presentation/
    ├── component/              # Feature-specific, reusable Composable UI
    ├── screen/                 # MVI State-driven Screen Composables
    └── viewmodel/              # Architecture-guided Presentation ViewModels
```

### Strict Layer Dependency Direction
- **Presentation Layer** depends on **Domain Layer** (Invokes Use Cases, renders Domain models).
- **Data Layer** depends on **Domain Layer** (Implements Domain repository interfaces, maps DTOs/Entities to Domain models).
- **Domain Layer** depends on **NO OTHER LAYER**. It must remain a pure Kotlin module, entirely decoupled from UI frameworks, database engines, or network clients.

---

## 2. Enforcing SOLID Principles

1. **Single Responsibility Principle (SRP)**: Each class must have exactly one reason to change. ViewModels manage UI state transitions; Use Cases execute a single business workflow; Repositories orchestrate data synchronization.
2. **Open/Closed Principle (OCP)**: Software artifacts must be open for extension but closed for modification. Implement behavior modifications by creating new Use Cases or extending Domain contracts rather than altering core repository flows.
3. **Liskov Substitution Principle (LSP)**: Derived types must be completely substitutable for their base types. Production code and mock/fake Data Sources or Repositories must act identically under the contract of their shared interface.
4. **Interface Segregation Principle (ISP)**: Clients should not be forced to depend on interfaces they do not use. Break large, bloated repository interfaces into small, focused sub-contracts if consumers require a specialized subsection.
5. **Dependency Inversion Principle (DIP)**: High-level policy components (Domain, Presentation) must never depend on low-level detailed components (SQL databases, HTTP client implementations). Both must rely upon pure abstractions.

---

## 3. Single Source of Truth (SSOT) Pattern

To guarantee a reliable, offline-first experience, the **Data Layer** orchestrates updates across data sources, ensuring local storage acts as the single source of truth for the presentation layer.

### Repository Interface (Domain Layer)
```kotlin
package com.falcon.tripingly.feature.map.domain.repository

import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getSavedLocationsStream(): Flow<List<Coordinates>>
    suspend fun getCurrentLocation(): AppResult<Coordinates, DataError.Location>
    suspend fun syncSavedLocations(): AppResult<Unit, DataError>
}
```

### General Repository Implementation (Data Layer SSOT Coordination)
```kotlin
package com.falcon.tripingly.feature.map.data.repository

import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class LocationRepositoryImpl(
    private val locationDataSource: LocationDataSource
) : LocationRepository {
    
    override fun getSavedLocationsStream(): Flow<List<Coordinates>> {
        // Observes local cache database stream as the sole source of truth
        return locationDataSource.observeCachedCoordinates()
    }

    override suspend fun getCurrentLocation(): AppResult<Coordinates, DataError.Location> {
        return locationDataSource.getLastKnownOrCurrentLocation()
    }

    override suspend fun syncSavedLocations(): AppResult<Unit, DataError> {
        // Fetches from remote data source, updates local source, completes
        return Unit.asSuccess()
    }
}
```

---

## 4. Single-Responsibility Use Cases / Interactors

- Every use case must encompass exactly **one single domain responsibility**.
- Name use cases using a precise verb + noun structure (e.g., `GetCurrentLocationUseCase`, `SaveDestinationUseCase`).
- Provide an `operator fun invoke` for declarative, clear invocation:

```kotlin
package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository

class GetCurrentLocationUseCase(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(): AppResult<Coordinates, DataError.Location> {
        return locationRepository.getCurrentLocation()
    }
}
```
