---
name: kmp-clean-architecture
description: >-
  Use this skill when designing, organizing, or refactoring the Clean Architecture layers
  (Data, Domain, Presentation), implementing Single Source of Truth (SSOT), or applying
  SOLID principles in a Kotlin Multiplatform (KMP) project.
---

# KMP Clean Architecture & Single Source of Truth (SSOT)

This guide governs how the `tripingly` project structures its code across architectural boundaries.

---

## 1. Architectural Layers & Boundaries

The application is strictly decoupled into three layers:

```
feature/
├── data/
│   ├── datasource/
│   │   ├── local/              # Room / SQLDelight / DataStore
│   │   └── remote/             # Ktor HTTP Client / WebSocket
│   ├── dto/                    # Data Transfer Objects / Database Entities
│   ├── mapper/                 # DTO <-> Domain entity mappers
│   └── repository/             # Repository Implementations (SSOT)
├── domain/
│   ├── model/                  # Pure Kotlin Business Models
│   ├── repository/             # Repository Interfaces
│   └── usecase/                # Single-responsibility Use Cases / Interactors
└── presentation/
    ├── component/              # Reusable Composables
    ├── mvi/                    # UiState, UiAction, UiEvent
    ├── screen/                 # Main Screen Composables
    └── viewmodel/              # ViewModel implementations
```

### Layer Dependency Direction
- **Presentation** depends on **Domain** (Calls Use Cases, consumes Domain models).
- **Data** depends on **Domain** (Implements Domain repository interfaces, maps DTOs to Domain models).
- **Domain** depends on **NO OTHER LAYER** (Pure Kotlin, zero platform or framework dependencies).

---

## 2. Single Source of Truth (SSOT) Pattern

To provide a consistent, offline-first experience, the **Data Layer** coordinates between a local repository/data source and a remote repository/data source.

### Architecture Contract
- The UI / Use Case observes data from the local store (via `Flow`).
- The repository fetches fresh data from remote, updates local storage, and emits or lets the local stream push the updated state.

### Repository Interface (Domain Layer)
```kotlin
package com.falcon.tripingly.domain.repository

import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun getTripsStream(): Flow<List<Trip>>
    suspend fun getTripById(id: String): AppResult<Trip, DataError>
    suspend fun syncTrips(): AppResult<Unit, DataError>
    suspend fun saveTrip(trip: Trip): AppResult<Unit, DataError>
}
```

### Local & Remote Data Sources (Data Layer)
```kotlin
package com.falcon.tripingly.data.datasource.local

import com.falcon.tripingly.data.dto.TripEntity
import kotlinx.coroutines.flow.Flow

interface TripLocalDataSource {
    fun observeAllTrips(): Flow<List<TripEntity>>
    suspend fun getTripById(id: String): TripEntity?
    suspend fun insertOrUpdateTrips(trips: List<TripEntity>)
    suspend fun insertTrip(trip: TripEntity)
    suspend fun deleteTrip(id: String)
}
```

```kotlin
package com.falcon.tripingly.data.datasource.remote

import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.error.DataError.Network
import com.falcon.tripingly.data.dto.TripDto

interface TripRemoteDataSource {
    suspend fun fetchTrips(): AppResult<List<TripDto>, Network>
    suspend fun fetchTripDetails(id: String): AppResult<TripDto, Network>
    suspend fun createTrip(trip: TripDto): AppResult<TripDto, Network>
}
```

### General Repository Implementation (Data Layer SSOT)
```kotlin
package com.falcon.tripingly.data.repository

import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.core.domain.result.asError
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.data.datasource.local.TripLocalDataSource
import com.falcon.tripingly.data.datasource.remote.TripRemoteDataSource
import com.falcon.tripingly.data.mapper.toDomain
import com.falcon.tripingly.data.mapper.toEntity
import com.falcon.tripingly.data.mapper.toDto
import com.falcon.tripingly.domain.model.Trip
import com.falcon.tripingly.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TripRepositoryImpl(
    private val localDataSource: TripLocalDataSource,
    private val remoteDataSource: TripRemoteDataSource
) : TripRepository {

    override fun getTripsStream(): Flow<List<Trip>> {
        return localDataSource.observeAllTrips().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTripById(id: String): AppResult<Trip, DataError> {
        val cached = localDataSource.getTripById(id)?.toDomain()
        if (cached != null) {
            return cached.asSuccess()
        }
        return when (val remoteResult = remoteDataSource.fetchTripDetails(id)) {
            is AppResult.Success -> {
                localDataSource.insertTrip(remoteResult.data.toEntity())
                remoteResult.data.toDomain().asSuccess()
            }
            is AppResult.Error -> remoteResult.asError()
        }
    }

    override suspend fun syncTrips(): AppResult<Unit, DataError> {
        return when (val remoteResult = remoteDataSource.fetchTrips()) {
            is AppResult.Success -> {
                val entities = remoteResult.data.map { it.toEntity() }
                localDataSource.insertOrUpdateTrips(entities)
                Unit.asSuccess()
            }
            is AppResult.Error -> remoteResult.asError()
        }
    }

    override suspend fun saveTrip(trip: Trip): AppResult<Unit, DataError> {
        localDataSource.insertTrip(trip.toEntity())
        return when (val remoteResult = remoteDataSource.createTrip(trip.toDto())) {
            is AppResult.Success -> Unit.asSuccess()
            is AppResult.Error -> remoteResult.asError()
        }
    }
}
```

---

## 3. Domain Layer: Use Cases / Interactors

- Each Use Case must have **one single responsibility**.
- Name Use Cases with verb + noun (e.g. `GetTripsStreamUseCase`, `SyncTripsUseCase`, `CreateTripUseCase`).
- Provide an `operator fun invoke` for idiomatic invocation:

```kotlin
package com.falcon.tripingly.domain.usecase

import com.falcon.tripingly.domain.model.Trip
import com.falcon.tripingly.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow

class GetTripsStreamUseCase(
    private val repository: TripRepository
) {
    operator fun invoke(): Flow<List<Trip>> = repository.getTripsStream()
}
```

---

## 4. SOLID Rules Checklist
1. **Single Responsibility**: Each class has one reason to change. Use cases only contain single domain operations.
2. **Open/Closed**: Features are extensible through abstractions (interfaces) without modifying core business logic.
3. **Liskov Substitution**: Repository and DataSource implementations can be substituted with test fakes without altering program correctness.
4. **Interface Segregation**: Keep interfaces focused. Split read and write operations if clients only need one.
5. **Dependency Inversion**: High-level modules (Domain, Presentation) do not depend on low-level modules (SQL, Ktor). Both depend on abstractions.
