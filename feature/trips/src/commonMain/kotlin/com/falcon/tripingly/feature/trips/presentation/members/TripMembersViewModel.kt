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
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.trip.JoinRequest
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_add
import com.falcon.tripingly.feature.trips.generated.resources.members_error_answer_request
import com.falcon.tripingly.feature.trips.generated.resources.members_error_create_invite
import com.falcon.tripingly.feature.trips.generated.resources.members_error_load
import com.falcon.tripingly.feature.trips.generated.resources.members_error_load_invites
import com.falcon.tripingly.feature.trips.generated.resources.members_error_load_requests
import com.falcon.tripingly.feature.trips.generated.resources.members_error_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_error_revoke_invite
import com.falcon.tripingly.feature.trips.generated.resources.members_error_some_not_added
import com.falcon.tripingly.feature.trips.generated.resources.members_error_user_not_found
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Who is in a trip; the owner adds editors (by username, or several friends at once) and removes
 * them, answers requests to join, and manages invite links.
 */
class TripMembersViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val inviteRepository: TripInviteRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    val uiState: StateFlow<State> = combine(tripRepository.observeTrip(tripId), ui) { details, ui ->
        State(
            tripName = details?.trip?.name.orEmpty(),
            canManage = details?.trip?.role?.canManage == true,
            members = details?.members.orEmpty().toImmutableList(),
            friends = ui.friends
                .filterNot { friend -> details?.members.orEmpty().any { it.user.username == friend.username } }
                .toImmutableList(),
            pickedFriends = ui.pickedFriends,
            joinRequests = ui.joinRequests,
            answeringRequestIds = ui.answeringRequestIds,
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
            is Action.OnFriendToggled -> ui.update {
                val username = action.friend.username
                val picked = if (username in it.pickedFriends) it.pickedFriends - username else it.pickedFriends + username
                it.copy(pickedFriends = picked.toImmutableSet(), message = null)
            }
            Action.OnAddPickedFriends -> addPickedFriends()
            is Action.OnRemoveMember -> run(Res.string.members_error_remove) { tripRepository.removeMember(tripId, action.userId) }
            Action.OnCreateInvite -> createInvite()
            is Action.OnShareInvite -> sendEvent(Event.ShareInvite(uiState.value.tripName, action.invite.url))
            is Action.OnRevokeInvite -> revokeInvite(action.inviteId)
            is Action.OnAcceptJoinRequest -> answerJoinRequest(action.request.id) { tripRepository.acceptJoinRequest(tripId, it) }
            is Action.OnDeclineJoinRequest -> answerJoinRequest(action.request.id) { tripRepository.declineJoinRequest(tripId, it) }
            Action.OnDismissMessage -> ui.update { it.copy(message = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            ui.update { it.copy(isLoading = false, message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_load)) }
            if (result is AppResult.Success && result.data.trip.role.canManage) {
                if (result.data.pendingJoinRequestCount > 0) loadJoinRequests()
                loadFriends()
                loadInvites()
            }
        }
    }

    private suspend fun loadFriends() {
        val result = userRepository.friends()
        if (result is AppResult.Success) ui.update { it.copy(friends = result.data.toImmutableList()) }
    }

    private suspend fun loadJoinRequests() {
        when (val result = tripRepository.joinRequests(tripId)) {
            is AppResult.Success -> ui.update { it.copy(joinRequests = result.data.items.toImmutableList()) }
            is AppResult.Error -> ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_load_requests)) }
        }
    }

    /** Accepting or declining; the request leaves the list once the server agreed (or it was gone already). */
    private fun answerJoinRequest(requestId: String, answer: suspend (String) -> AppResult<*, DataError.Network>) {
        if (requestId in ui.value.answeringRequestIds) return
        ui.update { it.copy(answeringRequestIds = (it.answeringRequestIds + requestId).toImmutableSet(), message = null) }
        viewModelScope.launch {
            val result = answer(requestId)
            val isGone = result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))
            ui.update { state ->
                state.copy(
                    answeringRequestIds = (state.answeringRequestIds - requestId).toImmutableSet(),
                    joinRequests = if (isGone) state.joinRequests.filterNot { it.id == requestId }.toImmutableList() else state.joinRequests,
                    // A full trip (`LIMIT_REACHED`) comes with the server's message.
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_answer_request),
                )
            }
        }
    }

    private suspend fun loadInvites() {
        when (val result = inviteRepository.invites(tripId)) {
            is AppResult.Success -> ui.update { it.copy(invites = result.data.toImmutableList()) }
            is AppResult.Error -> ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_load_invites)) }
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

    private fun addPickedFriends() {
        val usernames = ui.value.pickedFriends.toList()
        if (usernames.isEmpty() || ui.value.isAdding) return
        ui.update { it.copy(isAdding = true, message = null) }
        viewModelScope.launch {
            val result = tripRepository.addMembers(tripId, usernames)
            ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(isAdding = false, pickedFriends = persistentSetOf())
                    is AppResult.Error -> it.copy(isAdding = false, message = addFriendsError(result.error))
                }
            }
        }
    }

    private fun addFriendsError(error: DataError.Network): UiText =
        if (error.hasCode(TripErrorCodes.VALIDATION_FAILED)) {
            UiText.Resource(Res.string.members_error_some_not_added)
        } else {
            error.toUiText(Res.string.members_error_add)
        }

    private fun addMemberError(error: DataError.Network): UiText =
        if (error.hasCode(TripErrorCodes.NOT_FOUND)) UiText.Resource(Res.string.members_error_user_not_found) else error.toUiText(Res.string.members_error_add)

    private fun createInvite() {
        if (ui.value.isCreatingInvite) return
        ui.update { it.copy(isCreatingInvite = true, message = null) }
        viewModelScope.launch {
            when (val result = inviteRepository.createInvite(tripId)) {
                is AppResult.Success -> {
                    ui.update { it.copy(isCreatingInvite = false, invites = (it.invites + result.data).toImmutableList()) }
                    sendEvent(Event.ShareInvite(uiState.value.tripName, result.data.url))
                }
                is AppResult.Error -> ui.update { it.copy(isCreatingInvite = false, message = result.error.toUiText(Res.string.members_error_create_invite)) }
            }
        }
    }

    private fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            val result = inviteRepository.revokeInvite(tripId, inviteId)
            if (result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))) {
                ui.update { state -> state.copy(invites = state.invites.filterNot { it.id == inviteId }.toImmutableList()) }
            } else if (result is AppResult.Error) {
                ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_revoke_invite)) }
            }
        }
    }

    private fun run(failed: StringResource, change: suspend () -> AppResult<*, DataError.Network>) {
        viewModelScope.launch {
            val result = change()
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(failed)) }
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    private data class UiState(
        val joinRequests: ImmutableList<JoinRequest> = persistentListOf(),
        val answeringRequestIds: ImmutableSet<String> = persistentSetOf(),
        val invites: ImmutableList<TripInvite> = persistentListOf(),
        val friends: ImmutableList<UserSummary> = persistentListOf(),
        val pickedFriends: ImmutableSet<String> = persistentSetOf(),
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
        /** The owner's friends who aren't in the trip yet. */
        val friends: ImmutableList<UserSummary> = persistentListOf(),
        /** Usernames of the friends picked to add together. */
        val pickedFriends: ImmutableSet<String> = persistentSetOf(),
        /** The owner's: people asking to join, newest first. */
        val joinRequests: ImmutableList<JoinRequest> = persistentListOf(),
        /** Requests whose answer is waiting for the server. */
        val answeringRequestIds: ImmutableSet<String> = persistentSetOf(),
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
        data class OnFriendToggled(val friend: UserSummary) : Action
        data object OnAddPickedFriends : Action
        data class OnRemoveMember(val userId: String) : Action
        data object OnCreateInvite : Action
        data class OnShareInvite(val invite: TripInvite) : Action
        data class OnRevokeInvite(val inviteId: String) : Action
        data class OnAcceptJoinRequest(val request: JoinRequest) : Action
        data class OnDeclineJoinRequest(val request: JoinRequest) : Action
        data object OnDismissMessage : Action
    }
}
