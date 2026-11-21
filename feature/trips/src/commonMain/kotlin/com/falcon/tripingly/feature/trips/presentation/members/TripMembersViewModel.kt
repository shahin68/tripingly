package com.falcon.tripingly.feature.trips.presentation.members

import androidx.compose.foundation.text.input.TextFieldState
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
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.members_error_add
import com.falcon.tripingly.feature.trips.generated.resources.members_error_answer_request
import com.falcon.tripingly.feature.trips.generated.resources.members_error_create_invite
import com.falcon.tripingly.feature.trips.generated.resources.members_error_load
import com.falcon.tripingly.feature.trips.generated.resources.members_error_load_requests
import com.falcon.tripingly.feature.trips.generated.resources.members_error_remove
import com.falcon.tripingly.feature.trips.generated.resources.members_error_search
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlin.time.Clock

/**
 * Who is in a trip (design: the members sheet canvas, board "Search icon in the header", Shahin 2026-11-18). The owner
 * answers requests to join, adds a friend with one tap (at once, with Undo), finds anyone by name or @username on the
 * Add people page, removes editors and shares an invite link. Everything loads together behind one skeleton, so the
 * sheet doesn't jump; someone asking to join is never offered as a friend to add.
 */
class TripMembersViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val inviteRepository: TripInviteRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    /** Everything but the trip itself, which comes from the cache and is filled in below. */
    private val ui = MutableStateFlow(Ui())

    val uiState: StateFlow<State> = combine(tripRepository.observeTrip(tripId), ui) { details, ui ->
        val undone = ui.undone
        val saved = details?.members.orEmpty().filterNot { it.user.username in undone }
        val savedNames = saved.mapTo(HashSet()) { it.user.username }
        // One row per person: a quick add, Undo, add again must never list anyone twice (it crashed the list's keys).
        val members = (saved + ui.adding.filterNot { it.username in savedNames || it.username in undone }.map { TripMember(it, TripRole.EDITOR) })
            .distinctBy { it.user.username }
        val onTrip = members.mapTo(HashSet()) { it.user.username }
        val asking = ui.requests.requests.mapTo(HashSet()) { it.user.username }
        State(
            tripName = details?.trip?.name.orEmpty(),
            canManage = details?.trip?.role?.canManage == true,
            isLoading = ui.isLoading,
            waitingCount = details?.pendingJoinRequestCount ?: 0,
            members = members.toImmutableList(),
            joinRequests = ui.requests,
            friends = ui.friends?.filterNot { it.username in onTrip || it.username in asking }?.toImmutableList(),
            onTrip = onTrip.toImmutableSet(),
            asking = asking.toImmutableSet(),
            search = ui.search,
            invites = ui.invites,
            isCreatingInvite = ui.isCreatingInvite,
            message = ui.message,
            added = ui.added,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    fun onAction(action: Action) {
        when (action) {
            // The sheet's view model outlives the sheet, so each opening starts fresh.
            Action.Opened -> {
                searchJob?.cancel()
                ui.value = Ui()
                load()
            }
            is Action.RemoveMember -> remove(action.user, Res.string.members_error_remove)
            Action.DismissMessage -> ui.update { it.copy(message = null) }
            Action.DismissAdded -> ui.update { it.copy(added = null) }
            is Action.AddPerson -> add(action.user)
            is Action.Undo -> undo(action.user)
            is Action.JoinRequests -> when (action) {
                is Action.JoinRequests.Accept -> answerJoinRequest(action.request.id, isAccept = true) { tripRepository.acceptJoinRequest(tripId, it) }
                is Action.JoinRequests.Decline -> answerJoinRequest(action.request.id, isAccept = false) { tripRepository.declineJoinRequest(tripId, it) }
            }
            Action.ShareInvite -> shareInvite()
            Action.Search.Open -> {
                searchJob?.cancel()
                ui.update { it.copy(search = SearchState()) }
            }
            is Action.Search.QueryChanged -> search(action.query)
            Action.Search.Close -> {
                searchJob?.cancel()
                ui.update { it.copy(search = null) }
            }
        }
    }

    /** The trip first, then (for its owner) the requests, friends and invite links together: one skeleton, no jumps. */
    private fun load() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            if (result is AppResult.Success && result.data.trip.role.canManage) {
                val requests = async { if (result.data.pendingJoinRequestCount > 0) tripRepository.joinRequests(tripId) else null }
                val friends = async { userRepository.friends() }
                val invites = async { inviteRepository.invites(tripId) }
                val requestsResult = requests.await()
                val friendsResult = friends.await()
                val invitesResult = invites.await()
                ui.update { state ->
                    state.copy(
                        requests = (requestsResult as? AppResult.Success)?.data?.items?.toImmutableList()?.let { JoinRequestsState(it) } ?: state.requests,
                        friends = (friendsResult as? AppResult.Success)?.data?.toImmutableList() ?: persistentListOf(),
                        invites = (invitesResult as? AppResult.Success)?.data?.toImmutableList() ?: persistentListOf(),
                        message = (requestsResult as? AppResult.Error)?.error?.toUiText(Res.string.members_error_load_requests),
                    )
                }
            }
            ui.update {
                it.copy(
                    isLoading = false,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_load) ?: it.message,
                )
            }
        }
    }

    /**
     * A friend tapped or a person found: on the trip at once, with Undo in the banner (the optimistic rule). The server
     * answer only matters when it fails: then they leave the list again and the banner says why. Undo during the call
     * removes them once it's done.
     */
    private fun add(user: UserSummary) {
        val state = uiState.value
        if (user.username in state.onTrip || user.username in state.asking) return
        ui.update { it.copy(adding = (it.adding.filterNot { added -> added.username == user.username } + user).toImmutableList(), undone = (it.undone - user.username).toImmutableSet(), added = user, message = null) }
        viewModelScope.launch {
            val result = tripRepository.addMember(tripId, user.username)
            if (result is AppResult.Success) awaitCache(user, isMember = true)
            val wasUndone = user.username in ui.value.undone
            ui.update { state ->
                state.copy(
                    adding = state.adding.filterNot { it.username == user.username }.toImmutableList(),
                    added = if (result is AppResult.Error && state.added?.username == user.username) null else state.added,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_add) ?: state.message,
                )
            }
            if (result is AppResult.Success && wasUndone) removeSaved(user, Res.string.members_error_remove)
        }
    }

    /** Undo of an add: gone from the list at once; removed on the server once the add has landed. */
    private fun undo(user: UserSummary) {
        val isStillAdding = ui.value.adding.any { it.username == user.username }
        ui.update { it.copy(undone = (it.undone + user.username).toImmutableSet(), added = null) }
        if (!isStillAdding) viewModelScope.launch { removeSaved(user, Res.string.members_error_remove) }
    }

    /** An editor removed from the ⋮ menu: gone at once, back with the banner if the server refuses. */
    private fun remove(user: UserSummary, failed: StringResource) {
        ui.update { it.copy(undone = (it.undone + user.username).toImmutableSet()) }
        viewModelScope.launch { removeSaved(user, failed) }
    }

    /** Removes [user] as the cache knows them (the server's member id) and forgets the local hiding once it's done. */
    private suspend fun removeSaved(user: UserSummary, failed: StringResource) {
        val saved = tripRepository.observeTrip(tripId).first()?.members.orEmpty().firstOrNull { it.user.username == user.username }
        val result = if (saved == null) null else tripRepository.removeMember(tripId, saved.user.id)
        if (result is AppResult.Success) awaitCache(user, isMember = false)
        ui.update {
            it.copy(
                undone = (it.undone - user.username).toImmutableSet(),
                message = (result as? AppResult.Error)?.error?.toUiText(failed) ?: it.message,
            )
        }
    }

    /**
     * Waits until the cached trip shows the change the server made. The cache emits a moment after it is written, so
     * dropping the local add or removal before that would show the old list for a frame (Shahin's recording: the
     * sheet blinked on add and remove).
     */
    private suspend fun awaitCache(user: UserSummary, isMember: Boolean) {
        // Or until the user turned it around again (Undo of this add, or adding them back during this removal): the
        // cache may then never show this change, and the newer tap takes over.
        combine(tripRepository.observeTrip(tripId), ui) { details, ui ->
            details?.members.orEmpty().any { it.user.username == user.username } == isMember || (user.username in ui.undone) == isMember
        }.first { it }
    }

    /** Accepting or declining; the request leaves the list once the server agreed (or it was gone already). */
    private fun answerJoinRequest(requestId: String, isAccept: Boolean, answer: suspend (String) -> AppResult<*, DataError.Network>) {
        if (requestId in ui.value.requests.answeringIds) return
        ui.update { it.copy(requests = it.requests.copy(answeringIds = (it.requests.answeringIds + requestId).toImmutableSet()), message = null) }
        viewModelScope.launch {
            val result = answer(requestId)
            val isGone = result is AppResult.Success || (result is AppResult.Error && result.error.hasCode(TripErrorCodes.NOT_FOUND))
            if (isAccept && result is AppResult.Success) sendEvent(Event.Accepted)
            ui.update { state ->
                val requests = state.requests
                state.copy(
                    requests = JoinRequestsState(
                        requests = if (isGone) requests.requests.filterNot { it.id == requestId }.toImmutableList() else requests.requests,
                        answeringIds = (requests.answeringIds - requestId).toImmutableSet(),
                    ),
                    // A full trip (`LIMIT_REACHED`) comes with the server's message.
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_answer_request),
                )
            }
        }
    }

    /** Shares the newest link that hasn't expired, or makes one first. */
    private fun shareInvite() {
        val current = ui.value.invites.filter { it.expiresAt > Clock.System.now() }.maxByOrNull { it.expiresAt }
        if (current != null) return sendEvent(Event.ShareInvite(uiState.value.tripName, current.url))
        if (ui.value.isCreatingInvite) return
        ui.update { it.copy(isCreatingInvite = true, message = null) }
        viewModelScope.launch {
            val result = inviteRepository.createInvite(tripId)
            ui.update {
                it.copy(
                    isCreatingInvite = false,
                    invites = if (result is AppResult.Success) (it.invites + result.data).toImmutableList() else it.invites,
                    message = (result as? AppResult.Error)?.error?.toUiText(Res.string.members_error_create_invite),
                )
            }
            if (result is AppResult.Success) sendEvent(Event.ShareInvite(uiState.value.tripName, result.data.url))
        }
    }

    /** The Add people page: friends match as you type; anyone else is asked for once typing pauses. */
    private fun search(text: String) {
        val query = text.trim().removePrefix("@")
        searchJob?.cancel()
        if (query.length < UserRepository.MIN_SEARCH_LENGTH) {
            updateSearch { it.copy(results = persistentListOf(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            updateSearch { it.copy(isLoading = true) }
            val result = userRepository.search(query)
            updateSearch { it.copy(results = (result as? AppResult.Success)?.data?.toImmutableList() ?: it.results, isLoading = false) }
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(Res.string.members_error_search)) }
        }
    }

    private fun updateSearch(change: (SearchState) -> SearchState) = ui.update { state -> state.copy(search = state.search?.let(change)) }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    /** What the view model keeps beside the cached trip. */
    private data class Ui(
        /** Until this opening's load is done: a cached trip waits for its requests and friends, so nothing jumps in. */
        val isLoading: Boolean = true,
        val requests: JoinRequestsState = JoinRequestsState(),
        val friends: ImmutableList<UserSummary>? = null,
        /** Added on the device, waiting for the server. */
        val adding: ImmutableList<UserSummary> = persistentListOf(),
        /** Usernames undone or removed on the device, hidden while the server catches up. */
        val undone: ImmutableSet<String> = persistentSetOf(),
        val invites: ImmutableList<TripInvite> = persistentListOf(),
        val isCreatingInvite: Boolean = false,
        val search: SearchState? = null,
        val message: UiText? = null,
        val added: UserSummary? = null,
    )

    @Immutable
    data class State(
        val tripName: String = "",
        val canManage: Boolean = false,
        val isLoading: Boolean = true,
        /** How many wait to join by the cached trip: the skeleton draws as many request rows. */
        val waitingCount: Int = 0,
        val members: ImmutableList<TripMember> = persistentListOf(),
        val joinRequests: JoinRequestsState = JoinRequestsState(),
        /** The owner's friends who aren't on the trip and aren't asking to join; null until they've loaded. */
        val friends: ImmutableList<UserSummary>? = null,
        /** Usernames on the trip (saved or being added) and asking to join, for the Add people page. */
        val onTrip: ImmutableSet<String> = persistentSetOf(),
        val asking: ImmutableSet<String> = persistentSetOf(),
        /** The Add people page, while it's open. */
        val search: SearchState? = null,
        val invites: ImmutableList<TripInvite> = persistentListOf(),
        val isCreatingInvite: Boolean = false,
        val message: UiText? = null,
        /** Who was just added: the banner offers Undo. */
        val added: UserSummary? = null,
    )

    /** The owner's: people asking to join, newest first, and those whose answer is waiting for the server. */
    @Immutable
    data class JoinRequestsState(
        val requests: ImmutableList<JoinRequest> = persistentListOf(),
        val answeringIds: ImmutableSet<String> = persistentSetOf(),
    )

    /** The Add people page: what's typed (each change comes back as [Action.Search.QueryChanged]), and the people the server found for it. */
    @Immutable
    data class SearchState(
        val query: TextFieldState = TextFieldState(),
        val results: ImmutableList<UserSummary> = persistentListOf(),
        val isLoading: Boolean = false,
    )

    sealed interface Event {
        data class ShareInvite(val tripName: String, val url: String) : Event

        /** The server took in a request I accepted: a success click. */
        data object Accepted : Event
    }

    sealed interface Action {
        /** The sheet was opened (not on rotation). */
        data object Opened : Action
        data class RemoveMember(val user: UserSummary) : Action
        data object DismissMessage : Action

        /** The "added" banner with Undo was handed to the banner. */
        data object DismissAdded : Action

        /** A friend's face tapped, or Add on the Add people page. */
        data class AddPerson(val user: UserSummary) : Action

        /** Undo in the banner after [AddPerson]. */
        data class Undo(val user: UserSummary) : Action
        data object ShareInvite : Action

        sealed interface JoinRequests : Action {
            data class Accept(val request: JoinRequest) : JoinRequests
            data class Decline(val request: JoinRequest) : JoinRequests
        }

        sealed interface Search : Action {
            data object Open : Search
            data object Close : Search

            /** The Add people field changed. */
            data class QueryChanged(val query: String) : Search
        }
    }

    private companion object {
        val SEARCH_DEBOUNCE = 300.milliseconds
    }
}
