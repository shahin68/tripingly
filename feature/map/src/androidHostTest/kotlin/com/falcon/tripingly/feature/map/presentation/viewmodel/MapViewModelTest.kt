package com.falcon.tripingly.feature.map.presentation.viewmodel

import app.cash.turbine.test
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asError
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.presentation.permission.LocationPermission
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Confirm
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.LocationPrompt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var fakeLocationRepository: FakeLocationRepository
    private lateinit var backend: FakeTripBackend
    private lateinit var trip: TripDetails

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

    @BeforeTest
    fun setUp() = runTest {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        fakeLocationRepository = FakeLocationRepository()
        backend = FakeTripBackend()
        trip = (backend.createTrip(NewTrip("Test Trip", LocalDate(2024, 1, 1), LocalDate(2024, 1, 3))) as AppResult.Success).data
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(tripId: String = trip.trip.id) = MapViewModel(
        tripId = tripId,
        tripRepository = backend,
        markerRepository = backend,
        getCurrentLocationUseCase = GetCurrentLocationUseCase(fakeLocationRepository),
        dispatchers = TestCoroutineDispatchers(testDispatcher),
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    @Test
    fun `opening a trip shows its days from the server`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test Trip", state.tripName)
        assertEquals(listOf(1, 2, 3), state.days.map { it.number })
        assertEquals(LocalDate(2024, 1, 2), state.days[1].date)
        assertTrue(state.canEdit)
        assertFalse(state.isLoading)
        assertTrue(state.markers.isEmpty())
    }

    @Test
    fun `a trip that is gone says it isn't available`() = runTest(testDispatcher) {
        val viewModel = viewModel(tripId = "missing")
        testScheduler.advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.days.isEmpty())
        assertFalse(viewModel.uiState.value.canEdit)
        assertTrue(message.resource.key.contains("not_available"))
    }

    @Test
    fun `permission granted triggers location fetch and updates current location`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()

        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPermissionGranted)
        assertEquals(userCoords, state.currentLocation)
        assertEquals(userCoords, state.cameraTarget)
        assertFalse(state.isLoadingLocation)
    }

    @Test
    fun `navigating to location updates camera target and emits AnimateCamera event`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
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
    fun `map click adds numbered stops to the active day on the server`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnMapClick(Coordinates.London))
        testScheduler.advanceUntilIdle()

        val markers = viewModel.uiState.value.markers
        assertEquals(listOf("Stop #1", "Stop #2"), markers.map { it.title })
        assertEquals(listOf(1, 2), markers.map { it.orderNumber })
        assertEquals(Coordinates.London, markers[1].position)
        assertEquals(2, backend.refreshTrip(trip.trip.id).let { (it as AppResult.Success).data.days[0].markers.size })
    }

    @Test
    fun `stops belong to their day`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnDaySelected(1))
        viewModel.onAction(Action.OnMapClick(Coordinates.Rome))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.markers.size)
        viewModel.onAction(Action.OnDaySelected(0))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.markers.isEmpty())
    }

    @Test
    fun `removing a marker renumbers the remaining stops`() = runTest(testDispatcher) {
        val day = trip.days[0].id
        listOf(Coordinates.Paris, Coordinates.London, Coordinates.Rome).forEachIndexed { i, c ->
            backend.addMarker(trip.trip.id, day, NewMarker("Stop #${i + 1}", GeoPoint(c.latitude, c.longitude)))
        }
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnRemoveMarker(viewModel.uiState.value.markers[0].id))
        testScheduler.advanceUntilIdle()

        val remaining = viewModel.uiState.value.markers
        assertEquals(listOf("Stop #2", "Stop #3"), remaining.map { it.title })
        assertEquals(listOf(1, 2), remaining.map { it.orderNumber })
    }

    @Test
    fun `selecting a stop highlights it and centers the map on it`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val second = viewModel.uiState.value.markers[1]
        viewModel.onAction(Action.OnMarkerClick(second))
        testScheduler.advanceUntilIdle()

        assertEquals(second.id, viewModel.uiState.value.selectedMarker?.id)
        assertEquals(second.position, viewModel.uiState.value.cameraTarget)
    }

    @Test
    fun `clear all asks first, then empties the day`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.ClearAllMarkers)
        testScheduler.advanceUntilIdle()
        assertEquals(Confirm.ClearDay, viewModel.uiState.value.confirm)
        assertEquals(2, viewModel.uiState.value.markers.size)

        viewModel.onAction(Action.OnConfirm)
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.markers.size)
        assertNull(viewModel.uiState.value.confirm)
        assertNull(viewModel.uiState.value.selectedMarker)
    }

    @Test
    fun `adding a day selects it, deleting it goes back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.AddDay)
        testScheduler.advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.days.size)
        assertEquals(3, viewModel.uiState.value.activeDayIndex)

        viewModel.onAction(Action.DeleteDay)
        viewModel.onAction(Action.OnConfirm)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.days.size)
        assertEquals(2, viewModel.uiState.value.activeDayIndex)
    }

    @Test
    fun `a refused change shows the server's message`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        backend.nextError = DataError.Network.Api(403, "FORBIDDEN", "You can't change this trip.")

        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.markers.isEmpty())
        assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `a stop shows at once, and one that couldn't be sent comes back with Retry`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Stop #1"), viewModel.uiState.value.markers.map { it.title })
        assertFalse(viewModel.uiState.value.isSaving)

        backend.nextError = DataError.Network.NoInternet
        viewModel.onAction(Action.OnMapClick(Coordinates.London))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.markers.size)
        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertEquals(Res.string.map_change_not_saved, message.resource)
        assertTrue(viewModel.uiState.value.canRetry)

        viewModel.onAction(Action.RetryFailedChanges)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("Stop #1", "Stop #2"), viewModel.uiState.value.markers.map { it.title })
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `viewers can't add stops`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canEdit)
        assertTrue(viewModel.uiState.value.markers.isEmpty())
    }

    @Test
    fun `location failure sets appropriate error message without crashing`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = DataError.Location.PermissionDenied.asError()
        val viewModel = viewModel()

        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertEquals(Res.string.error_location_permission_required, message.resource)
    }
    @Test
    fun `the map asks for the location once when it first opens`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
            viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
            testScheduler.advanceUntilIdle()
            assertEquals(Event.RequestPermission, awaitItem())

            // Coming back to the screen doesn't ask again.
            viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `a denied answer says how to turn the location on`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnPermissionResult(LocationPermission.ShouldExplain))
        testScheduler.advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertEquals(Res.string.error_location_denied_manual, message.resource)
        assertFalse(viewModel.uiState.value.isPermissionGranted)
        assertFalse(viewModel.uiState.value.isWaitingForFirstLocation)
    }

    @Test
    fun `the location button asks again after a denial`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
            skipItems(1) // the request when the map opened

            viewModel.onAction(Action.CenterOnUserLocation)
            testScheduler.advanceUntilIdle()

            assertEquals(Event.RequestPermission, awaitItem())
            assertTrue(viewModel.uiState.value.isWaitingForFirstLocation)
        }
    }

    @Test
    fun `the location button explains first when the system says so`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.ShouldExplain))
        testScheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onAction(Action.CenterOnUserLocation)
            testScheduler.advanceUntilIdle()
            assertEquals(LocationPrompt.Explain, viewModel.uiState.value.locationPrompt)
            expectNoEvents()

            viewModel.onAction(Action.OnConfirmLocationPrompt)
            testScheduler.advanceUntilIdle()
            assertEquals(Event.RequestPermission, awaitItem())
            assertNull(viewModel.uiState.value.locationPrompt)
        }
    }

    @Test
    fun `the location button sends the user to Settings when it can't ask anymore`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onAction(Action.CenterOnUserLocation)
            testScheduler.advanceUntilIdle()
            assertEquals(LocationPrompt.OpenSettings, viewModel.uiState.value.locationPrompt)

            viewModel.onAction(Action.OnConfirmLocationPrompt)
            testScheduler.advanceUntilIdle()
            assertEquals(Event.OpenAppSettings, awaitItem())
        }
    }

    @Test
    fun `allowing it in Settings moves to the user on return`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050)
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.Blocked))
        viewModel.onAction(Action.CenterOnUserLocation)
        viewModel.onAction(Action.OnConfirmLocationPrompt)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPermissionGranted)
        assertEquals(userCoords, viewModel.uiState.value.cameraTarget)
    }

    @Test
    fun `dismissing the prompt stops waiting for the location`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.Blocked))
        viewModel.onAction(Action.CenterOnUserLocation)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnDismissLocationPrompt)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.locationPrompt)
        assertFalse(viewModel.uiState.value.isWaitingForFirstLocation)
    }
    @Test
    fun `the location button offers Settings when the system prompt can't show`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
        viewModel.onAction(Action.CenterOnUserLocation)
        testScheduler.advanceUntilIdle()

        // Android came straight back: the user said no for good.
        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        assertEquals(LocationPrompt.OpenSettings, viewModel.uiState.value.locationPrompt)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `a blocked answer when the map opens only shows the message`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.locationPrompt)
        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertEquals(Res.string.error_location_denied_manual, message.resource)
    }
}
