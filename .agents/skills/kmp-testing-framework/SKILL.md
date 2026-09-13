---
name: kmp-testing-framework
description: >-
  Use this skill when writing, configuring, or running tests in Kotlin Multiplatform.
  Covers unit testing for ViewModels, Use Cases, Repositories, Coroutines testing with
  StandardTestDispatcher, Flow testing with Turbine, and test fakes.
---

# Kotlin Multiplatform Testing Framework & Guidelines

This guide sets the testing standards and practices for `tripingly`.

---

## 1. Testing Stack & Tools

- **Core Assertions**: `kotlin.test` (`assertEquals`, `assertTrue`, `assertIs`, etc.)
- **Flow & State Testing**: `app.cash.turbine:turbine`
- **Coroutines Testing**: `org.jetbrains.kotlinx:kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`)
- **Test Doubles**: High-fidelity Fakes (preferred over brittle reflection-based mocks for KMP)

---

## 2. Test Structure & Naming Conventions

Follow the **Given - When - Then** / Arrange - Act - Assert pattern:
- Test classes: `<SubjectUnderTest>Test` (e.g. `TripsViewModelTest`, `TripRepositoryImplTest`).
- Test method names: Describe the condition and expected outcome (e.g. `given_successful_sync_when_refresh_action_then_ui_state_updates_to_success`).

---

## 3. Testing ViewModels with Turbine & Test Coroutines

```kotlin
package com.falcon.tripingly.feature.trips.presentation

import app.cash.turbine.test
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.domain.model.Trip
import com.falcon.tripingly.domain.usecase.GetTripsStreamUseCase
import com.falcon.tripingly.domain.usecase.SyncTripsUseCase
import com.falcon.tripingly.test.fake.FakeTripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeTripRepository
    private lateinit var viewModel: TripsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTripRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh action updates state with fetched trips`() = runTest(testDispatcher) {
        val testTrips = listOf(
            Trip(id = "1", title = "Paris Vacation", destination = "Paris"),
            Trip(id = "2", title = "Tokyo Adventure", destination = "Tokyo")
        )
        fakeRepository.setTrips(testTrips)

        viewModel = TripsViewModel(
            getTripsStreamUseCase = GetTripsStreamUseCase(fakeRepository),
            syncTripsUseCase = SyncTripsUseCase(fakeRepository)
        )

        viewModel.uiState.test {
            // Initial emission
            val initialState = awaitItem()
            
            // Advance coroutines
            testScheduler.advanceUntilIdle()

            val loadedState = awaitItem()
            assertFalse(loadedState.isLoading)
            assertEquals(2, loadedState.trips.size)
            assertEquals("Paris Vacation", loadedState.trips.first().title)
        }
    }

    @Test
    fun `trip clicked action emits NavigateToDetails event`() = runTest(testDispatcher) {
        viewModel = TripsViewModel(
            getTripsStreamUseCase = GetTripsStreamUseCase(fakeRepository),
            syncTripsUseCase = SyncTripsUseCase(fakeRepository)
        )

        viewModel.events.test {
            viewModel.onAction(TripsAction.OnTripClicked("trip_123"))

            val event = awaitItem()
            assertEquals(TripsEvent.NavigateToDetails("trip_123"), event)
        }
    }
}
```

---

## 4. High-Fidelity Test Fakes Pattern

Prefer in-memory test fakes over mocks for KMP repositories and data sources. Fakes are reliable, fast, and multiplatform-native:

```kotlin
package com.falcon.tripingly.test.fake

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.domain.model.Trip
import com.falcon.tripingly.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeTripRepository : TripRepository {
    private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())
    var shouldFailSync: Boolean = false

    fun setTrips(trips: List<Trip>) {
        tripsFlow.value = trips
    }

    override fun getTripsStream(): Flow<List<Trip>> = tripsFlow.asStateFlow()

    override suspend fun getTripById(id: String): AppResult<Trip, DataError> {
        val trip = tripsFlow.value.find { it.id == id }
        return if (trip != null) {
            trip.asSuccess()
        } else {
            AppResult.Error(DataError.Local.NotFound)
        }
    }

    override suspend fun syncTrips(): AppResult<Unit, DataError> {
        return if (shouldFailSync) {
            AppResult.Error(DataError.Network.NoInternet)
        } else {
            Unit.asSuccess()
        }
    }

    override suspend fun saveTrip(trip: Trip): AppResult<Unit, DataError> {
        tripsFlow.value = tripsFlow.value + trip
        return Unit.asSuccess()
    }
}
```

---

## 5. Repository Testing (Verifying SSOT)

Test that the repository coordinates between local and remote sources correctly:

```kotlin
@Test
fun `getTripById returns cached entity when present in local data source`() = runTest {
    val localDataSource = FakeTripLocalDataSource()
    val remoteDataSource = FakeTripRemoteDataSource()
    val repository = TripRepositoryImpl(localDataSource, remoteDataSource)

    localDataSource.insertTrip(TripEntity(id = "1", title = "Cached Trip"))

    val result = repository.getTripById("1")
    assertIs<AppResult.Success<Trip>>(result)
    assertEquals("Cached Trip", result.data.title)
    assertEquals(0, remoteDataSource.fetchCallCount) // Verifies remote was NOT called
}
```

---

## 6. Testing Checklist
1. **Never use `Thread.sleep`**: Always use virtual time with `runTest` and `testScheduler.advanceTimeBy()` or `advanceUntilIdle()`.
2. **Reset Dispatchers**: Always reset `Dispatchers.resetMain()` in `@AfterTest`.
3. **Verify cancellation resilience**: Write tests verifying that cancelling a parent job does not cause unhandled exceptions.
4. **Test both success and failure branches**: Ensure all `AppResult.Error` paths are covered.
