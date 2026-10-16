package com.falcon.tripingly.feature.map.presentation.viewmodel

import app.cash.turbine.test
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asError
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.OsmRef
import com.falcon.tripingly.core.model.place.OsmType
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_stop_change
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.presentation.component.PinSizes
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Action
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Confirm
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.Event
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel.LocationPrompt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var fakeLocationRepository: FakeLocationRepository
    private lateinit var backend: FakeTripBackend
    private lateinit var trip: TripDetails
    private lateinit var places: CountingPlaceRepository
    private lateinit var photos: FakePhotoRepository
    private lateinit var social: RecordingSocialRepository

    /** The fake's sights (Eiffel Tower, Louvre …), counting the squares asked for. */
    private class CountingPlaceRepository(
        private val fake: FakePlaceRepository = FakePlaceRepository(),
    ) : PlaceRepository by fake {
        val requests = mutableListOf<List<PlaceSquare>>()
        /** A cluster drawn besides the sights. */
        var cluster: PlaceCluster? = null
        /** Images of the sights, by place ID. */
        val images = mutableMapOf<String, List<PlaceImage>>()
        val detailRequests = mutableListOf<String>()

        override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> {
            detailRequests += id
            return fake.place(id).map { it.copy(images = images[id].orEmpty()) }
        }

        override suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network> {
            requests += squares.toList()
            return fake.placesIn(squares)
        }

        override suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView> =
            fake.loadedPlacesIn(squares).mapValues { (square, places) ->
                val cluster = cluster?.takeIf { it.location in square.bounds } ?: return@mapValues places
                places.copy(clusters = listOf(cluster))
            }
    }

    /** Keeps what was reported. */
    private class RecordingSocialRepository(
        private val fake: FakeSocialRepository = FakeSocialRepository(),
    ) : SocialRepository by fake {
        val reports = mutableListOf<Pair<ReportTarget, String>>()

        override suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason, details: String?): AppResult<Unit, DataError.Network> {
            reports += target to targetId
            return fake.report(target, targetId, reason, details)
        }
    }

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
        places = CountingPlaceRepository()
        photos = FakePhotoRepository()
        social = RecordingSocialRepository()
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
        placeRepository = places,
        photoRepository = photos,
        socialRepository = social,
        appConfigRepository = FakeAppConfigRepository(),
        getCurrentLocationUseCase = GetCurrentLocationUseCase(fakeLocationRepository),
        dispatchers = TestCoroutineDispatchers(testDispatcher),
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    /** The place of the pin named [name] on the map. */
    private fun MapViewModel.pin(name: String): MapPlace = uiState.value.placePins.single { it.place.name == name }.place

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

        val message = assertIs<UiText.Joined>(viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.days.isEmpty())
        assertFalse(viewModel.uiState.value.canEdit)
        assertEquals(Res.string.map_error_load_trip, assertIs<UiText.Resource>(message.first).resource)
        assertTrue(assertIs<UiText.Resource>(message.second).resource.key.contains("not_available"))
    }

    @Test
    fun `allowing the location from the location button moves the camera to the user`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.NotAsked))
        viewModel.onAction(Action.CenterOnUserLocation)

        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPermissionGranted)
        assertEquals(userCoords, state.currentLocation)
        assertEquals(userCoords, state.cameraTarget)
        assertFalse(state.isLoadingLocation)
    }

    @Test
    fun `an allowed location shows the user but never moves the camera by itself`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val before = viewModel.uiState.value.cameraTarget

        viewModel.onAction(Action.OnPermissionChecked(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(userCoords, state.currentLocation)
        assertEquals(before, state.cameraTarget)
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

        // A tap sends no name: the server names the stop after what's there (the fake uses the coordinates).
        val markers = viewModel.uiState.value.markers
        assertEquals(listOf("48.8566, 2.3522", "51.5074, -0.1278"), markers.map { it.title })
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
    fun `a stop's card opens once the camera has centered it, and a touch elsewhere closes it`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        val stop = viewModel.uiState.value.markers.single()
        viewModel.onAction(Action.OnMarkerClick(stop))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isStopCardShown)

        viewModel.onAction(Action.OnCameraMove(stop.position, 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStopCardShown)

        viewModel.onAction(Action.OnScreenTouch)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedMarker)
        assertFalse(viewModel.uiState.value.isStopCardShown)
    }

    @Test
    fun `photos picked on a stop's card upload to it and open its photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.markers.single()
        viewModel.onAction(Action.OnMarkerClick(stop))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnAddStopPhotos(listOf(PickedImage { byteArrayOf(1) }, PickedImage { byteArrayOf(2) })))
        testScheduler.advanceUntilIdle()

        assertEquals(stop.id, viewModel.uiState.value.photosMarker?.id)
        assertEquals(2, photos.photos(stop.id).first()?.size)

        viewModel.onAction(Action.OnCloseStopPhotos)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.photosMarker)
    }

    @Test
    fun `a photo taken on a stop's card uploads to it, closes the camera and opens its photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.markers.single()
        viewModel.onAction(Action.OnMarkerClick(stop))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnOpenPhotoCamera)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPhotoCameraOpen)

        viewModel.onAction(Action.OnAddStopPhotos(listOf(PickedImage { byteArrayOf(1) })))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPhotoCameraOpen)
        assertEquals(stop.id, state.photosMarker?.id)
        assertEquals(1, photos.photos(stop.id).first()?.size)
    }

    @Test
    fun `a viewer can't add photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val withStop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data
        backend.seed(withStop.copy(trip = withStop.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.markers.single()
        viewModel.onAction(Action.OnMarkerClick(stop))

        viewModel.onAction(Action.OnOpenPhotoCamera)
        viewModel.onAction(Action.OnAddStopPhotos(listOf(PickedImage { byteArrayOf(1) })))
        testScheduler.advanceUntilIdle()

        assertNull(photos.photos(stop.id).first())
        assertNull(viewModel.uiState.value.photosMarker)
        assertFalse(viewModel.uiState.value.isPhotoCameraOpen)
    }

    @Test
    fun `opening a trip frames the open day's stops`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(48.86, 2.29)))
        backend.addMarker(trip.trip.id, trip.days[1].id, NewMarker("Stop #1", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(Coordinates(48.85, 2.35), Coordinates(48.86, 2.29)), viewModel.uiState.value.frame?.points)

        viewModel.onAction(Action.OnDaySelected(1))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(Coordinates(41.9, 12.49)), viewModel.uiState.value.frame?.points)
    }

    @Test
    fun `a trip whose first day is empty frames the whole trip`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[2].id, NewMarker("Stop #1", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(Coordinates(41.9, 12.49)), viewModel.uiState.value.frame?.points)
    }

    @Test
    fun `a trip without stops opens at its destination, not at the device location`() = runTest(testDispatcher) {
        val rome = (
            backend.createTrip(
                NewTrip("Rome", null, null, destination = Destination("Rome", GeoPoint(41.9028, 12.4964))),
            ) as AppResult.Success
        ).data
        val viewModel = viewModel(rome.trip.id)
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Coordinates(41.9028, 12.4964), state.cameraTarget)
        assertEquals(12f, state.zoomLevel)
        assertEquals(null, state.frame)
    }

    @Test
    fun `choosing the same day again frames it again`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val first = viewModel.uiState.value.frame

        viewModel.onAction(Action.OnDaySelected(0))
        testScheduler.advanceUntilIdle()

        assertEquals(first?.points, viewModel.uiState.value.frame?.points)
        assertTrue(viewModel.uiState.value.frame!!.id > first!!.id)
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
        val message = assertIs<UiText.Joined>(viewModel.uiState.value.message)
        assertEquals(Res.string.map_error_stop_change, assertIs<UiText.Resource>(message.first).resource)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `a stop shows at once, and one that couldn't be sent comes back with Retry`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapClick(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("48.8566, 2.3522"), viewModel.uiState.value.markers.map { it.title })
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

        assertEquals(listOf("48.8566, 2.3522", "51.5074, -0.1278"), viewModel.uiState.value.markers.map { it.title })
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
    fun `a viewer saves the trip and asks to join, once each`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnToggleSaveTrip)
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnRequestToJoin)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedByMe)
        assertEquals(JoinRequestStatus.PENDING, state.myJoinRequest)
        assertEquals(Res.string.map_join_requested, assertIs<UiText.Resource>(assertIs<UiText.Note>(state.message).text).resource)
        assertEquals(listOf(trip.trip.id), backend.observeMyTrips().first().map { it.id })
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
    fun `opening the map doesn't ask for the location`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
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

    @Test
    fun `the squares in view load when the camera rests, the ones around them once it stays`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val elsewhere = GeoBounds(47.9, 1.9, 48.1, 2.1)

        // The camera rests twice in a row: each view's squares load at once, only the last one's ring.
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.0, 2.0), 15f, elsewhere))
        testScheduler.advanceTimeBy(100)
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15.6f, PARIS_CENTER))
        testScheduler.advanceTimeBy(299)
        assertEquals(listOf(PlaceSquare.covering(elsewhere, 15), PlaceSquare.covering(PARIS_CENTER, 15)), places.requests)

        testScheduler.advanceUntilIdle()
        assertEquals(PlaceSquare.covering(PARIS_CENTER, 15, ring = 1), places.requests.last())
        assertEquals(3, places.requests.size)
    }

    @Test
    fun `a place's images open full screen, and a Commons image is reported as the place's image`() = runTest(testDispatcher) {
        places.images["fake-louvre"] = listOf(TRIP_PHOTO, COMMONS_IMAGE)
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(TRIP_PHOTO, COMMONS_IMAGE), viewModel.uiState.value.selectedPlaceDetails?.images)

        viewModel.onAction(Action.OnOpenPlaceImage(1))
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.placeImageIndex)
        viewModel.onAction(Action.OnReportPlaceImage(COMMONS_IMAGE))
        viewModel.onAction(Action.OnSendReport(ReportReason.Copyright, ""))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnReportPlaceImage(TRIP_PHOTO))
        viewModel.onAction(Action.OnSendReport(ReportReason.Spam, ""))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(ReportTarget.PlaceImage to "pi1", ReportTarget.Photo to "ph1"), social.reports)

        // Closing the place closes its images too.
        viewModel.onAction(Action.OnScreenTouch)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.placeImageIndex)
    }

    @Test
    fun `a stop without a cover shows its place's first image, asked for once`() = runTest(testDispatcher) {
        places.images["fake-louvre"] = listOf(COMMONS_IMAGE)
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Louvre", LOUVRE, placeId = "fake-louvre"))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val stop = viewModel.uiState.value.markers.single()
        viewModel.onAction(Action.OnMarkerClick(stop))
        testScheduler.advanceUntilIdle()
        assertEquals(COMMONS_IMAGE, viewModel.uiState.value.selectedStopPlaceImage)

        viewModel.onAction(Action.OnMarkerClick(stop))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("fake-louvre"), places.detailRequests)
    }

    @Test
    fun `a tap on a pin opens its place, and adding it makes a stop named after it`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
        testScheduler.advanceUntilIdle()
        val louvre = assertNotNull(viewModel.uiState.value.selectedPlace)
        assertEquals("Louvre", louvre.name)
        // The camera centers the place, at the same zoom.
        assertEquals(Coordinates(LOUVRE.lat, LOUVRE.lng), viewModel.uiState.value.cameraTarget)
        assertEquals(15f, viewModel.uiState.value.zoomLevel)
        assertEquals("Mo-Su 09:00-18:00", viewModel.uiState.value.selectedPlaceDetails?.openingHours)
        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())

        viewModel.onAction(Action.OnAddPlaceToDay)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedPlace)
        val stop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.single()
        assertEquals("Louvre", stop.name)
        assertEquals(louvre.id, stop.placeId)
        // It shows as the stop only: its pin leaves the map, and a tap there is a tap on the map.
        assertTrue(state.placePins.none { it.place.id == louvre.id })
        viewModel.onAction(Action.OnMapClick(Coordinates(LOUVRE.lat, LOUVRE.lng)))
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedPlace)
        assertEquals(2, (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size)
    }

    @Test
    fun `a place's card opens once the camera has centered it, and moving the map away or touching elsewhere closes the place`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            testScheduler.advanceUntilIdle()
            viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()

            viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.selectedPlace)
            assertFalse(viewModel.uiState.value.isPlaceCardShown)

            // The camera rests on the place: the card opens.
            viewModel.onAction(Action.OnCameraMove(Coordinates(LOUVRE.lat, LOUVRE.lng), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isPlaceCardShown)

            // Tapped again while centered: the touch closes the card, and the camera doesn't move, so the
            // card shows again at once.
            viewModel.onAction(Action.OnScreenTouch)
            viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isPlaceCardShown)

            // A tap on the bare map while the card is open only closes it: no stop is added.
            viewModel.onAction(Action.OnScreenTouch)
            viewModel.onAction(Action.OnMapClick(Coordinates(LOUVRE.lat, LOUVRE.lng + 0.0021)))
            testScheduler.advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedPlace)
            assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())

            // The user drags the map while the camera flies to another tap: the place closes.
            viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
            viewModel.onAction(Action.OnScreenTouch)
            viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.selectedPlace)
            viewModel.onAction(Action.OnCameraMove(Coordinates(48.85, 2.30), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedPlace)
            assertFalse(viewModel.uiState.value.isPlaceCardShown)
        }

    @Test
    fun `a tap on the bare map adds a stop`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapClick(Coordinates(LOUVRE.lat, LOUVRE.lng + 0.0021)))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedPlace)
        assertEquals(1, (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size)
    }

    @Test
    fun `a tap on a cluster zooms in on it`() = runTest(testDispatcher) {
        places.cluster = PlaceCluster(12, GeoPoint(48.80, 2.20))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.8, 2.2), 11f, GeoBounds(48.6, 2.0, 49.0, 2.4)))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnClusterClick(viewModel.uiState.value.placeClusters.single()))
        testScheduler.advanceUntilIdle()

        assertEquals(Coordinates(48.80, 2.20), viewModel.uiState.value.cameraTarget)
        assertEquals(13f, viewModel.uiState.value.zoomLevel)
        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())
    }

    @Test
    fun `pins are drawn for the places in view, bigger the better known`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        val pins = viewModel.uiState.value.placePins
        assertTrue(pins.isNotEmpty())
        assertTrue(pins.all { it.place.location in PARIS_CENTER })
        assertEquals(PinSizes[2], pins.single { it.place.name == "Louvre" }.size)
    }

    @Test
    fun `a viewer can't add a place`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnCameraMove(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnPlacePinClick(viewModel.pin("Louvre")))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnAddPlaceToDay)
        testScheduler.advanceUntilIdle()

        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())
    }

    @Test
    fun `search asks once typing pauses and moves the camera to the pick`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnOpenSearch)

        viewModel.onAction(Action.OnSearchQueryChanged("V"))
        testScheduler.advanceTimeBy(100)
        viewModel.onAction(Action.OnSearchQueryChanged("Vi"))
        testScheduler.advanceUntilIdle()

        val vienna = viewModel.uiState.value.searchResults.single()
        assertEquals("Vienna", vienna.name)
        viewModel.onAction(Action.OnSearchResultClick(vienna))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearchOpen)
        assertEquals("", state.searchQuery)
        assertEquals(Coordinates(48.2082, 16.3738), state.cameraTarget)
        // A city fills the screen.
        assertEquals(12f, state.zoomLevel)
    }

    @Test
    fun `an address picked in search opens its card, and adding it makes a stop named after it`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val address = PlaceSearchResult(
            name = "Kärntner Straße 38",
            address = "Wien, Österreich",
            location = GeoPoint(48.2052, 16.3696),
            type = SearchResultType.House,
            osm = OsmRef(OsmType.Node, "42"),
        )

        viewModel.onAction(Action.OnSearchResultClick(address))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Kärntner Straße 38", state.selectedPlace?.name)
        assertEquals("Wien, Österreich", state.selectedPlaceAddress)
        assertEquals(Coordinates(48.2052, 16.3696), state.cameraTarget)
        assertEquals(17f, state.zoomLevel)

        viewModel.onAction(Action.OnAddPlaceToDay)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedPlace)
        val stop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.single()
        assertEquals("Kärntner Straße 38", stop.name)
        assertEquals(GeoPoint(48.2052, 16.3696), stop.location)
    }

    @Test
    fun `a long press drops a pin with the name the user gives`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnMapLongClick(Coordinates(48.85, 2.35)))
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isNamingPin)
        viewModel.onAction(Action.OnConfirmPinName("Our hotel"))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isNamingPin)
        val stop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.single()
        assertEquals("Our hotel", stop.name)
        assertEquals(GeoPoint(48.85, 2.35), stop.location)
    }

    private companion object {
        val PARIS_CENTER = GeoBounds(48.84, 2.28, 48.87, 2.35)
        /** Where the fake's Louvre is. */
        val LOUVRE = GeoPoint(48.8606, 2.3376)
        val TRIP_PHOTO = PlaceImage("ph1", PlaceImageSource.Trip, "https://r2/ph1/thumb", "https://r2/ph1/display", 1600, 1200, credit = null)
        val COMMONS_IMAGE = PlaceImage(
            id = "pi1",
            source = PlaceImageSource.Commons,
            thumbUrl = "https://r2/pi1/thumb",
            displayUrl = "https://r2/pi1/display",
            width = 1280,
            height = 960,
            credit = ImageCredit("Jane Doe", null, "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0", "https://commons.wikimedia.org/wiki/File:Louvre.jpg"),
        )
    }
}
