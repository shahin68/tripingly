package com.falcon.tripingly.feature.trips

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_add
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripMembersViewModelTest {

    private val backend = FakeTripBackend()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(tripId: String): TripMembersViewModel =
        TripMembersViewModel(tripId, backend, backend, FakeUserRepository()).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.onAction(Action.Opened)
        }

    private suspend fun newTrip(): String = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data.trip.id

    private fun request(tripId: String, user: UserSummary, id: String = "r-${user.username}") =
        JoinRequest(id, tripId, user, Instant.parse("2026-10-16T10:00:00Z"))

    @Test
    fun everything_loadsTogether_behindOneSkeleton() = runTest {
        val viewModel = viewModel(newTrip())
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(listOf("anna", "jonas.k", "lena"), state.friends?.map { it.username })
    }

    @Test
    fun aFriendAskingToJoin_isOnlyARequest_notAFriendToAdd() = runTest {
        val tripId = newTrip()
        val anna = UserSummary("00000000-0000-4000-8000-000000000101", "anna", "Anna")
        backend.seedJoinRequest(request(tripId, anna))
        val viewModel = viewModel(tripId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("anna"), state.joinRequests.requests.map { it.user.username })
        assertEquals(listOf("jonas.k", "lena"), state.friends?.map { it.username })
        assertTrue("anna" in state.asking)
    }

    @Test
    fun tappingAFriend_addsThemAtOnce_andUndoTakesThemOffAgain() = runTest {
        val tripId = newTrip()
        val viewModel = viewModel(tripId)
        advanceUntilIdle()
        val anna = viewModel.uiState.value.friends!!.first()

        viewModel.onAction(Action.AddPerson(anna))

        // On the trip before the server answers, out of the friends, with Undo offered.
        var state = viewModel.uiState.value
        assertEquals(listOf("anna"), state.members.drop(1).map { it.user.username })
        assertEquals(listOf("jonas.k", "lena"), state.friends?.map { it.username })
        assertEquals(anna, state.added)
        advanceUntilIdle()
        assertEquals(listOf("anna"), backend.observeTrip(tripId).first()!!.members.drop(1).map { it.user.username })

        viewModel.onAction(Action.DismissAdded)
        viewModel.onAction(Action.Undo(anna))
        state = viewModel.uiState.value
        assertEquals(1, state.members.size)
        assertEquals(listOf("anna", "jonas.k", "lena"), state.friends?.map { it.username })
        advanceUntilIdle()
        assertEquals(1, backend.observeTrip(tripId).first()!!.members.size)
    }

    @Test
    fun undo_whileTheAddIsOnItsWay_removesThemOnceItLands() = runTest {
        val tripId = newTrip()
        val viewModel = viewModel(tripId)
        advanceUntilIdle()
        val anna = viewModel.uiState.value.friends!!.first()

        viewModel.onAction(Action.AddPerson(anna))
        viewModel.onAction(Action.Undo(anna))
        assertEquals(1, viewModel.uiState.value.members.size)
        advanceUntilIdle()

        assertEquals(1, backend.observeTrip(tripId).first()!!.members.size)
        assertEquals(1, viewModel.uiState.value.members.size)
    }

    @Test
    fun anAddTheServerRefuses_leavesTheList_andSaysSo() = runTest {
        val viewModel = viewModel(newTrip())
        advanceUntilIdle()

        viewModel.onAction(Action.AddPerson(UserSummary("u-x", "unknown", "Nobody")))
        assertEquals(2, viewModel.uiState.value.members.size)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.members.size)
        assertNull(state.added)
        assertEquals(Res.string.members_error_add, assertIs<UiText.Resource>(state.message).resource)
    }

    @Test
    fun joinRequests_acceptOneDeclineOne_editorsAndListFollow() = runTest {
        val tripId = newTrip()
        val mia = request(tripId, UserSummary("u-mia", "mia", "Mia"))
        val tom = request(tripId, UserSummary("u-tom", "tom", "Tom"))
        backend.seedJoinRequest(mia)
        backend.seedJoinRequest(tom)
        val viewModel = viewModel(tripId)
        advanceUntilIdle()
        assertEquals(listOf("tom", "mia"), viewModel.uiState.value.joinRequests.requests.map { it.user.username })

        viewModel.onAction(Action.JoinRequests.Accept(mia))
        viewModel.onAction(Action.JoinRequests.Decline(tom))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.joinRequests.requests.isEmpty())
        assertEquals(listOf("mia"), state.members.filter { it.role == TripRole.EDITOR }.map { it.user.username })
        assertEquals(0, backend.observeTrip(tripId).first()?.pendingJoinRequestCount)
        // The accept the server took in is felt.
        assertEquals(Event.Accepted, viewModel.events.first())
    }

    @Test
    fun removeMember_takesThemOffAtOnce() = runTest {
        val tripId = newTrip()
        backend.addMember(tripId, "anna")
        val viewModel = viewModel(tripId)
        advanceUntilIdle()

        viewModel.onAction(Action.RemoveMember(viewModel.uiState.value.members[1].user))
        assertEquals(1, viewModel.uiState.value.members.size)
        advanceUntilIdle()

        assertEquals(1, backend.observeTrip(tripId).first()!!.members.size)
        assertEquals(1, viewModel.uiState.value.members.size)
    }

    @Test
    fun shareInvite_makesALinkOnce_thenSharesTheSameOne() = runTest {
        val viewModel = viewModel(newTrip())
        advanceUntilIdle()

        viewModel.onAction(Action.ShareInvite)
        advanceUntilIdle()
        val first = assertIs<Event.ShareInvite>(viewModel.events.first())
        viewModel.onAction(Action.ShareInvite)
        advanceUntilIdle()

        assertEquals(first, viewModel.events.first())
        assertEquals("Paris", first.tripName)
        assertEquals(1, viewModel.uiState.value.invites.size)
    }

    @Test
    fun addPeople_findsAnyoneOnceTypingPauses() = runTest {
        val viewModel = viewModel(newTrip())
        advanceUntilIdle()

        viewModel.onAction(Action.Search.Open)
        runCurrent()
        viewModel.uiState.value.search!!.query.setTextAndPlaceCursorAtEnd("@mar")
        runCurrent()
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.search!!.results.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("marton"), viewModel.uiState.value.search!!.results.map { it.username })
        viewModel.onAction(Action.Search.Close)
        assertNull(viewModel.uiState.value.search)
    }

    @Test
    fun reopening_reloads_andForgetsTheLastVisit() = runTest {
        val tripId = newTrip()
        val viewModel = viewModel(tripId)
        advanceUntilIdle()
        viewModel.onAction(Action.Search.Open)
        backend.seedJoinRequest(request(tripId, UserSummary("u-mia", "mia", "Mia")))

        viewModel.onAction(Action.Opened)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("mia"), state.joinRequests.requests.map { it.user.username })
        assertNull(state.search)
    }

    @Test
    fun anEditor_seesOnlyWhoIsOnTheTrip() = runTest {
        val tripId = newTrip()
        val details = backend.observeTrip(tripId).first()!!
        backend.seed(details.copy(trip = details.trip.copy(role = TripRole.EDITOR)))
        val viewModel = viewModel(tripId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.canManage)
        assertNull(state.friends)
        assertTrue(state.joinRequests.requests.isEmpty())
    }
}
