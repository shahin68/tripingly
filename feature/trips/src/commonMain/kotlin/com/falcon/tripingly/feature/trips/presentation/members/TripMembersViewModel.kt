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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Who is in a trip; the owner adds friends as editors (only friends, Shahin 2026-10-29) and removes
 * them, answers requests to join, and manages invite links for everyone else.
 */
class TripMembersViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val inviteRepository: TripInviteRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** Everything but the trip itself, which comes from the cache and is filled in below. */
    private val ui = MutableStateFlow(State())

    val uiState: StateFlow<State> = combine(tripRepository.observeTrip(tripId), ui) { details, ui ->
        val members = details?.members.orEmpty().map { it.user.username }.toSet()
        ui.copy(
            tripName = details?.trip?.name.orEmpty(),
            canManage = details?.trip?.role?.canManage == true,
            members = details?.members.orEmpty().toImmutableList(),
            add = ui.add.copy(
                friends = ui.add.friends?.filterNot { it.username in members }?.toImmutableList(),
                pickedFriends = (ui.add.pickedFriends - members).toImmutableSet(),
            ),
            isLoading = ui.isLoading && (details == null || details.days.isEmpty()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            // The sheet's view model outlives the sheet, so each opening starts fresh.
            Action.Opened -> {
                ui.value = State()
                load()
            }
            is Action.RemoveMember -> run(Res.string.members_error_remove) { tripRepository.removeMember(tripId, action.userId) }
            Action.DismissMessage -> ui.update { it.copy(message = null) }
            is Action.Add -> onAddAction(action)
            is Action.JoinRequests -> when (action) {
                is Action.JoinRequests.Accept -> answerJoinRequest(action.request.id) { tripRepository.acceptJoinRequest(tripId, it) }
                is Action.JoinRequests.Decline -> answerJoinRequest(action.request.id) { tripRepository.declineJoinRequest(tripId, it) }
            }
            is Action.Invites -> when (action) {
                Action.Invites.Create -> createInvite()
                is Action.Invites.Share -> sendEvent(Event.ShareInvite(uiState.value.tripName, action.invite.url))
                is Action.Invites.Revoke -> revokeInvite(action.inviteId)
            }
        }
    }

    private fun onAddAction(action: Action.Add) {
        when (action) {
            is Action.Add.FriendToggled -> ui.update {
                val username = action.friend.username
                val picked = it.add.pickedFriends.let { picked -> if (username in picked) picked - username else picked + username }
                it.copy(add = it.add.copy(pickedFriends = picked.toImmutableSet()), message = null)
            }
            Action.Add.PickedFriends -> addPickedFriends()
        }
    }

    private fun updateAdd(change: (AddState) -> AddState) = ui.update { it.copy(add = change(it.add)) }

    private fun updateRequests(change: (JoinRequestsState) -> JoinRequestsState) = ui.update { it.copy(joinRequests = change(it.joinRequests)) }

    private fun updateInvites(change: (InvitesState) -> InvitesState) = ui.update { it.copy(invites = change(it.invites)) }

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
        if (result is AppResult.Success) updateAdd { it.copy(friends = result.data.toImmutableList()) }
    }

    private suspend fun loadJoinRequests() {
        when (val result = tripRepository.joinRequests(tripId)) {
            is AppResult.Success -> updateRequests { it.copy(requests = result.data.items.toImmutableList()) }
            is AppResult.Error -> ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_load_requests)) }
        }
    }

    /** Accepting or declining; the request leaves the list once the server agreed (or it was gone already). */
    private fun answerJoinRequest(requestId: String, answer: suspend (String) -> AppResult<*, DataError.Network>) {
        if (requestId in ui.value.joinRequests.answeringIds) return
        ui.update { it.copy(joinRequests = it.joinRequests.copy(answeringIds = (it.joinRequests.answeringIds + requestId).toImmutableSet()), message = null) }
        viewModelScope.launch {
            val result = answer(requestId)
            val isGone = result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))
            ui.update { state ->
                val requests = state.joinRequests
                state.copy(
                    joinRequests = JoinRequestsState(
                        requests = if (isGone) requests.requests.filterNot { it.id == requestId }.toImmutableList() else requests.requests,
                        answeringIds = (requests.answeringIds - requestId).toImmutableSet(),
                    ),
                    // A full trip (`LIMIT_REACHED`) comes with the server's message.
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_answer_request),
                )
            }
        }
    }

    private suspend fun loadInvites() {
        when (val result = inviteRepository.invites(tripId)) {
            is AppResult.Success -> updateInvites { it.copy(invites = result.data.toImmutableList()) }
            is AppResult.Error -> ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_load_invites)) }
        }
    }

    /** Friends who became members another way meanwhile aren't sent again. */
    private fun addPickedFriends() {
        if (ui.value.add.pickedFriends.isEmpty() || ui.value.add.isAdding) return
        ui.update { it.copy(add = it.add.copy(isAdding = true), message = null) }
        viewModelScope.launch {
            val members = tripRepository.observeTrip(tripId).first()?.members.orEmpty().map { it.user.username }.toSet()
            val usernames = (ui.value.add.pickedFriends - members).toList()
            val result = if (usernames.isEmpty()) null else tripRepository.addMembers(tripId, usernames)
            if (result is AppResult.Error) {
                ui.update { it.copy(add = it.add.copy(isAdding = false), message = addFriendsError(result.error)) }
            } else {
                updateAdd { it.copy(isAdding = false, pickedFriends = persistentSetOf()) }
            }
        }
    }

    private fun addFriendsError(error: DataError.Network): UiText =
        if (error.hasCode(TripErrorCodes.VALIDATION_FAILED)) {
            UiText.Resource(Res.string.members_error_some_not_added)
        } else {
            error.toUiText(Res.string.members_error_add)
        }

    private fun createInvite() {
        if (ui.value.invites.isCreating) return
        ui.update { it.copy(invites = it.invites.copy(isCreating = true), message = null) }
        viewModelScope.launch {
            when (val result = inviteRepository.createInvite(tripId)) {
                is AppResult.Success -> {
                    updateInvites { it.copy(invites = (it.invites + result.data).toImmutableList(), isCreating = false) }
                    sendEvent(Event.ShareInvite(uiState.value.tripName, result.data.url))
                }
                is AppResult.Error -> ui.update {
                    it.copy(invites = it.invites.copy(isCreating = false), message = result.error.toUiText(Res.string.members_error_create_invite))
                }
            }
        }
    }

    private fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            val result = inviteRepository.revokeInvite(tripId, inviteId)
            if (result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))) {
                updateInvites { state -> state.copy(invites = state.invites.filterNot { it.id == inviteId }.toImmutableList()) }
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

    @Immutable
    data class State(
        val tripName: String = "",
        val canManage: Boolean = false,
        val members: ImmutableList<TripMember> = persistentListOf(),
        val isLoading: Boolean = true,
        val add: AddState = AddState(),
        val joinRequests: JoinRequestsState = JoinRequestsState(),
        val invites: InvitesState = InvitesState(),
        val message: UiText? = null,
    )

    /** Adding editors: friends picked from the avatar row. */
    @Immutable
    data class AddState(
        /** The owner's friends who aren't in the trip yet; null until they've loaded. */
        val friends: ImmutableList<UserSummary>? = null,
        /** Usernames of the friends picked to add together. */
        val pickedFriends: ImmutableSet<String> = persistentSetOf(),
        val isAdding: Boolean = false,
    )

    /** The owner's: people asking to join, newest first, and those whose answer is waiting for the server. */
    @Immutable
    data class JoinRequestsState(
        val requests: ImmutableList<JoinRequest> = persistentListOf(),
        val answeringIds: ImmutableSet<String> = persistentSetOf(),
    )

    @Immutable
    data class InvitesState(val invites: ImmutableList<TripInvite> = persistentListOf(), val isCreating: Boolean = false)

    sealed interface Event {
        data class ShareInvite(val tripName: String, val url: String) : Event
    }

    sealed interface Action {
        /** The sheet was opened (not on rotation). */
        data object Opened : Action
        data class RemoveMember(val userId: String) : Action
        data object DismissMessage : Action

        sealed interface Add : Action {
            data class FriendToggled(val friend: UserSummary) : Add
            data object PickedFriends : Add
        }

        sealed interface JoinRequests : Action {
            data class Accept(val request: JoinRequest) : JoinRequests
            data class Decline(val request: JoinRequest) : JoinRequests
        }

        sealed interface Invites : Action {
            data object Create : Invites
            data class Share(val invite: TripInvite) : Invites
            data class Revoke(val inviteId: String) : Invites
        }
    }
}
