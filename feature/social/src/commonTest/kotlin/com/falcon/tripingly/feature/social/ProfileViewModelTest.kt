package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Action
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val users = object : UserRepository by FakeUserRepository() {
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

        viewModel.onAction(Action.OnToggleFollow)

        val profile = viewModel.uiState.value.profile!!
        assertFalse(profile.isFollowing)
        assertEquals(0, profile.followerCount)
    }

    @Test
    fun following_refused_putsItBack() = runTest {
        followRefused = true
        val viewModel = viewModel("julia_t")
        advanceUntilIdle()

        viewModel.onAction(Action.OnToggleFollow)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.profile!!.isFollowing)
        assertTrue(viewModel.uiState.value.message != null)
    }

    @Test
    fun blocking_asksFirst_thenCloses() = runTest {
        val viewModel = viewModel("anna")
        advanceUntilIdle()

        viewModel.onAction(Action.OnBlock)
        assertTrue(viewModel.uiState.value.isConfirmingBlock)
        viewModel.onAction(Action.OnConfirmBlock)
        advanceUntilIdle()

        assertEquals(Event.Close, viewModel.events.first())
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
}
