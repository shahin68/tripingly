package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripInviteRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_user_not_found
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who is in a trip; the owner adds and removes editors and manages invite links. */
class TripMembersViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val inviteRepository: TripInviteRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    val uiState: StateFlow<State> = combine(tripRepository.observeTrip(tripId), ui) { details, ui ->
        State(
            tripName = details?.trip?.name.orEmpty(),
            canManage = details?.trip?.role?.canManage == true,
            members = details?.members.orEmpty().toImmutableList(),
            invites = ui.invites,
            username = ui.username,
            isLoading = ui.isLoading && (details == null || details.days.isEmpty()),
            isAdding = ui.isAdding,
            isCreatingInvite = ui.isCreatingInvite,
            message = ui.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnUsernameChanged -> ui.update { it.copy(username = action.username, message = null) }
            Action.OnAddMember -> addMember()
            is Action.OnRemoveMember -> run { tripRepository.removeMember(tripId, action.userId) }
            Action.OnCreateInvite -> createInvite()
            is Action.OnShareInvite -> sendEvent(Event.ShareInvite(uiState.value.tripName, action.invite.url))
            is Action.OnRevokeInvite -> revokeInvite(action.inviteId)
            Action.OnDismissMessage -> ui.update { it.copy(message = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            ui.update { it.copy(isLoading = false, message = (result as? AppResult.Error)?.error?.toUiText()) }
            if (result is AppResult.Success && result.data.trip.role.canManage) loadInvites()
        }
    }

    private suspend fun loadInvites() {
        when (val result = inviteRepository.invites(tripId)) {
            is AppResult.Success -> ui.update { it.copy(invites = result.data.toImmutableList()) }
            is AppResult.Error -> ui.update { it.copy(message = result.error.toUiText()) }
        }
    }

    private fun addMember() {
        val username = ui.value.username.trim()
        if (username.isEmpty() || ui.value.isAdding) return
        ui.update { it.copy(isAdding = true, message = null) }
        viewModelScope.launch {
            val result = tripRepository.addMember(tripId, username)
            ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(isAdding = false, username = "")
                    is AppResult.Error -> it.copy(isAdding = false, message = addMemberError(result.error))
                }
            }
        }
    }

    private fun addMemberError(error: DataError.Network): UiText =
        if (error.hasCode(TripErrorCodes.NOT_FOUND)) UiText.Resource(Res.string.members_error_user_not_found) else error.toUiText()

    private fun createInvite() {
        if (ui.value.isCreatingInvite) return
        ui.update { it.copy(isCreatingInvite = true, message = null) }
        viewModelScope.launch {
            when (val result = inviteRepository.createInvite(tripId)) {
                is AppResult.Success -> {
                    ui.update { it.copy(isCreatingInvite = false, invites = (it.invites + result.data).toImmutableList()) }
                    sendEvent(Event.ShareInvite(uiState.value.tripName, result.data.url))
                }
                is AppResult.Error -> ui.update { it.copy(isCreatingInvite = false, message = result.error.toUiText()) }
            }
        }
    }

    private fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            val result = inviteRepository.revokeInvite(tripId, inviteId)
            if (result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))) {
                ui.update { state -> state.copy(invites = state.invites.filterNot { it.id == inviteId }.toImmutableList()) }
            } else if (result is AppResult.Error) {
                ui.update { it.copy(message = result.error.toUiText()) }
            }
        }
    }

    private fun run(change: suspend () -> AppResult<*, DataError.Network>) {
        viewModelScope.launch {
            val result = change()
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText()) }
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    private data class UiState(
        val invites: ImmutableList<TripInvite> = persistentListOf(),
        val username: String = "",
        val isLoading: Boolean = true,
        val isAdding: Boolean = false,
        val isCreatingInvite: Boolean = false,
        val message: UiText? = null,
    )

    @Immutable
    data class State(
        val tripName: String = "",
        val canManage: Boolean = false,
        val members: ImmutableList<TripMember> = persistentListOf(),
        val invites: ImmutableList<TripInvite> = persistentListOf(),
        val username: String = "",
        val isLoading: Boolean = true,
        val isAdding: Boolean = false,
        val isCreatingInvite: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Event {
        data class ShareInvite(val tripName: String, val url: String) : Event
    }

    sealed interface Action {
        data class OnUsernameChanged(val username: String) : Action
        data object OnAddMember : Action
        data class OnRemoveMember(val userId: String) : Action
        data object OnCreateInvite : Action
        data class OnShareInvite(val invite: TripInvite) : Action
        data class OnRevokeInvite(val inviteId: String) : Action
        data object OnDismissMessage : Action
    }
}
