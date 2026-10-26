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
import com.falcon.tripingly.core.model.social.HotState
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
abstract class SocialTestBase {

    protected val backend = FakeTripBackend()
    protected var explore = emptyList<PublicTrip>()

    /** Explore's second page, after the cursor `page-2`. */
    protected var exploreMore = emptyList<PublicTrip>()
    protected var likeRefused = false
    protected var reportRefused = false
    protected val reported = mutableListOf<Pair<ReportTarget, String>>()

    /** The filter of every first page of Explore asked for. */
    protected val filters = mutableListOf<ExploreFilter>()

    protected val social = object : SocialRepository by FakeSocialRepository() {
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

        override suspend fun setHot(placeId: String, hot: Boolean): AppResult<HotState, DataError.Network> =
            if (likeRefused) AppResult.Error(DataError.Network.NoInternet) else HotState(hot, 99).asSuccess()
    }

    /** Counts the searches that reach the server. */
    protected class CountingUsers(private val fake: FakeUserRepository = FakeUserRepository()) : UserRepository by fake {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String): AppResult<List<UserSummary>, DataError.Network> {
            queries += query
            return fake.search(query)
        }
    }

    protected val users = CountingUsers()

    protected val location = GetCurrentLocationUseCase(
        object : LocationRepository {
            override fun getLocationStream() = flowOf(Coordinates.Paris.asSuccess())
        },
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** One of my trips, as if Explore listed it to someone else. */
    protected suspend fun publicTrip(): PublicTrip {
        val details: TripDetails = (backend.createTrip(NewTrip("Vienna", LocalDate(2026, 6, 1), LocalDate(2026, 6, 2))) as AppResult.Success).data
        return PublicTrip(details.trip.copy(role = TripRole.VIEWER), likeCount = 4, likedByMe = false, saveCount = 0)
    }

    /** Anna's public trip. */
    protected fun othersTrip(): PublicTrip {
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

    protected fun commonsImage(id: String) = Photo(
        id = id,
        owner = PhotoOwner.Place("place"),
        status = PhotoStatus.Ready,
        thumbUrl = null,
        displayUrl = null,
        width = null,
        height = null,
        likeCount = 0,
        likedByMe = false,
        uploader = null,
        credit = ImageCredit(author = "A", title = null, license = "CC BY-SA 4.0", licenseUrl = null, sourceUrl = "https://commons.wikimedia.org/wiki/File:A.jpg"),
    )
}
