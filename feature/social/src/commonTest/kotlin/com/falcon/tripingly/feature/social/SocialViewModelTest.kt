package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.asSuccess
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.social.ExploreFilter
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Action
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Event
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.NearbyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PhotoStatus
import com.falcon.tripingly.core.model.place.ImageCredit

@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModelTest {

    private val backend = FakeTripBackend()
    private var explore = emptyList<PublicTrip>()

    /** Explore's second page, after the cursor `page-2`. */
    private var exploreMore = emptyList<PublicTrip>()
    private var likeRefused = false
    private var reportRefused = false
    private val reported = mutableListOf<Pair<ReportTarget, String>>()

    /** The filter of every first page of Explore asked for. */
    private val filters = mutableListOf<ExploreFilter>()

    private val social = object : SocialRepository by FakeSocialRepository() {
        override suspend fun explore(filter: ExploreFilter, cursor: String?): AppResult<Page<PublicTrip>, DataError.Network> {
            if (cursor == null) filters += filter
            return if (cursor == null) Page(explore, nextCursor = "page-2".takeIf { exploreMore.isNotEmpty() }).asSuccess() else Page(exploreMore, null).asSuccess()
        }

        override suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason, details: String?): AppResult<Unit, DataError.Network> {
            reported += target to targetId
            return if (reportRefused) AppResult.Error(DataError.Network.NoInternet) else Unit.asSuccess()
        }

        override suspend fun setLiked(target: LikeTarget, targetId: String, liked: Boolean): AppResult<LikeState, DataError.Network> =
            if (likeRefused) AppResult.Error(DataError.Network.NoInternet) else LikeState(liked, if (liked) 5 else 4).asSuccess()
    }

    /** Counts what reaches the server. */
    private val users = object : UserRepository by FakeUserRepository() {
        val queries = mutableListOf<String>()
        private val fake = FakeUserRepository()
        override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
            queries += query
            return fake.search(query)
        }
    }

    private val location = GetCurrentLocationUseCase(
        object : LocationRepository {
            override fun getLocationStream() = flowOf(Coordinates.Paris.asSuccess())
        },
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SocialViewModel(social, users, FakePlaceRepository(), backend, backend, location)

    /** One of my trips, as if Explore listed it to someone else. */
    private suspend fun publicTrip(): PublicTrip {
        val details: TripDetails = (backend.createTrip(NewTrip("Vienna", LocalDate(2026, 6, 1), LocalDate(2026, 6, 2))) as AppResult.Success).data
        return PublicTrip(details.trip.copy(role = TripRole.VIEWER), likeCount = 4, likedByMe = false, saveCount = 0)
    }

    /** Anna's public trip. */
    private fun othersTrip(): PublicTrip {
        val trip = Trip(
            id = "t-anna",
            name = "Budapest",
            startDate = null,
            endDate = null,
            visibility = TripVisibility.PUBLIC,
            role = TripRole.VIEWER,
            owner = UserSummary("u-anna", "anna", "Anna"),
            dayCount = 1,
            markerCount = 2,
            createdAt = Instant.parse("2026-10-16T10:00:00Z"),
            updatedAt = Instant.parse("2026-10-16T10:00:00Z"),
        )
        backend.seed(TripDetails(trip, emptyList(), emptyList()))
        return PublicTrip(trip, likeCount = 0, likedByMe = false, saveCount = 2)
    }

    @Test
    fun likingATrip_showsAtOnce_andKeepsIt() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.LikeTrip(viewModel.uiState.value.explore.trips.single()))
        assertTrue(viewModel.uiState.value.explore.trips.single().likedByMe)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.explore.trips.single().likeCount)
    }

    @Test
    fun likingATrip_refused_putsItBack() = runTest {
        explore = listOf(publicTrip())
        likeRefused = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.LikeTrip(viewModel.uiState.value.explore.trips.single()))
        advanceUntilIdle()

        val trip = viewModel.uiState.value.explore.trips.single()
        assertFalse(trip.likedByMe)
        assertEquals(4, trip.likeCount)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun peopleSearch_asksOnceTypingPauses() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        listOf("a", "an", "ann").forEach {
            viewModel.onAction(Action.People.QueryChanged(it))
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("ann"), users.queries)
        assertEquals("anna", viewModel.uiState.value.people.results.single().username)
    }

    @Test
    fun savingATrip_showsAtOnce_andPutsItInMyTrips() = runTest {
        explore = listOf(othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.ToggleSaveTrip(viewModel.uiState.value.explore.trips.single()))
        val trip = viewModel.uiState.value.explore.trips.single()
        assertTrue(trip.trip.savedByMe)
        assertEquals(3, trip.saveCount)
        advanceUntilIdle()

        assertEquals(listOf("t-anna"), backend.observeMyTrips().first().map { it.id })
    }

    @Test
    fun savingMyOwnTrip_refused_putsItBack() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.ToggleSaveTrip(viewModel.uiState.value.explore.trips.single()))
        advanceUntilIdle()

        val trip = viewModel.uiState.value.explore.trips.single()
        assertFalse(trip.trip.savedByMe)
        assertEquals(0, trip.saveCount)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun explore_opensOnThisMonth_andAPickedPeriodReloadsTheList() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.PeriodSelected(ExplorePeriod.Week))
        viewModel.onAction(Action.Explore.PeriodSelected(ExplorePeriod.Week))
        advanceUntilIdle()

        assertEquals(listOf(ExploreFilter(ExplorePeriod.Month), ExploreFilter(ExplorePeriod.Week)), filters)
        assertEquals(ExplorePeriod.Week, viewModel.uiState.value.explore.period)
    }

    @Test
    fun exploreNearby_asksForTheLocationOnlyWhenSwitchedOn() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.ToggleNearby)
        advanceUntilIdle()
        assertEquals(Event.CheckExploreLocation, viewModel.events.first())

        viewModel.onAction(Action.Explore.LocationChecked(LocationPermission.NotAsked))
        advanceUntilIdle()
        assertEquals(Event.RequestPermission, viewModel.events.first())

        viewModel.onAction(Action.LocationPermissionResult(LocationPermission.Granted))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.explore.isNearbyFilter, "Nearby is on once allowed")
        val paris = GeoPoint(Coordinates.Paris.latitude, Coordinates.Paris.longitude)
        assertEquals(ExploreFilter(ExplorePeriod.Month, near = paris), filters.last(), "loads the trips near the user")
        // Places nearby load only on their own tab.
        assertEquals(NearbyStatus.Unknown, viewModel.uiState.value.nearby.status, "places nearby don't load")

        viewModel.onAction(Action.Explore.ToggleNearby)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.explore.isNearbyFilter, "Nearby switches off")
        assertEquals(ExploreFilter(ExplorePeriod.Month), filters.last(), "reloads without the location")
    }

    @Test
    fun exploreNearby_staysOffWhenTheLocationIsRefused() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.ToggleNearby)
        viewModel.onAction(Action.Explore.LocationChecked(LocationPermission.ShouldExplain))
        viewModel.onAction(Action.LocationPermissionResult(LocationPermission.ShouldExplain))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.explore.isNearbyFilter)

        viewModel.onAction(Action.Explore.LocationChecked(LocationPermission.Blocked))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.explore.isNearbyFilter)
        assertTrue(viewModel.uiState.value.message != null)
        assertEquals(1, filters.size)
    }

    @Test
    fun fastTaps_theLatestWins_andARefusalPutsBackTheLastConfirmedHeart() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.Explore.LikeTrip(viewModel.uiState.value.explore.trips.single()))
        advanceUntilIdle()

        likeRefused = true
        repeat(3) { viewModel.onAction(Action.Explore.LikeTrip(viewModel.uiState.value.explore.trips.single())) }
        assertFalse(viewModel.uiState.value.explore.trips.single().likedByMe, "the third tap shows at once")
        advanceUntilIdle()

        val trip = viewModel.uiState.value.explore.trips.single()
        assertTrue(trip.likedByMe, "back to the heart the server confirmed")
        assertEquals(5, trip.likeCount)
    }

    @Test
    fun blockingSomeone_dropsTheirTripsAndTheirRow_atOnce() = runTest {
        explore = listOf(othersTrip(), publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.People.QueryChanged("an"))
        advanceUntilIdle()
        val anna = viewModel.uiState.value.people.results.single()

        users.setBlocked("u-anna", blocked = true)
        users.setBlocked(anna.id, blocked = true)
        advanceUntilIdle()

        assertEquals(listOf("Vienna"), viewModel.uiState.value.explore.trips.map { it.trip.name })
        assertTrue(viewModel.uiState.value.people.results.isEmpty())
    }

    @Test
    fun explore_loadsMoreAtTheEnd_withoutRepeats() = runTest {
        val vienna = publicTrip()
        explore = listOf(vienna)
        exploreMore = listOf(vienna, othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Explore.LoadMore)
        viewModel.onAction(Action.Explore.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("Vienna", "Budapest"), viewModel.uiState.value.explore.trips.map { it.trip.name })
    }

    @Test
    fun reportingATrip_closesTheDialogOnceSent_andStaysOpenWhenRefused() = runTest {
        explore = listOf(othersTrip())
        val viewModel = viewModel()
        advanceUntilIdle()
        val trip = viewModel.uiState.value.explore.trips.single()

        reportRefused = true
        viewModel.onAction(Action.Explore.ReportTrip(trip))
        viewModel.onAction(Action.Report.Send(ReportReason.Spam, ""))
        advanceUntilIdle()
        assertEquals(SocialViewModel.ReportState(ReportTarget.Trip, trip.trip.id), viewModel.uiState.value.report, "open again for another try")
        assertTrue(viewModel.uiState.value.message != null)

        reportRefused = false
        viewModel.onAction(Action.Report.Send(ReportReason.Spam, ""))
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.report)
    }

    @Test
    fun nearby_asksFirst_thenSettingsWhenBlocked_andListsPlacesOnceAllowed() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.SectionSelected(SocialViewModel.Section.Nearby))

        viewModel.onAction(Action.Nearby.PermissionChecked(LocationPermission.NotAsked))
        assertEquals(NearbyStatus.NeedsPermission, viewModel.uiState.value.nearby.status)
        viewModel.onAction(Action.Nearby.AllowLocation)
        assertEquals(Event.RequestPermission, viewModel.events.first())

        viewModel.onAction(Action.LocationPermissionResult(LocationPermission.Blocked))
        assertEquals(NearbyStatus.Blocked, viewModel.uiState.value.nearby.status)
        viewModel.onAction(Action.Nearby.AllowLocation)
        assertEquals(Event.OpenAppSettings, viewModel.events.first())

        viewModel.onAction(Action.Nearby.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        assertEquals(NearbyStatus.Ready, viewModel.uiState.value.nearby.status)
        assertTrue(viewModel.uiState.value.nearby.places.isNotEmpty())
    }

    @Test
    fun aNearbyPlacesImages_openFromItsCover_andACommonsImageIsReportedAsAPlaceImage() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.Nearby.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.nearby.places.first()

        viewModel.onAction(Action.Nearby.OpenImage(place))
        assertEquals(place, viewModel.uiState.value.nearby.openImage)

        val commons = commonsImage("commons-1")
        viewModel.onAction(Action.Nearby.ReportImage(commons))
        viewModel.onAction(Action.Report.Send(ReportReason.Other, ""))
        viewModel.onAction(Action.Nearby.ReportImage(commons.copy(id = "photo-1", credit = null)))
        viewModel.onAction(Action.Report.Send(ReportReason.Other, ""))
        advanceUntilIdle()
        assertEquals(listOf(ReportTarget.PlaceImage to "commons-1", ReportTarget.Photo to "photo-1"), reported)

        viewModel.onAction(Action.Nearby.CloseImage)
        assertEquals(null, viewModel.uiState.value.nearby.openImage)
    }

    private fun commonsImage(id: String) = Photo(
        id = id,
        owner = PhotoOwner.Place("place"),
        status = PhotoStatus.Ready,
        thumbUrl = null,
        displayUrl = null,
        width = null,
        height = null,
        position = 0,
        likeCount = 0,
        likedByMe = false,
        uploaderId = null,
        credit = ImageCredit(author = "A", title = null, license = "CC BY-SA 4.0", licenseUrl = null, sourceUrl = "https://commons.wikimedia.org/wiki/File:A.jpg"),
    )

    @Test
    fun addingAPlace_withoutTripsOfMine_says_so() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.Nearby.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()

        viewModel.onAction(Action.Nearby.AddPlace(viewModel.uiState.value.nearby.places.first()))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.addPlace)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun addingAPlace_pickTripThenDay_addsTheStop_andOpensTheTrip() = runTest {
        val vienna = publicTrip().trip.id
        val viewModel = viewModel()
        viewModel.onAction(Action.Nearby.PermissionChecked(LocationPermission.Granted))
        advanceUntilIdle()
        val place = viewModel.uiState.value.nearby.places.first()

        viewModel.onAction(Action.Nearby.AddPlace(place))
        advanceUntilIdle()
        val trip = viewModel.uiState.value.addPlace!!.trips.single()
        viewModel.onAction(Action.AddToTrip.PickTrip(trip))
        advanceUntilIdle()
        viewModel.onAction(Action.AddToTrip.PickDay(viewModel.uiState.value.addPlace!!.days.first()))
        advanceUntilIdle()

        assertEquals(Event.OpenTrip(vienna), viewModel.events.first())
        assertEquals(listOf(place.name), backend.observeTrip(vienna).first()!!.days.first().markers.map { it.name })
    }
}
