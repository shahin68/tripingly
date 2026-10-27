package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.test
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asError
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.FakeRouteRepository
import com.falcon.tripingly.core.data.routing.RouteErrorCodes
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
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
import com.falcon.tripingly.core.model.route.AlongTheWayPlace
import com.falcon.tripingly.core.model.route.BestOrder
import com.falcon.tripingly.core.model.route.ROUTES_ENTITLEMENT
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMapView
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_already
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_error_load_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_stop_change
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.generated.resources.map_my_content_removed
import com.falcon.tripingly.feature.map.generated.resources.map_trip_owner
import com.falcon.tripingly.feature.map.presentation.FakeTripMapViewRepository
import com.falcon.tripingly.feature.map.presentation.component.CameraCommand
import com.falcon.tripingly.feature.map.presentation.component.PinSizes
import com.falcon.tripingly.feature.map.presentation.component.routeLine
import com.falcon.tripingly.feature.map.presentation.component.stopPathShape
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
    private lateinit var routes: RecordingRouteRepository
    /** Signed in as the trips backend's `me`. */
    private lateinit var session: FakeAccountBackend
    private lateinit var mapViews: FakeTripMapViewRepository

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

    /** Straight lines between the stops, counting what was asked for. */
    private class RecordingRouteRepository(
        private val fake: FakeRouteRepository = FakeRouteRepository(),
    ) : RouteRepository by fake {
        val dayRoutes = mutableListOf<Pair<List<String>, TravelMode>>()
        val pointRoutes = mutableListOf<Pair<GeoPoint, GeoPoint>>()
        /** Answers every route with this error when set. */
        var error: DataError.Network? = null
        /** The best order to propose; the fake's (nearest stop next) when null. */
        var bestOrder: List<String>? = null
        /** The places along every day route. */
        var alongTheWay: List<AlongTheWayPlace> = emptyList()

        override suspend fun dayRoute(tripId: String, day: TripDay, mode: TravelMode): AppResult<RoutePlan, DataError.Network> {
            dayRoutes += day.markers.map { it.id } to mode
            return error?.asError() ?: fake.dayRoute(tripId, day, mode).map { it.copy(alongTheWay = alongTheWay) }
        }

        override suspend fun route(from: GeoPoint, to: GeoPoint, mode: TravelMode, tripId: String?): AppResult<RoutePlan, DataError.Network> {
            pointRoutes += from to to
            return error?.asError() ?: fake.route(from, to, mode, tripId)
        }

        override suspend fun bestOrder(tripId: String, day: TripDay, mode: TravelMode): AppResult<BestOrder, DataError.Network> =
            bestOrder?.let { BestOrder(it, byTravelTime = false, savedMinutes = null).asSuccess() } ?: fake.bestOrder(tripId, day, mode)
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
        routes = RecordingRouteRepository()
        mapViews = FakeTripMapViewRepository()
        // With Tripinly Pro, for the routes.
        session = signedIn(setOf(ROUTES_ENTITLEMENT))
        trip = (backend.createTrip(NewTrip("Test Trip", LocalDate(2024, 1, 1), LocalDate(2024, 1, 3))) as AppResult.Success).data
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Onboarded, so the session is SignedIn with an account id. */
    private suspend fun signedIn(entitlements: Set<String>) =
        FakeAccountBackend(today = { LocalDate(2026, 10, 16) }, entitlements = entitlements).also {
            it.signInForDevelopment("me", "Me")
            it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
            it.accept((it.legalDocuments("en") as AppResult.Success).data)
        }

    private fun TestScope.viewModel(tripId: String = trip.trip.id) = MapViewModel(
        tripId = tripId,
        tripRepository = backend,
        markerRepository = backend,
        placeRepository = places,
        routeRepository = routes,
        photoRepository = photos,
        socialRepository = social,
        appConfigRepository = FakeAppConfigRepository(),
        mapViews = mapViews,
        sessionRepository = session,
        getCurrentLocationUseCase = GetCurrentLocationUseCase(fakeLocationRepository),
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    /** The place of the pin named [name] on the map. */
    private fun MapViewModel.pin(name: String): MapPlace = uiState.value.layers.placePins.single { it.place.name == name }.place

    private val MapUiState.locationPrompt get() = (dialog as? MapDialog.Location)?.prompt
    private val MapUiState.othersContentMessage get() = (dialog as? MapDialog.DeleteOthersContent)?.message
    private val MapUiState.stopPhotos get() = (sheet as? MapSheet.StopPhotos)?.stop
    private val MapUiState.bestOrder get() = (dialog as? MapDialog.BestOrder)?.preview
    private val MapUiState.stopPositions get() = layers.stops.map { it.position }
    private val MapUiState.stopsCard get() = plan as? StopsCardState
    private val MapUiState.pointRouteCard get() = plan as? PointRouteCardState

    /** The fake routes go straight from stop to stop. */
    private fun MapUiState.assertRoute(stops: List<Coordinates>, mode: TravelMode = TravelMode.Walking) =
        assertEquals(routeLine(stops, stops, mode), layers.line)

    /** The map comes to rest where the camera flew, so the open place's or stop's card opens. */
    private fun TestScope.restOnTarget(viewModel: MapViewModel) {
        testScheduler.advanceUntilIdle()
        val camera = viewModel.uiState.value.camera
        viewModel.onAction(MapAction.MapView.CameraRest(camera.target, camera.zoom, PARIS_CENTER))
    }

    private fun MapUiState.assertStopPath() = assertEquals(stopPathShape(stopPositions), layers.line)

    @Test
    fun `opening a trip shows its days from the server`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test Trip", state.header.tripName)
        assertEquals(listOf(1, 2, 3), state.dayTabs.days.map { it.number })
        assertEquals(DateUtils.formatAbbreviated(LocalDate(2024, 1, 2)), state.dayTabs.days[1].date)
        assertNotNull(state.header.editorMenu)
        assertFalse(state.header.isBusy)
        assertTrue(state.layers.stops.isEmpty())
    }

    @Test
    fun `a trip that is gone shows the error screen saying it isn't available`() = runTest(testDispatcher) {
        val viewModel = viewModel(tripId = "missing")
        testScheduler.advanceUntilIdle()

        val error = assertIs<UiText.Joined>(viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.dayTabs.days.isEmpty())
        assertNull(viewModel.uiState.value.header.editorMenu)
        assertEquals(Res.string.map_error_load_trip, assertIs<UiText.Resource>(error.first).resource)
        assertTrue(assertIs<UiText.Resource>(error.second).resource.key.contains("not_available"))
    }

    @Test
    fun `retry on the error screen loads the trip again`() = runTest(testDispatcher) {
        val viewModel = viewModel(tripId = "missing")
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Header.Retry)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.header.isBusy)
    }

    @Test
    fun `allowing the location from the location button moves the camera to the user`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.NotAsked))
        viewModel.onAction(MapAction.Location.CenterOnUser)

        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.layers.isMyLocationEnabled)
        assertEquals(userCoords, state.camera.target)
        assertFalse(state.location.isLoading)
    }

    @Test
    fun `an allowed location shows the user but never moves the camera by itself`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val before = viewModel.uiState.value.camera.target

        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Granted))
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.layers.isMyLocationEnabled)
            assertEquals(before, viewModel.uiState.value.camera.target)
            expectNoEvents()
        }
    }

    @Test
    fun `the location button flies to the user once the location is known`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050) // Berlin
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.Location.CenterOnUser)
            testScheduler.advanceUntilIdle()
            assertEquals(CameraCommand.MoveTo(userCoords, 15f), expectMostRecentItem())
        }
    }

    @Test
    fun `a tap on a cluster flies the camera in on it`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.MapView.ClusterClick(PlaceCluster(3, GeoPoint(35.6762, 139.6503))))
            testScheduler.advanceUntilIdle()

            val move = assertIs<CameraCommand.MoveTo>(expectMostRecentItem())
            assertEquals(Coordinates(35.6762, 139.6503), move.target)
            assertEquals(move.target, viewModel.uiState.value.camera.target)
            assertEquals(move.zoom, viewModel.uiState.value.camera.zoom)
        }
    }

    @Test
    fun `map click adds numbered stops to the active day on the server`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.Click(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.Click(Coordinates.London))
        testScheduler.advanceUntilIdle()

        // A tap sends no name: the server names the stop after what's there (the fake uses the coordinates).
        val markers = viewModel.uiState.value.layers.stops
        assertEquals(listOf("48.8566, 2.3522", "51.5074, -0.1278").map(UiText::Server), markers.map { it.title })
        assertEquals(listOf(1, 2), markers.map { it.orderNumber })
        assertEquals(Coordinates.London, markers[1].position)
        assertEquals(2, backend.refreshTrip(trip.trip.id).let { (it as AppResult.Success).data.days[0].markers.size })
    }

    @Test
    fun `typing a search keeps the map's layers as they were`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val layers = viewModel.uiState.value.layers

        viewModel.onAction(MapAction.Search.Open)
        viewModel.onAction(MapAction.Search.QueryChanged("Lou"))
        testScheduler.advanceUntilIdle()

        assertSame(layers, viewModel.uiState.value.layers)
    }

    @Test
    fun `stops belong to their day`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Days.Select(1))
        viewModel.onAction(MapAction.MapView.Click(Coordinates.Rome))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.layers.stops.size)
        viewModel.onAction(MapAction.Days.Select(0))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.layers.stops.isEmpty())
    }

    @Test
    fun `removing a marker renumbers the remaining stops`() = runTest(testDispatcher) {
        val day = trip.days[0].id
        listOf(Coordinates.Paris, Coordinates.London, Coordinates.Rome).forEachIndexed { i, c ->
            backend.addMarker(trip.trip.id, day, NewMarker("Stop #${i + 1}", GeoPoint(c.latitude, c.longitude)))
        }
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Plan.RemoveStop(viewModel.uiState.value.layers.stops[0].id))
        testScheduler.advanceUntilIdle()

        val remaining = viewModel.uiState.value.layers.stops
        assertEquals(listOf("Stop #2", "Stop #3").map(UiText::Server), remaining.map { it.title })
        assertEquals(listOf(1, 2), remaining.map { it.orderNumber })
    }

    @Test
    fun `selecting a stop highlights it and centers the map on it`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val second = viewModel.uiState.value.layers.stops[1]
        viewModel.onAction(MapAction.MapView.MarkerClick(second))
        testScheduler.advanceUntilIdle()

        assertEquals(second.id, viewModel.uiState.value.stopsCard?.selectedStopId)
        assertEquals(second.position, viewModel.uiState.value.camera.target)
    }

    @Test
    fun `a stop's card opens once the camera has centered it, and a touch elsewhere closes it`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        val stop = viewModel.uiState.value.layers.stops.single()
        viewModel.onAction(MapAction.MapView.MarkerClick(stop))
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.stopCard)

        viewModel.onAction(MapAction.MapView.CameraRest(stop.position, 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.stopCard)

        viewModel.onAction(MapAction.MapView.ScreenTouch)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.stopsCard?.selectedStopId)
        assertNull(viewModel.uiState.value.stopCard)
    }

    @Test
    fun `photos picked on a stop's card upload to it and open its photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.layers.stops.single()
        viewModel.onAction(MapAction.MapView.MarkerClick(stop))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.StopCard.AddPhotos(listOf(PickedImage { byteArrayOf(1) }, PickedImage { byteArrayOf(2) })))
        testScheduler.advanceUntilIdle()

        assertEquals(stop.id, viewModel.uiState.value.stopPhotos?.id)
        assertEquals(2, photos.photos(PhotoOwner.Stop(stop.id)).first()?.size)

        viewModel.onAction(MapAction.Sheet.CloseStopPhotos)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.stopPhotos)
    }

    @Test
    fun `a photo taken on a stop's card uploads to it, closes the camera and opens its photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.layers.stops.single()
        viewModel.onAction(MapAction.MapView.MarkerClick(stop))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.StopCard.OpenCamera)
        testScheduler.advanceUntilIdle()
        assertEquals(MapSheet.PhotoCamera, viewModel.uiState.value.sheet)

        viewModel.onAction(MapAction.StopCard.AddPhotos(listOf(PickedImage { byteArrayOf(1) })))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotEquals(MapSheet.PhotoCamera, state.sheet)
        assertEquals(stop.id, state.stopPhotos?.id)
        assertEquals(1, photos.photos(PhotoOwner.Stop(stop.id)).first()?.size)
    }

    @Test
    fun `a viewer can't add photos`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stephansdom", GeoPoint(48.2085, 16.3721)))
        val withStop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data
        backend.seed(withStop.copy(trip = withStop.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val stop = viewModel.uiState.value.layers.stops.single()
        viewModel.onAction(MapAction.MapView.MarkerClick(stop))

        viewModel.onAction(MapAction.StopCard.OpenCamera)
        viewModel.onAction(MapAction.StopCard.AddPhotos(listOf(PickedImage { byteArrayOf(1) })))
        testScheduler.advanceUntilIdle()

        assertNull(photos.photos(PhotoOwner.Stop(stop.id)).first())
        assertNull(viewModel.uiState.value.stopPhotos)
        assertNotEquals(MapSheet.PhotoCamera, viewModel.uiState.value.sheet)
    }

    @Test
    fun `the map rests where it opened, so a rotation keeps it there`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.85, 2.35), 15f))
        testScheduler.advanceUntilIdle()

        val camera = viewModel.uiState.value.camera
        assertNull(camera.openingBounds)
        assertEquals(15f, camera.zoom)
    }

    @Test
    fun `opening a trip frames the open day's stops`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(48.86, 2.29)))
        backend.addMarker(trip.trip.id, trip.days[1].id, NewMarker("Stop #1", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        viewModel.cameraCommands.test {
            testScheduler.advanceUntilIdle()

            // The new map opens already fitted to them, the same area the fit-day button fits; nothing moves it after.
            val camera = viewModel.uiState.value.camera
            assertTrue(camera.isReady)
            assertEquals(CameraCommand.fit(listOf(Coordinates(48.85, 2.35), Coordinates(48.86, 2.29))).bounds, camera.openingBounds)
            expectNoEvents()

            // A day tab leaves the camera where it is; the fit-day button frames the open day.
            viewModel.onAction(MapAction.Days.Select(1))
            testScheduler.advanceUntilIdle()
            expectNoEvents()
            viewModel.onAction(MapAction.Days.FitDay)
            testScheduler.advanceUntilIdle()
            assertEquals(CameraCommand.fit(listOf(Coordinates(41.9, 12.49))), awaitItem())
        }
    }

    @Test
    fun `reopening a trip opens its day and route as left, framed like the fit-day button`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[1].id, NewMarker("Stop #1", GeoPoint(41.9, 12.49)))
        backend.addMarker(trip.trip.id, trip.days[1].id, NewMarker("Stop #2", GeoPoint(41.89, 12.48)))
        val left = viewModel()
        testScheduler.advanceUntilIdle()
        lateinit var dayTabFit: CameraCommand.Fit
        left.cameraCommands.test {
            left.onAction(MapAction.Days.Select(1))
            left.onAction(MapAction.Days.FitDay)
            testScheduler.advanceUntilIdle()
            dayTabFit = assertIs<CameraCommand.Fit>(expectMostRecentItem())
        }
        // The user moved away from the stops before leaving.
        left.onAction(MapAction.MapView.CameraRest(Coordinates(10.0, 10.0), 5f))
        left.onAction(MapAction.Plan.ToggleRoute)
        left.onAction(MapAction.Plan.SelectTravelMode(TravelMode.Cycling))
        testScheduler.advanceUntilIdle()

        val reopened = viewModel()
        testScheduler.advanceUntilIdle()
        val state = reopened.uiState.value
        // Opens fitted to the same area as the fit-day button.
        assertEquals(dayTabFit.bounds, state.camera.openingBounds)
        assertEquals(1, state.dayTabs.activeIndex)
        assertTrue(state.camera.isReady)
        assertEquals(true, state.stopsCard?.isRouteOn)
        assertEquals(TravelMode.Cycling, state.stopsCard?.route?.mode)
    }

    @Test
    fun `a day without stops shows the whole trip, as when a trip opens on it`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.Days.Select(2))
            viewModel.onAction(MapAction.Days.FitDay)
            testScheduler.advanceUntilIdle()
            assertEquals(CameraCommand.fit(listOf(Coordinates(48.85, 2.35))), expectMostRecentItem())
        }
    }

    @Test
    fun `a reopened trip whose day is gone opens on its first day`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        mapViews.views[trip.trip.id] = TripMapView("gone", isRouteShown = false, TravelMode.Walking)

        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.dayTabs.activeIndex)
        assertTrue(viewModel.uiState.value.camera.isReady)
    }

    @Test
    fun `a tap on Route shows the whole plan, on and off, and without Pro opens the paywall`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
            viewModel.onAction(MapAction.Plan.ToggleRoute)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.ShowWholePlan, awaitItem())
            viewModel.onAction(MapAction.Plan.ToggleRoute)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.ShowWholePlan, awaitItem())
        }

        session = signedIn(entitlements = emptySet())
        val free = viewModel()
        testScheduler.advanceUntilIdle()
        free.events.test {
            free.onAction(MapAction.Plan.ToggleRoute)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenPaywall, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a route left on stays off without Pro`() = runTest(testDispatcher) {
        session = signedIn(entitlements = emptySet())
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        mapViews.views[trip.trip.id] = TripMapView(trip.days[0].id, isRouteShown = true, TravelMode.Driving)

        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.stopsCard?.isRouteOn == true)
    }

    @Test
    fun `a trip whose first day is empty frames the whole trip`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[2].id, NewMarker("Stop #1", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(CameraCommand.fit(listOf(Coordinates(41.9, 12.49))).bounds, viewModel.uiState.value.camera.openingBounds)
    }

    @Test
    fun `a trip without stops opens at its destination, not at the device location`() = runTest(testDispatcher) {
        val rome = (
            backend.createTrip(
                NewTrip("Rome", null, null, destination = Destination("Rome", GeoPoint(41.9028, 12.4964))),
            ) as AppResult.Success
        ).data
        val viewModel = viewModel(rome.trip.id)
        viewModel.cameraCommands.test {
            testScheduler.advanceUntilIdle()
            viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Granted))
            testScheduler.advanceUntilIdle()

            assertEquals(CameraCommand.MoveTo(Coordinates(41.9028, 12.4964), 12f), awaitItem())
            expectNoEvents()
            val state = viewModel.uiState.value
            assertEquals(Coordinates(41.9028, 12.4964), state.camera.target)
            assertEquals(12f, state.camera.zoom)
            assertTrue(state.camera.isReady)
        }
    }

    @Test
    fun `the fit-day button frames the day again each time`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.Days.FitDay)
            testScheduler.advanceUntilIdle()
            val first = assertIs<CameraCommand.Fit>(awaitItem())

            viewModel.onAction(MapAction.Days.FitDay)
            testScheduler.advanceUntilIdle()

            assertEquals(first, awaitItem())
        }
    }

    @Test
    fun `clear all asks first, then empties the day`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #2", GeoPoint(41.9, 12.49)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Plan.ClearAll)
        testScheduler.advanceUntilIdle()
        assertEquals(MapDialog.ClearDay(dayNumber = 1, stopCount = 2), viewModel.uiState.value.dialog)
        assertEquals(2, viewModel.uiState.value.layers.stops.size)

        viewModel.onAction(MapAction.Dialog.Confirm)
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.layers.stops.size)
        assertNull(viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.stopsCard?.selectedStopId)
    }

    @Test
    fun `an editor deletes only their own stops, and removes their things from someone else's`() = runTest(testDispatcher) {
        val mine = backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Mine", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Theirs", GeoPoint(41.9, 12.49)))
        val shared = backend.observeTrip(trip.trip.id).first()!!
        backend.seed(
            shared.copy(
                trip = shared.trip.copy(role = TripRole.EDITOR),
                days = shared.days.map { day ->
                    day.copy(markers = day.markers.map { if (it.id == mine.id) it else it.copy(createdById = "someone-else") })
                },
            ),
        )
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(true, false), viewModel.uiState.value.layers.stops.map { it.canDelete })
        assertEquals(false, viewModel.uiState.value.header.editorMenu?.canDeleteDay)
        assertEquals(false, viewModel.uiState.value.stopsCard?.canClear)

        viewModel.onAction(MapAction.MapView.MarkerClick(viewModel.uiState.value.layers.stops[1]))
        viewModel.onAction(MapAction.StopCard.RemoveMyContent)
        testScheduler.advanceUntilIdle()
        assertEquals(MapDialog.RemoveMyContent, viewModel.uiState.value.dialog)

        viewModel.onAction(MapAction.Dialog.Confirm)
        testScheduler.advanceUntilIdle()
        val note = assertIs<UiText.Note>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.map_my_content_removed, assertIs<UiText.Resource>(note.text).resource)
        assertEquals(2, viewModel.uiState.value.layers.stops.size)
    }

    @Test
    fun `on someone else's trip the header says whose it is, with its dates, stops and members`() = runTest(testDispatcher) {
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Owner's", GeoPoint(48.85, 2.35)))
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Another editor's", GeoPoint(41.9, 12.49)))
        val own = viewModel()
        testScheduler.advanceUntilIdle()
        assertNull(own.uiState.value.header.ownerLabel)
        assertEquals(2, own.uiState.value.header.stopCount)

        val shared = backend.observeTrip(trip.trip.id).first()!!
        backend.seed(
            shared.copy(
                trip = shared.trip.copy(role = TripRole.EDITOR),
                days = shared.days.map { day ->
                    day.copy(markers = day.markers.mapIndexed { i, it -> it.copy(createdById = if (i == 0) shared.trip.owner.id else "someone-else") })
                },
            ),
        )
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val header = viewModel.uiState.value.header
        assertEquals(shared.members.size, header.members?.count)
        assertEquals(0, header.members?.requestCount)
        val owner = assertIs<UiText.Resource>(viewModel.uiState.value.header.ownerLabel)
        assertEquals(Res.string.map_trip_owner, owner.resource)
    }

    @Test
    fun `deleting a stop with other people's things warns first, then deletes it for everyone`() = runTest(testDispatcher) {
        val stop = backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop #1", GeoPoint(48.85, 2.35)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        backend.nextError = DataError.Network.Api(409, "STOP_HAS_OTHERS_CONTENT", "Their photos go too.")

        viewModel.onAction(MapAction.Plan.RemoveStop(stop.id))
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.layers.stops.size)
        assertEquals(UiText.Server("Their photos go too."), viewModel.uiState.value.othersContentMessage)
        assertNull(viewModel.uiState.value.message)

        viewModel.onAction(MapAction.Dialog.DeleteAnyway)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.layers.stops.isEmpty())
        assertNull(viewModel.uiState.value.othersContentMessage)
    }

    @Test
    fun `deleting a day with other people's things warns first`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        backend.nextError = DataError.Network.Api(409, "STOP_HAS_OTHERS_CONTENT", "Their photos go too.")

        viewModel.onAction(MapAction.Header.DeleteDay)
        viewModel.onAction(MapAction.Dialog.Confirm)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.dayTabs.days.size)
        assertEquals(UiText.Server("Their photos go too."), viewModel.uiState.value.othersContentMessage)
        assertNull(viewModel.uiState.value.message)

        viewModel.onAction(MapAction.Dialog.DeleteAnyway)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.dayTabs.days.size)
    }

    @Test
    fun `adding a day selects it, deleting it goes back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Header.AddDay)
        testScheduler.advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.dayTabs.days.size)
        assertEquals(3, viewModel.uiState.value.dayTabs.activeIndex)

        viewModel.onAction(MapAction.Header.DeleteDay)
        viewModel.onAction(MapAction.Dialog.Confirm)
        testScheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.dayTabs.days.size)
        assertEquals(2, viewModel.uiState.value.dayTabs.activeIndex)
    }

    @Test
    fun `a refused change shows the server's message`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        backend.nextError = DataError.Network.Api(403, "FORBIDDEN", "You can't change this trip.")

        viewModel.onAction(MapAction.MapView.Click(Coordinates.Paris))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.layers.stops.isEmpty())
        val message = assertIs<UiText.Joined>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.map_error_stop_change, assertIs<UiText.Resource>(message.first).resource)
        assertNotEquals(true, viewModel.uiState.value.message?.canRetry)
    }

    @Test
    fun `a stop shows at once, and one that couldn't be sent comes back with Retry`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.Click(Coordinates.Paris))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("48.8566, 2.3522").map(UiText::Server), viewModel.uiState.value.layers.stops.map { it.title })
        assertFalse(viewModel.uiState.value.header.isBusy)

        backend.nextError = DataError.Network.NoInternet
        viewModel.onAction(MapAction.MapView.Click(Coordinates.London))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.layers.stops.size)
        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.map_change_not_saved, message.resource)
        assertEquals(true, viewModel.uiState.value.message?.canRetry)

        viewModel.onAction(MapAction.Message.RetryFailedChanges)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("48.8566, 2.3522", "51.5074, -0.1278").map(UiText::Server), viewModel.uiState.value.layers.stops.map { it.title })
        assertNull(viewModel.uiState.value.message)
        assertNotEquals(true, viewModel.uiState.value.message?.canRetry)
    }

    @Test
    fun `viewers can't add stops`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.Click(Coordinates.Paris))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.header.editorMenu)
        assertTrue(viewModel.uiState.value.layers.stops.isEmpty())
    }

    @Test
    fun `a viewer saves the trip and asks to join, once each`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Header.ToggleSave)
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Header.RequestToJoin)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.header.viewerMenu?.isSavedByMe == true)
        assertEquals(JoinRequestStatus.PENDING, state.header.viewerMenu?.myJoinRequest)
        assertEquals(Res.string.map_join_requested, assertIs<UiText.Resource>(assertIs<UiText.Note>(state.message?.text).text).resource)
        assertEquals(listOf(trip.trip.id), backend.observeMyTrips().first().map { it.id })
    }

    @Test
    fun `location failure sets appropriate error message without crashing`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = DataError.Location.PermissionDenied.asError()
        val viewModel = viewModel()

        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.error_location_permission_required, message.resource)
    }
    @Test
    fun `opening the map doesn't ask for the location`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
            viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.NotAsked))
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `a denied answer says how to turn the location on`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.ShouldExplain))
        testScheduler.advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.error_location_denied_manual, message.resource)
        assertFalse(viewModel.uiState.value.layers.isMyLocationEnabled)
    }

    @Test
    fun `the location button asks again after a denial`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = Coordinates(52.5200, 13.4050).asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.NotAsked))
        testScheduler.advanceUntilIdle()
        viewModel.events.test {
            viewModel.onAction(MapAction.Location.CenterOnUser)
            testScheduler.advanceUntilIdle()

            assertEquals(MapEvent.RequestPermission, awaitItem())
        }
        // Allowed: the camera goes to the user, as the button asked.
        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()
        assertEquals(Coordinates(52.5200, 13.4050), viewModel.uiState.value.camera.target)
    }

    @Test
    fun `the location button explains first when the system says so`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.ShouldExplain))
        testScheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onAction(MapAction.Location.CenterOnUser)
            testScheduler.advanceUntilIdle()
            assertEquals(LocationPrompt.Explain, viewModel.uiState.value.locationPrompt)
            expectNoEvents()

            viewModel.onAction(MapAction.Location.ConfirmPrompt)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.RequestPermission, awaitItem())
            assertNull(viewModel.uiState.value.locationPrompt)
        }
    }

    @Test
    fun `the location button sends the user to Settings when it can't ask anymore`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onAction(MapAction.Location.CenterOnUser)
            testScheduler.advanceUntilIdle()
            assertEquals(LocationPrompt.OpenSettings, viewModel.uiState.value.locationPrompt)

            viewModel.onAction(MapAction.Location.ConfirmPrompt)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenAppSettings, awaitItem())
        }
    }

    @Test
    fun `allowing it in Settings moves to the user on return`() = runTest(testDispatcher) {
        val userCoords = Coordinates(52.5200, 13.4050)
        fakeLocationRepository.locationResult = userCoords.asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Blocked))
        viewModel.onAction(MapAction.Location.CenterOnUser)
        viewModel.onAction(MapAction.Location.ConfirmPrompt)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.layers.isMyLocationEnabled)
        assertEquals(userCoords, viewModel.uiState.value.camera.target)
    }

    @Test
    fun `dismissing the prompt stops waiting for the location`() = runTest(testDispatcher) {
        fakeLocationRepository.locationResult = Coordinates(52.5200, 13.4050).asSuccess()
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Blocked))
        viewModel.onAction(MapAction.Location.CenterOnUser)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Location.DismissPrompt)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.locationPrompt)
        // Allowed later in Settings: the map stays where it is.
        val before = viewModel.uiState.value.camera.target
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.Granted))
        testScheduler.advanceUntilIdle()
        assertEquals(before, viewModel.uiState.value.camera.target)
    }
    @Test
    fun `the location button offers Settings when the system prompt can't show`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.NotAsked))
        viewModel.onAction(MapAction.Location.CenterOnUser)
        testScheduler.advanceUntilIdle()

        // Android came straight back: the user said no for good.
        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        assertEquals(LocationPrompt.OpenSettings, viewModel.uiState.value.locationPrompt)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `a blocked answer when the map opens only shows the message`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Location.PermissionChecked(LocationPermission.NotAsked))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Location.PermissionResult(LocationPermission.Blocked))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.locationPrompt)
        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.error_location_denied_manual, message.resource)
    }

    @Test
    fun `the squares in view load when the camera rests, the ones around them once it stays`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val elsewhere = GeoBounds(47.9, 1.9, 48.1, 2.1)

        // The camera rests twice in a row: each view's squares load at once, only the last one's ring.
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.0, 2.0), 15f, elsewhere))
        testScheduler.advanceTimeBy(100)
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15.6f, PARIS_CENTER))
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
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
        restOnTarget(viewModel)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(TRIP_PHOTO, COMMONS_IMAGE), viewModel.uiState.value.placeCard?.details?.images)

        viewModel.onAction(MapAction.PlaceCard.OpenImage(1))
        testScheduler.advanceUntilIdle()
        assertEquals(MapSheet.PlaceImage("fake-louvre", "pi1"), viewModel.uiState.value.sheet)
        viewModel.onAction(MapAction.Sheet.ReportPhoto(COMMONS_IMAGE.asPhoto()))
        viewModel.onAction(MapAction.Dialog.SendReport(ReportReason.Copyright))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Sheet.ReportPhoto(TRIP_PHOTO.asPhoto()))
        viewModel.onAction(MapAction.Dialog.SendReport(ReportReason.Spam))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(ReportTarget.PlaceImage to "pi1", ReportTarget.Photo to "ph1"), social.reports)

        // Closing the place closes its images too.
        viewModel.onAction(MapAction.MapView.ScreenTouch)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.sheet)
    }

    /** The image as the place's photo viewer has it. */
    private fun PlaceImage.asPhoto() = Photo(
        id = id,
        owner = PhotoOwner.Place("fake-louvre"),
        status = PhotoStatus.Ready,
        thumbUrl = thumbUrl,
        displayUrl = displayUrl,
        width = width,
        height = height,
        likeCount = 0,
        likedByMe = false,
        uploader = null,
        credit = credit,
    )

    @Test
    fun `a stop whose pin shows a Commons image credits it, its place asked for once`() = runTest(testDispatcher) {
        places.images["fake-louvre"] = listOf(COMMONS_IMAGE)
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Louvre", LOUVRE, placeId = "fake-louvre"))
        // The server sends the place's image as the cover of a stop without a picked one.
        val withStop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data
        backend.seed(
            withStop.copy(
                days = withStop.days.map { day ->
                    day.copy(markers = day.markers.map { it.copy(coverPhotoId = COMMONS_IMAGE.id, coverThumbUrl = COMMONS_IMAGE.thumbUrl) })
                },
            ),
        )
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val stop = viewModel.uiState.value.layers.stops.single()
        viewModel.onAction(MapAction.MapView.MarkerClick(stop))
        restOnTarget(viewModel)
        testScheduler.advanceUntilIdle()
        assertEquals(COMMONS_IMAGE.credit, viewModel.uiState.value.stopCard?.coverCredit)

        viewModel.onAction(MapAction.MapView.MarkerClick(stop))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("fake-louvre"), places.detailRequests)
    }

    @Test
    fun `a tap on a pin opens its place, and adding it makes a stop named after it`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
        testScheduler.advanceUntilIdle()
        val louvre = assertNotNull(viewModel.uiState.value.layers.selectedPlace)
        assertEquals("Louvre", louvre.name)
        // The camera centers the place, at the same zoom.
        assertEquals(Coordinates(LOUVRE.lat, LOUVRE.lng), viewModel.uiState.value.camera.target)
        assertEquals(15f, viewModel.uiState.value.camera.zoom)
        restOnTarget(viewModel)
        testScheduler.advanceUntilIdle()
        assertEquals("Mo-Su 09:00-18:00", viewModel.uiState.value.placeCard?.details?.openingHours)
        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())

        viewModel.onAction(MapAction.PlaceCard.AddToDay)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.layers.selectedPlace)
        val stop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.single()
        assertEquals("Louvre", stop.name)
        assertEquals(louvre.id, stop.placeId)
        // It shows as the stop only: its pin leaves the map, and a tap there is a tap on the map.
        assertTrue(state.layers.placePins.none { it.place.id == louvre.id })
        viewModel.onAction(MapAction.MapView.Click(Coordinates(LOUVRE.lat, LOUVRE.lng)))
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.layers.selectedPlace)
        assertEquals(2, (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size)
    }

    @Test
    fun `a place's card opens once the camera stops, and touching the map closes the place`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            testScheduler.advanceUntilIdle()
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()

            viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.layers.selectedPlace)
            assertNull(viewModel.uiState.value.placeCard)

            // The camera rests on the place: the card opens.
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(LOUVRE.lat, LOUVRE.lng), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.placeCard)

            // Tapped again while centered: the touch closes the card, and the camera doesn't move, so the
            // card shows again at once.
            viewModel.onAction(MapAction.MapView.ScreenTouch)
            viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.placeCard)

            // A tap on the bare map while the card is open only closes it: no stop is added.
            viewModel.onAction(MapAction.MapView.ScreenTouch)
            viewModel.onAction(MapAction.MapView.Click(Coordinates(LOUVRE.lat, LOUVRE.lng + 0.0021)))
            testScheduler.advanceUntilIdle()
            assertNull(viewModel.uiState.value.layers.selectedPlace)
            assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())

            // The camera stops a little off the place (the layout moved during the flight): the card still opens.
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
            viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(LOUVRE.lat + 0.0002, LOUVRE.lng), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.placeCard)

            // The user drags the map while the camera flies to another tap: the finger closes the place, and the
            // camera stopping where the drag left it opens nothing.
            viewModel.onAction(MapAction.MapView.ScreenTouch)
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
            viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.layers.selectedPlace)
            viewModel.onAction(MapAction.MapView.ScreenTouch)
            viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.85, 2.30), 15f, PARIS_CENTER))
            testScheduler.advanceUntilIdle()
            assertNull(viewModel.uiState.value.layers.selectedPlace)
            assertNull(viewModel.uiState.value.placeCard)
        }

    @Test
    fun `a tap on the bare map adds a stop`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.Click(Coordinates(LOUVRE.lat, LOUVRE.lng + 0.0021)))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.layers.selectedPlace)
        assertEquals(1, (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.size)
    }

    @Test
    fun `a tap on a cluster zooms in on it`() = runTest(testDispatcher) {
        places.cluster = PlaceCluster(12, GeoPoint(48.80, 2.20))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.8, 2.2), 11f, GeoBounds(48.6, 2.0, 49.0, 2.4)))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.ClusterClick(viewModel.uiState.value.layers.placeClusters.single()))
        testScheduler.advanceUntilIdle()

        assertEquals(Coordinates(48.80, 2.20), viewModel.uiState.value.camera.target)
        assertEquals(13f, viewModel.uiState.value.camera.zoom)
        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())
    }

    @Test
    fun `pins are drawn for the places in view, bigger the better known`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        val pins = viewModel.uiState.value.layers.placePins
        assertTrue(pins.isNotEmpty())
        assertTrue(pins.all { it.place.location in PARIS_CENTER })
        assertEquals(PinSizes[2], pins.single { it.place.name == "Louvre" }.size)
    }

    @Test
    fun `a viewer can't add a place`() = runTest(testDispatcher) {
        backend.seed(trip.copy(trip = trip.trip.copy(role = TripRole.VIEWER)))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.PlacePinClick(viewModel.pin("Louvre")))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.PlaceCard.AddToDay)
        testScheduler.advanceUntilIdle()

        assertTrue((backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.isEmpty())
    }

    @Test
    fun `search asks once typing pauses and moves the camera to the pick`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.Search.Open)

        viewModel.onAction(MapAction.Search.QueryChanged("V"))
        testScheduler.advanceTimeBy(100)
        viewModel.onAction(MapAction.Search.QueryChanged("Vi"))
        testScheduler.advanceUntilIdle()

        val vienna = viewModel.uiState.value.search?.results.orEmpty().single()
        assertEquals("Vienna", vienna.name)
        viewModel.onAction(MapAction.Search.ResultClick(vienna))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.search)
        assertEquals(Coordinates(48.2082, 16.3738), state.camera.target)
        // A city fills the screen.
        assertEquals(12f, state.camera.zoom)
    }

    @Test
    fun `a place picked in search is drawn as its pin, photo included`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.86, 2.32), 15f, PARIS_CENTER))
        testScheduler.advanceUntilIdle()
        val pin = viewModel.pin("Louvre")
        // Search sends neither the cover image nor the prominence.
        val searched = pin.copy(coverImageId = null, coverThumbUrl = null, prominence = 0)

        viewModel.onAction(MapAction.Search.ResultClick(PlaceSearchResult("Louvre", null, pin.location, place = searched)))
        testScheduler.advanceUntilIdle()

        assertEquals(pin, viewModel.uiState.value.layers.selectedPlace)
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

        viewModel.onAction(MapAction.Search.ResultClick(address))
        restOnTarget(viewModel)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Kärntner Straße 38", state.layers.selectedPlace?.name)
        assertEquals("Wien, Österreich", state.placeCard?.address)
        assertEquals(Coordinates(48.2052, 16.3696), state.camera.target)
        assertEquals(17f, state.camera.zoom)

        viewModel.onAction(MapAction.PlaceCard.AddToDay)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.layers.selectedPlace)
        val stop = (backend.refreshTrip(trip.trip.id) as AppResult.Success).data.days[0].markers.single()
        assertEquals("Kärntner Straße 38", stop.name)
        assertEquals(GeoPoint(48.2052, 16.3696), stop.location)
    }

    @Test
    fun `a long press drops a pin with the name the user gives`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.MapView.LongClick(Coordinates(48.85, 2.35)))
        testScheduler.runCurrent()
        assertIs<MapDialog.NamePin>(viewModel.uiState.value.dialog).name.setTextAndPlaceCursorAtEnd("Our hotel")
        viewModel.onAction(MapAction.Dialog.ConfirmPinName)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
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

    private suspend fun addStops(vararg points: GeoPoint) = points.forEachIndexed { index, point ->
        backend.addMarker(trip.trip.id, trip.days[0].id, NewMarker("Stop ${index + 1}", point))
    }

    @Test
    fun `the route button draws the day's route, asked for again when the stops or the mode change`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.2085, 16.3731), GeoPoint(48.2104, 16.3655))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        // Without the route, direction lines join the stops, with no routing call.
        viewModel.uiState.value.assertStopPath()
        assertTrue(routes.dayRoutes.isEmpty())

        viewModel.onAction(MapAction.Plan.ToggleRoute)
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        state.assertRoute(state.stopPositions)
        val card = assertNotNull(state.stopsCard)
        assertNotNull(card.route?.durationSeconds)
        assertEquals(state.layers.stops[1].id, card.legLabels.keys.single())
        assertEquals(1, routes.dayRoutes.size)

        // Unrelated changes don't ask again.
        viewModel.onAction(MapAction.MapView.CameraRest(Coordinates(48.2, 16.37), 14f))
        testScheduler.advanceUntilIdle()
        assertEquals(1, routes.dayRoutes.size)

        viewModel.onAction(MapAction.MapView.Click(Coordinates(48.215, 16.36)))
        testScheduler.advanceUntilIdle()
        assertEquals(3, routes.dayRoutes.last().first.size)

        viewModel.onAction(MapAction.Plan.SelectTravelMode(TravelMode.Cycling))
        testScheduler.advanceUntilIdle()
        assertEquals(TravelMode.Cycling, routes.dayRoutes.last().second)
        assertEquals(3, routes.dayRoutes.size)
        viewModel.uiState.value.assertRoute(viewModel.uiState.value.stopPositions, TravelMode.Cycling)

        viewModel.onAction(MapAction.Plan.ToggleRoute)
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.assertStopPath()
        assertEquals(3, viewModel.uiState.value.layers.stops.size)
        assertEquals(3, routes.dayRoutes.size)
    }

    @Test
    fun `without routing the day keeps its own lines between the stops, never straight lines`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.2085, 16.3731), GeoPoint(48.2104, 16.3655))
        routes.error = DataError.Network.Api(503, RouteErrorCodes.ROUTING_UNAVAILABLE, "Routes aren't available")
        val viewModel = viewModel()
        viewModel.onAction(MapAction.Plan.ToggleRoute)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        state.assertStopPath()
        val route = assertNotNull(state.stopsCard?.route)
        assertTrue(route.isEstimated)
        assertNull(route.durationSeconds)
        // The route's own note says so.
        assertNull(state.message)
    }

    @Test
    fun `places along the way get pins of their own color`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.2085, 16.3731), GeoPoint(48.2104, 16.3655))
        val cafe = MapPlace("cafe", "Café Central", PlaceCategory.Cafe, GeoPoint(48.2101, 16.3654), isTripinly = false, hotCount = 0, prominence = 70)
        routes.alongTheWay = listOf(AlongTheWayPlace(cafe, distanceFromRouteMeters = 40, etaFromStartSeconds = 300))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.layers.placePins.none { it.isAlongTheWay })

        viewModel.onAction(MapAction.Plan.ToggleRoute)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.layers.placePins.single { it.place.id == "cafe" }.isAlongTheWay)
        assertEquals(1, viewModel.uiState.value.layers.placePins.count { it.isAlongTheWay })
        assertEquals(listOf(cafe), viewModel.uiState.value.stopsCard?.route?.alongTheWay?.map { it.place })
    }

    @Test
    fun `routes are Tripinly Pro, so a free user's tap opens the paywall`() = runTest(testDispatcher) {
        session = signedIn(entitlements = emptySet())
        addStops(GeoPoint(48.2085, 16.3731), GeoPoint(48.2104, 16.3655))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onAction(MapAction.Plan.ToggleRoute)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenPaywall, awaitItem())
            assertEquals(false, viewModel.uiState.value.stopsCard?.isRouteOn)
            viewModel.uiState.value.assertStopPath()

            val place = MapPlace("a", "Oper", PlaceCategory.Attraction, GeoPoint(48.2031, 16.3691), isTripinly = false, hotCount = 0, prominence = 80)
            viewModel.onAction(MapAction.MapView.PlacePinClick(place))
            viewModel.onAction(MapAction.PlaceCard.RouteFrom)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenPaywall, awaitItem())
            assertNull(viewModel.uiState.value.routeStartName)
            assertTrue(routes.dayRoutes.isEmpty() && routes.pointRoutes.isEmpty())
        }
    }

    @Test
    fun `a route from one place to another shows instead of the day's until it is closed`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.2085, 16.3731), GeoPoint(48.2104, 16.3655))
        val viewModel = viewModel()
        viewModel.onAction(MapAction.Plan.ToggleRoute)
        testScheduler.advanceUntilIdle()
        val from = MapPlace("a", "Oper", PlaceCategory.Attraction, GeoPoint(48.2031, 16.3691), isTripinly = false, hotCount = 0, prominence = 80)
        val to = MapPlace("b", "Prater", PlaceCategory.Park, GeoPoint(48.2166, 16.3958), isTripinly = false, hotCount = 0, prominence = 80)

        viewModel.onAction(MapAction.MapView.PlacePinClick(from))
        viewModel.onAction(MapAction.PlaceCard.RouteFrom)
        testScheduler.advanceUntilIdle()
        assertEquals("Oper", viewModel.uiState.value.routeStartName)
        assertNull(viewModel.uiState.value.layers.selectedPlace)

        viewModel.cameraCommands.test {
            viewModel.onAction(MapAction.MapView.PlacePinClick(to))
            viewModel.onAction(MapAction.PlaceCard.RouteTo)
            testScheduler.advanceUntilIdle()
            // Both places show.
            assertEquals(CameraCommand.fit(listOf(Coordinates(48.2031, 16.3691), Coordinates(48.2166, 16.3958))), expectMostRecentItem())
        }
        val state = viewModel.uiState.value
        val card = assertNotNull(state.pointRouteCard)
        assertEquals(from to to, card.from to card.to)
        assertNull(state.stopsCard)
        assertNull(state.routeStartName)
        assertEquals(listOf(from.location to to.location), routes.pointRoutes)
        state.assertRoute(listOf(Coordinates(48.2031, 16.3691), Coordinates(48.2166, 16.3958)))

        viewModel.onAction(MapAction.Plan.ClosePointRoute)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.pointRouteCard)
        viewModel.uiState.value.assertRoute(viewModel.uiState.value.stopPositions)
    }

    @Test
    fun `the best order shows first and is saved on apply`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.20, 16.37), GeoPoint(48.30, 16.37), GeoPoint(48.21, 16.37))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        val (first, far, near) = viewModel.uiState.value.layers.stops.map { it.id }
        assertEquals(true, viewModel.uiState.value.stopsCard?.canFindBestOrder)

        viewModel.onAction(MapAction.Plan.FindBestOrder)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(first, near, far), viewModel.uiState.value.bestOrder?.stops?.map { it.id })
        assertEquals(listOf(first, far, near), viewModel.uiState.value.layers.stops.map { it.id })

        viewModel.onAction(MapAction.Dialog.ApplyBestOrder)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.bestOrder)
        assertEquals(listOf(first, near, far), viewModel.uiState.value.layers.stops.map { it.id })
    }

    @Test
    fun `stops already in the best order say so`() = runTest(testDispatcher) {
        addStops(GeoPoint(48.20, 16.37), GeoPoint(48.21, 16.37), GeoPoint(48.30, 16.37))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(MapAction.Plan.FindBestOrder)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.bestOrder)
        val note = assertIs<UiText.Note>(viewModel.uiState.value.message?.text)
        assertEquals(Res.string.map_best_order_already, assertIs<UiText.Resource>(note.text).resource)
    }

    @Test
    fun `a fit keeps a few streets around a single stop`() {
        val fit = CameraCommand.fit(listOf(Coordinates(48.2, 16.37)))
        assertEquals(0.005, fit.bounds.north - fit.bounds.south, 1e-9)
        assertEquals(0.005, fit.bounds.east - fit.bounds.west, 1e-9)
        assertEquals(48.2, (fit.bounds.north + fit.bounds.south) / 2, 1e-9)

        val wide = CameraCommand.fit(listOf(Coordinates(48.0, 16.0), Coordinates(48.5, 16.6)))
        assertEquals(GeoBounds(48.0, 16.0, 48.5, 16.6), wide.bounds)
    }
}
