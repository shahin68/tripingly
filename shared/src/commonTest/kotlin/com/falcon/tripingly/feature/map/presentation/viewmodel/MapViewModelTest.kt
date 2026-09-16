package com.falcon.tripingly.feature.map.presentation.viewmodel

import app.cash.turbine.test
import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.core.domain.result.asError
import com.falcon.tripingly.core.domain.result.asSuccess
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.data.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiAction
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiEvent
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var fakeLocationRepository: FakeLocationRepository
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

        override suspend fun getCurrentLocation(): AppResult<Coordinates, DataError.Location> {
            return locationResult
        }
    }

    @BeforeTest
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testDispatchers = TestCoroutineDispatchers(testDispatcher)
        fakeLocationRepository = FakeLocationRepository()
        viewModel = MapViewModel(
            getCurrentLocationUseCase = GetCurrentLocationUseCase(fakeLocationRepository),
            dispatchers = testDispatchers
        )
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

        viewModel.onAction(MapUiAction.OnPermissionResult(isGranted = true))
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
            viewModel.onAction(MapUiAction.NavigateToLocation(Coordinates.Tokyo, 14f))
            testScheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is MapUiEvent.AnimateCamera)
            assertEquals(Coordinates.Tokyo, event.coordinates)
            assertEquals(14f, event.zoom)

            val state = viewModel.uiState.value
            assertEquals(Coordinates.Tokyo, state.cameraTarget)
            assertEquals(14f, state.zoomLevel)
        }
    }

    @Test
    fun `map click adds numbered markers sequentially for trip planning`() = runTest(testDispatcher) {
        viewModel.events.test {
            // Add Stop #1
            viewModel.onAction(MapUiAction.OnMapClick(Coordinates.Paris))
            val event1 = awaitItem()
            assertTrue(event1 is MapUiEvent.ShowSnackbar)
            assertTrue(event1.message.contains("Stop #1"))

            // Add Stop #2
            viewModel.onAction(MapUiAction.OnMapClick(Coordinates.London))
            val event2 = awaitItem()
            assertTrue(event2 is MapUiEvent.ShowSnackbar)
            assertTrue(event2.message.contains("Stop #2"))

            val state = viewModel.uiState.value
            assertEquals(2, state.markers.size)
            assertEquals(1, state.markers[0].orderNumber)
            assertEquals("Stop #1", state.markers[0].title)
            assertEquals(Coordinates.Paris, state.markers[0].position)

            assertEquals(2, state.markers[1].orderNumber)
            assertEquals("Stop #2", state.markers[1].title)
            assertEquals(Coordinates.London, state.markers[1].position)
        }
    }

    @Test
    fun `removing a marker re-numbers remaining markers to preserve trip sequence`() = runTest(testDispatcher) {
        // Add 3 stops
        viewModel.onAction(MapUiAction.OnMapClick(Coordinates.Paris))
        viewModel.onAction(MapUiAction.OnMapClick(Coordinates.London))
        viewModel.onAction(MapUiAction.OnMapClick(Coordinates.Rome))

        val markers = viewModel.uiState.value.markers
        assertEquals(3, markers.size)
        val stop1Id = markers[0].id

        // Remove Stop #1
        viewModel.onAction(MapUiAction.OnRemoveMarker(stop1Id))

        val remaining = viewModel.uiState.value.markers
        assertEquals(2, remaining.size)
        // Previous Stop #2 (London) is now Stop #1
        assertEquals("Stop #1", remaining[0].title)
        assertEquals(1, remaining[0].orderNumber)
        assertEquals(Coordinates.London, remaining[0].position)

        // Previous Stop #3 (Rome) is now Stop #2
        assertEquals("Stop #2", remaining[1].title)
        assertEquals(2, remaining[1].orderNumber)
        assertEquals(Coordinates.Rome, remaining[1].position)
    }

    @Test
    fun `clear all markers empties trip stops`() = runTest(testDispatcher) {
        viewModel.onAction(MapUiAction.OnMapClick(Coordinates.Paris))
        viewModel.onAction(MapUiAction.OnMapClick(Coordinates.Rome))
        assertEquals(2, viewModel.uiState.value.markers.size)

        viewModel.onAction(MapUiAction.ClearAllMarkers)
        assertEquals(0, viewModel.uiState.value.markers.size)
        assertNull(viewModel.uiState.value.selectedMarker)
    }

    @Test
    fun `location failure sets appropriate error message without crashing`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = DataError.Location.PermissionDenied.asError()

        viewModel.onAction(MapUiAction.OnPermissionResult(isGranted = true))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("permission required"))
    }
}
