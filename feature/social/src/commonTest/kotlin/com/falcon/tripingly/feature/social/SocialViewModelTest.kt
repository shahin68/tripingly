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
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_not_copyable
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel
import com.falcon.tripingly.feature.social.presentation.social.SocialViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModelTest {

    private val backend = FakeTripBackend()
    private var explore = emptyList<PublicTrip>()
    private var likeRefused = false

    private val social = object : SocialRepository by FakeSocialRepository() {
        override suspend fun explore(cursor: String?): AppResult<Page<PublicTrip>, DataError.Network> =
            Page(explore, nextCursor = null).asSuccess()

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

    private suspend fun publicTrip(): PublicTrip {
        val details: TripDetails = (backend.createTrip(NewTrip("Vienna", LocalDate(2026, 6, 1), LocalDate(2026, 6, 2))) as AppResult.Success).data
        return PublicTrip(details.trip.copy(role = TripRole.VIEWER), likeCount = 4, likedByMe = false, copyCount = 0)
    }

    @Test
    fun likingATrip_showsAtOnce_andKeepsTheServersCount() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnLikeTrip(viewModel.uiState.value.explore.single()))
        assertTrue(viewModel.uiState.value.explore.single().likedByMe)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.explore.single().likeCount)
    }

    @Test
    fun likingATrip_refused_putsItBack() = runTest {
        explore = listOf(publicTrip())
        likeRefused = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnLikeTrip(viewModel.uiState.value.explore.single()))
        advanceUntilIdle()

        val trip = viewModel.uiState.value.explore.single()
        assertFalse(trip.likedByMe)
        assertEquals(4, trip.likeCount)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun peopleSearch_asksOnceTypingPauses() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        listOf("a", "an", "ann").forEach {
            viewModel.onAction(Action.OnQueryChanged(it))
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("ann"), users.queries)
        assertEquals("anna", viewModel.uiState.value.people.single().username)
    }

    @Test
    fun copyingMyOwnTrip_saysItCantBeAdded() = runTest {
        explore = listOf(publicTrip())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnCopyTrip(viewModel.uiState.value.explore.single()))
        advanceUntilIdle()

        assertEquals(UiText.Resource(Res.string.social_error_not_copyable), viewModel.uiState.value.message)
    }
}
