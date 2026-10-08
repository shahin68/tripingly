package com.falcon.tripingly.feature.trips

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_user_not_found
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripMembersViewModelTest {

    private val backend = FakeTripBackend()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(): TripMembersViewModel {
        val trip = (backend.createTrip(NewTrip("Paris", null, null)) as AppResult.Success).data
        return TripMembersViewModel(trip.trip.id, backend, backend).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
        }
    }

    @Test
    fun addMember_byUsername_addsAnEditor_andClearsTheField() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnUsernameChanged("@Anna"))
        viewModel.onAction(Action.OnAddMember)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canManage)
        assertEquals(listOf(TripRole.OWNER, TripRole.EDITOR), state.members.map { it.role })
        assertEquals("anna", state.members[1].user.username)
        assertEquals("", state.username)
    }

    @Test
    fun addMember_unknownUsername_saysNoOneHasIt() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnUsernameChanged("unknown"))
        viewModel.onAction(Action.OnAddMember)
        advanceUntilIdle()

        val message = assertIs<UiText.Resource>(viewModel.uiState.value.message)
        assertEquals(Res.string.members_error_user_not_found, message.resource)
        assertEquals("unknown", viewModel.uiState.value.username)
    }

    @Test
    fun removeMember_removesTheEditor() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(Action.OnUsernameChanged("anna"))
        viewModel.onAction(Action.OnAddMember)
        advanceUntilIdle()

        viewModel.onAction(Action.OnRemoveMember(viewModel.uiState.value.members[1].user.id))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.members.size)
    }

    @Test
    fun createInvite_listsIt_andOpensTheShareSheet() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(Action.OnCreateInvite)
        advanceUntilIdle()

        val invite = viewModel.uiState.value.invites.single()
        assertEquals(Event.ShareInvite("Paris", invite.url), viewModel.events.first())

        viewModel.onAction(Action.OnRevokeInvite(invite.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.invites.isEmpty())
    }
}
