package com.falcon.tripingly.feature.map.presentation.viewmodel

import app.cash.turbine.test
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asError
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.model.Trip
import com.falcon.tripingly.core.data.repository.TripRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetTripByIdUseCase
import com.falcon.tripingly.core.database.dao.MarkerDao
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.SaveMarkerUseCase
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var fakeLocationRepository: FakeLocationRepository
    private lateinit var fakeMarkerDao: FakeMarkerDao
    private lateinit var fakeTripRepository: FakeTripRepository
    private lateinit var testDispatchers: CoroutineDispatchers
    private lateinit var viewModel: MapViewModel

    private class TestCoroutineDispatchers(private val dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val unconfined: CoroutineDispatcher = dispatcher
    }

    private class FakeLocationRepository : LocationRepository {
        var locationResult: AppResult<Coordinates, DataError.Location> =
            Coordinates(48.8566, 2.3522).asSuccess()

        override fun getLocationStream(): Flow<AppResult<Coordinates, DataError.Location>> {
            return flowOf(locationResult)
        }
    }

    private class FakeMarkerDao : MarkerDao {
        private val markers = MutableStateFlow<List<MarkerEntity>>(emptyList())

        override suspend fun insertMarker(marker: MarkerEntity) {
            markers.update { current -> current.filterNot { it.id == marker.id } + marker }
        }

        override fun getMarkersForDay(tripId: String, dayIndex: Int): Flow<List<MarkerEntity>> {
            return markers.map { all ->
                all.filter { it.tripId == tripId && it.dayIndex == dayIndex }
                    .sortedBy { it.orderNumber }
            }
        }

        override suspend fun deleteMarkerById(markerId: String) {
            markers.update { current -> current.filterNot { it.id == markerId } }
        }

        override suspend fun deleteMarkersByTripId(tripId: String) {
            markers.update { current -> current.filterNot { it.tripId == tripId } }
        }

        override suspend fun deleteMarkersForDay(tripId: String, dayIndex: Int) {
            markers.update { current -> current.filterNot { it.tripId == tripId && it.dayIndex == dayIndex } }
        }

        override suspend fun deleteAll() {
            markers.value = emptyList()
        }
    }

    private class FakeTripRepository : TripRepository {
        var trip: Trip? = null

        override fun getAllTrips(): Flow<List<Trip>> = flowOf(listOfNotNull(trip))

        override suspend fun getTripById(id: String): Trip? = trip?.takeIf { it.id == id }

        override suspend fun saveTrip(trip: Trip) {
            this.trip = trip
        }

        override suspend fun updateTripName(id: String, name: String) {}

        override suspend fun updateTripDates(id: String, startDate: LocalDate, endDate: LocalDate) {}

        override suspend fun deleteTrip(id: String) {}
    }

    @BeforeTest
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testDispatchers = TestCoroutineDispatchers(testDispatcher)
        fakeLocationRepository = FakeLocationRepository()
        fakeMarkerDao = FakeMarkerDao()
        fakeTripRepository = FakeTripRepository().apply {
            trip = Trip(
                id = TRIP_ID,
                name = "Test Trip",
                startDate = LocalDate(2024, 1, 1),
                endDate = LocalDate(2024, 1, 5)
            )
        }
        viewModel = MapViewModel(
            tripId = TRIP_ID,
            getCurrentLocationUseCase = GetCurrentLocationUseCase(fakeLocationRepository),
            getMarkersForDayUseCase = GetMarkersForDayUseCase(fakeMarkerDao),
            saveMarkerUseCase = SaveMarkerUseCase(fakeMarkerDao),
            deleteMarkerUseCase = DeleteMarkerUseCase(fakeMarkerDao),
            deleteMarkersForDayUseCase = DeleteMarkersForDayUseCase(fakeMarkerDao),
            getTripByIdUseCase = GetTripByIdUseCase(fakeTripRepository),
            dispatchers = testDispatchers
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state defaults to Paris and empty markers`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals(Coordinates.Paris, state.cameraTarget)
        assertTrue(state.markers.isEmpty())
        assertFalse(state.isPermissionGranted)
        assertFalse(state.isLoadingLocation)
        assertNull(state.errorMessage)
    }

    @Test
    fun `permission granted triggers location fetch and updates current location`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()

        viewModel.onAction(Action.OnPermissionResult(isGranted = true))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPermissionGranted)
        assertEquals(userCoords, state.currentLocation)
        assertEquals(userCoords, state.cameraTarget)
        assertFalse(state.isLoadingLocation)
    }

    @Test
    fun `navigating to location updates camera target and emits AnimateCamera event`() = runTest(testDispatcher) {
        viewModel.events.test {
            viewModel.onAction(Action.NavigateToLocation(Coordinates.Tokyo, 14f))
            testScheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is Event.AnimateCamera)
            assertEquals(Coordinates.Tokyo, event.coordinates)
            assertEquals(14f, event.zoom)

            val state = viewModel.uiState.value
            assertEquals(Coordinates.Tokyo, state.cameraTarget)
            assertEquals(14f, state.zoomLevel)
        }
    }

    @Test
    fun `map click adds numbered markers sequentially for trip planning`() = runTest(testDispatcher) {
        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.markers.size)
        assertEquals(1, state.markers[0].orderNumber)
        assertEquals("Stop #1", state.markers[0].title)
        assertEquals(Coordinates.Paris, state.markers[0].position)

        viewModel.onAction(Action.OnMapClick(Coordinates.London))
        testScheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(2, state.markers.size)
        assertEquals(2, state.markers[1].orderNumber)
        assertEquals("Stop #2", state.markers[1].title)
        assertEquals(Coordinates.London, state.markers[1].position)
    }

    @Test
    fun `removing a marker deletes it from the trip stops`() = runTest(testDispatcher) {
        // Add 3 stops
        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnMapClick(Coordinates.London))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnMapClick(Coordinates.Rome))
        testScheduler.advanceUntilIdle()

        val markers = viewModel.uiState.value.markers
        assertEquals(3, markers.size)
        val stop1Id = markers[0].id

        // Remove Stop #1
        viewModel.onAction(Action.OnRemoveMarker(stop1Id))
        testScheduler.advanceUntilIdle()

        val remaining = viewModel.uiState.value.markers
        assertEquals(2, remaining.size)
        assertEquals("Stop #2", remaining[0].title)
        assertEquals(Coordinates.London, remaining[0].position)
        assertEquals("Stop #3", remaining[1].title)
        assertEquals(Coordinates.Rome, remaining[1].position)
    }

    @Test
    fun `clear all markers empties trip stops`() = runTest(testDispatcher) {
        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnMapClick(Coordinates.Rome))
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.markers.size)

        viewModel.onAction(Action.ClearAllMarkers)
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.markers.size)
        assertNull(viewModel.uiState.value.selectedMarker)
    }

    @Test
    fun `location failure sets appropriate error message without crashing`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = DataError.Location.PermissionDenied.asError()

        viewModel.onAction(Action.OnPermissionResult(isGranted = true))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("permission required"))
    }

    private companion object {
        const val TRIP_ID = "trip_1"
    }
}
