package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripMembersViewModelTest {

    private val backend = FakeTripBackend()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(): TripMembersViewModel {
        val trip = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data
        return TripMembersViewModel(trip.trip.id, backend, backend, FakeUserRepository()).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.onAction(Action.Opened)
        }
    }

    @Test
    fun pickedFriends_areAddedInOneRequest_andLeaveTheFriendsRow() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val (anna, jonas) = viewModel.uiState.value.add.friends!!

        viewModel.onAction(Action.Add.FriendToggled(anna))
        viewModel.onAction(Action.Add.FriendToggled(jonas))
        viewModel.onAction(Action.Add.FriendToggled(jonas))
        viewModel.onAction(Action.Add.FriendToggled(jonas))
        viewModel.onAction(Action.Add.PickedFriends)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("anna", "jonas.k"), state.members.drop(1).map { it.user.username })
        assertTrue(state.add.pickedFriends.isEmpty())
        assertEquals(listOf("lena"), state.add.friends?.map { it.username })
    }

    @Test
    fun pickedFriend_whoJoinedAnotherWay_isNotSentAgain() = runTest {
        val sent = mutableListOf<List<String>>()
        val repository = object : TripRepository by backend {
            override suspend fun addMembers(tripId: String, usernames: List<String>): AppResult<TripDetails, DataError.Network> {
                sent += usernames
                return backend.addMembers(tripId, usernames)
            }
        }
        val trip = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data
        val viewModel = TripMembersViewModel(trip.trip.id, repository, backend, FakeUserRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onAction(Action.Opened)
        advanceUntilIdle()
        val (anna, jonas) = viewModel.uiState.value.add.friends!!

        viewModel.onAction(Action.Add.FriendToggled(anna))
        viewModel.onAction(Action.Add.FriendToggled(jonas))
        backend.addMembers(trip.trip.id, listOf("anna"))
        advanceUntilIdle()
        assertEquals(setOf("jonas.k"), viewModel.uiState.value.add.pickedFriends)
        viewModel.onAction(Action.Add.PickedFriends)
        advanceUntilIdle()

        assertEquals(listOf(listOf("jonas.k")), sent)
        assertEquals(listOf("anna", "jonas.k"), viewModel.uiState.value.members.drop(1).map { it.user.username })
    }

    @Test
    fun joinRequests_acceptOneDeclineOne_editorsAndListFollow() = runTest {
        val trip = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data.trip
        val mia = JoinRequest("r1", trip.id, UserSummary("u-mia", "mia", "Mia"), Instant.parse("2026-10-16T10:00:00Z"))
        val tom = JoinRequest("r2", trip.id, UserSummary("u-tom", "tom", "Tom"), Instant.parse("2026-10-16T11:00:00Z"))
        backend.seedJoinRequest(mia)
        backend.seedJoinRequest(tom)
        val viewModel = TripMembersViewModel(trip.id, backend, backend, FakeUserRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onAction(Action.Opened)
        advanceUntilIdle()
        assertEquals(listOf("tom", "mia"), viewModel.uiState.value.joinRequests.requests.map { it.user.username })

        viewModel.onAction(Action.JoinRequests.Accept(mia))
        viewModel.onAction(Action.JoinRequests.Decline(tom))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.joinRequests.requests.isEmpty())
        assertEquals(listOf("mia"), state.members.filter { it.role == TripRole.EDITOR }.map { it.user.username })
        assertEquals(0, backend.observeTrip(trip.id).first()?.pendingJoinRequestCount)
    }

    @Test
    fun reopening_reloadsJoinRequests_andForgetsTheLastVisit() = runTest {
        val trip = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data.trip
        val viewModel = TripMembersViewModel(trip.id, backend, backend, FakeUserRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onAction(Action.Opened)
        advanceUntilIdle()
        viewModel.onAction(Action.Add.FriendToggled(viewModel.uiState.value.add.friends!!.first()))
        backend.seedJoinRequest(JoinRequest("r1", trip.id, UserSummary("u-mia", "mia", "Mia"), Instant.parse("2026-10-16T10:00:00Z")))

        viewModel.onAction(Action.Opened)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("mia"), state.joinRequests.requests.map { it.user.username })
        assertTrue(state.add.pickedFriends.isEmpty())
    }

    @Test
    fun removeMember_removesTheEditor() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.Add.FriendToggled(viewModel.uiState.value.add.friends!!.first()))
        viewModel.onAction(Action.Add.PickedFriends)
        advanceUntilIdle()

        viewModel.onAction(Action.RemoveMember(viewModel.uiState.value.members[1].user.id))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.members.size)
    }

    @Test
    fun createInvite_listsIt_andOpensTheShareSheet() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.Invites.Create)
        advanceUntilIdle()

        val invite = viewModel.uiState.value.invites.invites.single()
        assertEquals(Event.ShareInvite("Paris", invite.url), viewModel.events.first())

        viewModel.onAction(Action.Invites.Revoke(invite.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.invites.invites.isEmpty())
    }
}
