package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.FollowChange
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Action
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Event
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ProfileViewModelTest {

    private var followRefused = false

    private val liveFollows = MutableSharedFlow<FollowChange>()

    private val users = object : UserRepository by FakeUserRepository() {
        val profileCalls = mutableListOf<String>()
        private val fake = FakeUserRepository()
        override val followChanges: Flow<FollowChange> = liveFollows
        override suspend fun profile(username: String, cursor: String?): AppResult<Profile, DataError.Network> {
            profileCalls += username
            return fake.profile(username, cursor)
        }

        override suspend fun setFollowing(userId: String, following: Boolean): AppResult<Unit, DataError.Network> =
            if (followRefused) AppResult.Error(DataError.Network.NoInternet) else AppResult.Success(Unit)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(username: String) = ProfileViewModel(
        username = username,
        userRepository = users,
        socialRepository = FakeSocialRepository(),
        tripRepository = FakeTripBackend(),
        sessionRepository = FakeAccountBackend(today = { LocalDate(2026, 10, 11) }),
    )

    @Test
    fun unfollowing_showsAtOnce() = runTest {
        // The fake user "marton" is followed but doesn't follow back.
        val viewModel = viewModel("marton")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.profile!!.isFollowing)

        viewModel.onAction(Action.Person.ToggleFollow)

        val profile = viewModel.uiState.value.profile!!
        assertFalse(profile.isFollowing)
        assertEquals(0, profile.followerCount)
    }

    @Test
    fun following_refused_putsItBack() = runTest {
        followRefused = true
        val viewModel = viewModel("julia_t")
        advanceUntilIdle()

        viewModel.onAction(Action.Person.ToggleFollow)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.profile!!.isFollowing)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun blocking_asksFirst_thenCloses() = runTest {
        val viewModel = viewModel("anna")
        advanceUntilIdle()

        viewModel.onAction(Action.Person.Block)
        assertTrue(viewModel.uiState.value.isConfirmingBlock)
        viewModel.onAction(Action.Person.ConfirmBlock)
        advanceUntilIdle()

        assertEquals(Event.Close, viewModel.events.first())
    }

    @Test
    fun theirFollowChangingLive_reloadsTheProfile() = runTest {
        val viewModel = viewModel("julia_t")
        advanceUntilIdle()
        val julia = viewModel.uiState.value.profile!!.user

        liveFollows.emit(FollowChange(followerId = julia.id, followeeId = ME, following = false))
        advanceUntilIdle()

        assertEquals(listOf("julia_t", "julia_t"), users.profileCalls)
    }

    @Test
    fun myOwnFollowTapHere_doesNotReload() = runTest {
        val viewModel = viewModel("julia_t")
        advanceUntilIdle()
        val julia = viewModel.uiState.value.profile!!.user

        viewModel.onAction(Action.Person.ToggleFollow)
        advanceUntilIdle()
        liveFollows.emit(FollowChange(followerId = ME, followeeId = julia.id, following = true))
        advanceUntilIdle()

        assertEquals(listOf("julia_t"), users.profileCalls)
    }

    @Test
    fun unknownUser_isNotFound() = runTest {
        val viewModel = viewModel("nobody")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun peopleList_loadsTheList() = runTest {
        val viewModel = PeopleViewModel(PeopleList.Followers, FakeUserRepository())
        advanceUntilIdle()

        assertEquals(listOf("anna", "jonas.k", "lena", "julia_t"), viewModel.uiState.value.people.map { it.username })
    }

    @Test
    fun fastFollowTaps_theLatestWins_andARefusalPutsBackWhatTheServerConfirmed() = runTest {
        val viewModel = viewModel("marton")
        advanceUntilIdle()

        followRefused = true
        repeat(3) { viewModel.onAction(Action.Person.ToggleFollow) }
        assertFalse(viewModel.uiState.value.profile!!.isFollowing, "the third tap shows at once")
        advanceUntilIdle()

        val profile = viewModel.uiState.value.profile!!
        assertTrue(profile.isFollowing)
        assertEquals(1, profile.followerCount)
    }

    @Test
    fun reportingThePerson_closesTheDialogOnceSent() = runTest {
        val viewModel = viewModel("anna")
        advanceUntilIdle()

        viewModel.onAction(Action.Person.Report)
        viewModel.onAction(Action.Report.Send(ReportReason.Spam))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.report)
        assertTrue(viewModel.uiState.value.message is UiText.Note)
    }

    @Test
    fun peopleList_dropsSomeoneBlocked_atOnce() = runTest {
        val repository = FakeUserRepository()
        val viewModel = PeopleViewModel(PeopleList.Followers, repository)
        advanceUntilIdle()
        val anna = viewModel.uiState.value.people.first()

        repository.setBlocked(anna.id, blocked = true)
        advanceUntilIdle()

        assertEquals(listOf("jonas.k", "lena", "julia_t"), viewModel.uiState.value.people.map { it.username })
    }

    @Test
    fun peopleList_aFollowChangeDuringALoad_loadsAgain() = runTest {
        val firstAnswer = CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : UserRepository by FakeUserRepository() {
            override val followChanges: Flow<FollowChange> = liveFollows
            override suspend fun people(list: PeopleList, cursor: String?): AppResult<Page<UserSummary>, DataError.Network> {
                val call = ++calls
                if (call == 1) firstAnswer.await()
                return AppResult.Success(Page(listOf(UserSummary("u$call", "before$call", "Before")), nextCursor = null))
            }
        }
        val viewModel = PeopleViewModel(PeopleList.Following, repository)
        advanceUntilIdle()

        liveFollows.emit(FollowChange(ME, "u9", following = true))
        firstAnswer.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("before2"), viewModel.uiState.value.people.map { it.username })
    }

    private companion object {
        const val ME = "00000000-0000-4000-8000-000000000001"
    }
}
