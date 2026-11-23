package com.falcon.tripingly.feature.map.presentation.spot

import app.cash.turbine.test
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.billing.FakeProRepository
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.routing.FakeRouteRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.route.ROUTES_ENTITLEMENT
import com.falcon.tripingly.core.model.route.RoutePlan
import com.falcon.tripingly.core.model.route.TransitTime
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.social.HotState
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_to_trip_no_trips
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapEvent
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SpotMapViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var places: CountingPlaceRepository
    private lateinit var social: RefusingSocialRepository
    private lateinit var backend: FakeTripBackend
    private lateinit var session: FakeAccountBackend
    private lateinit var routes: RecordingRouteRepository

    /** The fake's sights, counting the details asked for; [failing] answers with an error. */
    private class CountingPlaceRepository(private val fake: FakePlaceRepository = FakePlaceRepository()) : PlaceRepository by fake {
        val detailRequests = mutableListOf<String>()
        var failing = false

        override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> {
            detailRequests += id
            return if (failing) AppResult.Error(DataError.Network.NoInternet) else fake.place(id)
        }
    }

    /** Refuses flames while [hotError] is set. */
    private class RefusingSocialRepository(private val fake: FakeSocialRepository = FakeSocialRepository()) : SocialRepository by fake {
        var hotError: DataError.Network? = null

        override suspend fun setHot(placeId: String, hot: Boolean): AppResult<HotState, DataError.Network> =
            hotError?.let { AppResult.Error(it) } ?: fake.setHot(placeId, hot)
    }

    /** The fake's straight lines, keeping the trip each route between two points was asked for. */
    private class RecordingRouteRepository(private val fake: FakeRouteRepository = FakeRouteRepository()) : RouteRepository by fake {
        val pointRouteTrips = mutableListOf<String?>()

        override suspend fun route(
            from: GeoPoint,
            to: GeoPoint,
            mode: TravelMode,
            tripId: String?,
            fromPlaceId: String?,
            toPlaceId: String?,
            time: TransitTime?,
            categories: Set<PlaceCategory>,
        ): AppResult<RoutePlan, DataError.Network> {
            pointRouteTrips += tripId
            return fake.route(from, to, mode, tripId, fromPlaceId, toPlaceId, time, categories)
        }
    }

    private class FixedLocationRepository : LocationRepository {
        override fun getLocationStream(): Flow<AppResult<Coordinates, DataError.Location>> = flowOf(Coordinates(48.8566, 2.3522).asSuccess())
    }

    @BeforeTest
    fun setUp() = runTest {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        places = CountingPlaceRepository()
        social = RefusingSocialRepository()
        backend = FakeTripBackend()
        routes = RecordingRouteRepository()
        session = signedIn(setOf(ROUTES_ENTITLEMENT))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun signedIn(entitlements: Set<String>) =
        FakeAccountBackend(today = { LocalDate(2026, 10, 16) }, entitlements = entitlements).also {
            it.signInForDevelopment("me", "Me")
            it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
            it.accept((it.legalDocuments("en") as AppResult.Success).data)
        }

    private fun TestScope.viewModel(placeId: String = EIFFEL) = SpotMapViewModel(
        placeId = placeId,
        placeRepository = places,
        socialRepository = social,
        tripRepository = backend,
        markerRepository = backend,
        routeRepository = routes,
        appConfigRepository = FakeAppConfigRepository(AppConfig()),
        sessionRepository = session,
        proRepository = FakeProRepository(session, backgroundScope),
        getCurrentLocationUseCase = GetCurrentLocationUseCase(FixedLocationRepository()),
        photoRepository = FakePhotoRepository(),
        computation = testDispatcher,
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    /** The map comes to rest where the camera flew, so the open card opens. */
    private fun TestScope.restOnTarget(viewModel: SpotMapViewModel) {
        testScheduler.advanceUntilIdle()
        val camera = viewModel.uiState.value.map.camera
        viewModel.onMapAction(MapAction.MapView.CameraRest(camera.target, camera.zoom, null))
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `the spot opens on its pin with its card, its name and flames in the header`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        restOnTarget(viewModel)

        val state = viewModel.uiState.value
        assertEquals("Eiffel Tower", state.spot?.name)
        assertEquals(12, state.spot?.hotCount)
        assertEquals(Coordinates(48.8584, 2.2945), state.map.camera.target)
        assertEquals(EIFFEL, state.map.placeCard?.place?.id)
        // Its + puts it on one of my trips.
        assertEquals(PinAdd.ToMyTrip, state.map.placeCard?.add)
        assertEquals(EIFFEL, state.map.layers.selectedPlace?.id)
        // Asked for once: its card doesn't ask again.
        assertEquals(listOf(EIFFEL), places.detailRequests)
    }

    @Test
    fun `a spot that doesn't load shows the error, and retry loads it`() = runTest(testDispatcher) {
        places.failing = true
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.spot)
        assertNotNull(viewModel.uiState.value.loadError)

        places.failing = false
        viewModel.onAction(SpotMapAction.Retry)
        restOnTarget(viewModel)
        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Eiffel Tower", viewModel.uiState.value.spot?.name)
    }

    @Test
    fun `the flame shows at once, and a refusal puts it back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        restOnTarget(viewModel)

        viewModel.onAction(SpotMapAction.ToggleHot)
        testScheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.spot?.isHot)
        assertEquals(13, viewModel.uiState.value.spot?.hotCount)
        // The card counts the same.
        assertEquals(13, viewModel.uiState.value.map.placeCard?.details?.hotCount)

        social.hotError = DataError.Network.NoInternet
        viewModel.onAction(SpotMapAction.ToggleHot)
        testScheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.spot?.isHot)
        assertNotNull(viewModel.uiState.value.map.message)
    }

    @Test
    fun `a touch closes the card and keeps the pin, the fit button brings the card back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        restOnTarget(viewModel)

        viewModel.onMapAction(MapAction.MapView.ScreenTouch)
        testScheduler.advanceUntilIdle()
        val touched = viewModel.uiState.value.map
        assertNull(touched.placeCard)
        assertTrue(touched.layers.placePins.any { it.place.id == EIFFEL })

        viewModel.onMapAction(MapAction.Days.FitDay)
        restOnTarget(viewModel)
        assertEquals(EIFFEL, viewModel.uiState.value.map.placeCard?.place?.id)
    }

    @Test
    fun `Around opens around the spot, a place found opens a card of its own, and closing it brings the spot back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        restOnTarget(viewModel)

        viewModel.onAction(SpotMapAction.Around)
        testScheduler.advanceUntilIdle()
        val around = viewModel.uiState.value.map
        assertEquals(Coordinates(48.8584, 2.2945), around.layers.aroundRing?.center)
        assertNull(around.placeCard)
        assertEquals(true, viewModel.uiState.value.spot?.routes?.isAroundOn)

        val louvre = MapPlace("fake-louvre", "Louvre", PlaceCategory("museum"), GeoPoint(48.8606, 2.3376), isTripinly = true, hotCount = 8, prominence = 97)
        viewModel.onMapAction(MapAction.Around.PlaceClick(louvre))
        restOnTarget(viewModel)
        // View only: nothing to add.
        assertEquals("fake-louvre", viewModel.uiState.value.map.placeCard?.place?.id)
        assertNull(viewModel.uiState.value.map.placeCard?.add)

        viewModel.onMapAction(MapAction.Around.Close)
        restOnTarget(viewModel)
        assertNull(viewModel.uiState.value.map.layers.aroundRing)
        assertEquals(EIFFEL, viewModel.uiState.value.map.placeCard?.place?.id)
    }

    @Test
    fun `Go here routes from me to the spot without a trip, and a second tap closes it with the card back`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        viewModel.onMapAction(MapAction.Location.PermissionChecked(LocationPermission.Granted))
        restOnTarget(viewModel)

        viewModel.onAction(SpotMapAction.GoHere)
        testScheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.spot?.routes?.isGoHereOn)
        // The spot map has no trip to leave out of the places along the way.
        assertEquals(listOf<String?>(null), routes.pointRouteTrips)

        viewModel.onAction(SpotMapAction.GoHere)
        restOnTarget(viewModel)
        assertEquals(false, viewModel.uiState.value.spot?.routes?.isGoHereOn)
        assertEquals(EIFFEL, viewModel.uiState.value.map.placeCard?.place?.id)
    }

    @Test
    fun `Go here and Around are Tripinly Pro, so without it the paywall opens`() = runTest(testDispatcher) {
        session = signedIn(entitlements = emptySet())
        val viewModel = viewModel()
        restOnTarget(viewModel)
        viewModel.events.test {
            viewModel.onAction(SpotMapAction.GoHere)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenPaywall, awaitItem())
            viewModel.onAction(SpotMapAction.Around)
            testScheduler.advanceUntilIdle()
            assertEquals(MapEvent.OpenPaywall, awaitItem())
        }
        assertNull(viewModel.uiState.value.map.layers.aroundRing)
        // The card stays as it was.
        assertEquals(EIFFEL, viewModel.uiState.value.map.placeCard?.place?.id)
    }

    @Test
    fun `the bar's + is about the spot also while its card is closed, and leaves the map as it was`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        restOnTarget(viewModel)
        viewModel.onMapAction(MapAction.MapView.ScreenTouch)
        testScheduler.advanceUntilIdle()

        // Without trips of mine there is nowhere to add it to.
        viewModel.onAction(SpotMapAction.Add)
        testScheduler.advanceUntilIdle()
        assertEquals(UiText.Resource(Res.string.map_add_to_trip_no_trips), viewModel.uiState.value.map.message)
        assertNull(viewModel.uiState.value.map.placeCard)
        assertFalse(viewModel.uiState.value.map.layers.selectedPlace?.id == EIFFEL)
    }

    private companion object {
        const val EIFFEL = "fake-eiffel"
    }
}
