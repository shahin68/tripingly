package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasFieldError
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDates
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_error_members
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_blocked
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_not_found
import com.falcon.tripingly.feature.trips.generated.resources.home_error_load_trips
import com.falcon.tripingly.feature.trips.generated.resources.home_error_make_private
import com.falcon.tripingly.feature.trips.generated.resources.home_error_make_public
import com.falcon.tripingly.feature.trips.generated.resources.home_error_remove_saved
import com.falcon.tripingly.feature.trips.generated.resources.home_offline_cached
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_error_days_not_empty
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * My Trips. The list comes from the cache and is reloaded from the server on
 * start and on pull-to-refresh; changes wait for the server's answer.
 * Also holds the New trip sheet ([CreateTripForm]).
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())
    private var destinationSearch: Job? = null
    private var memberSearch: Job? = null
    private var friendsLoad: Job? = null

    val uiState: StateFlow<State> = combine(tripRepository.observeMyTrips(), ui) { trips, ui ->
        State(
            selectedTab = ui.selectedTab,
            searchQuery = ui.searchQuery,
            peopleQuery = ui.peopleQuery,
            trips = trips.filter { it.matches(ui.searchQuery) }.toImmutableList(),
            hasTrips = trips.isNotEmpty(),
            isRefreshing = ui.isRefreshing,
            hasLoaded = ui.hasLoaded || trips.isNotEmpty(),
            message = ui.message,
            dialog = ui.dialog?.let { dialog -> dialog.withTrip(trips) },
            isSaving = ui.isSaving,
            dialogError = ui.dialogError,
            membersTripId = ui.membersTripId,
            createTrip = ui.createTrip,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnTabSelected -> ui.update { it.copy(selectedTab = action.tab) }
            is Action.OnSearchQueryChanged -> ui.update { it.copy(searchQuery = action.query) }
            is Action.OnPeopleQueryChanged -> ui.update { it.copy(peopleQuery = action.query) }
            Action.OnRefresh -> refresh()
            Action.OnDismissMessage -> ui.update { it.copy(message = null) }
            is Action.OnTripClick -> sendEvent(Event.NavigateToMap(action.tripId))
            Action.OnAddTripClick -> openCreateTrip()
            is Action.OnRenameTrip -> withTrip(action.tripId) { openDialog(Dialog.Rename(it)) }
            is Action.OnRescheduleTrip -> withTrip(action.tripId) { openDialog(Dialog.Reschedule(it)) }
            is Action.OnDeleteTrip -> withTrip(action.tripId) { openDialog(Dialog.ConfirmDelete(it)) }
            is Action.OnLeaveTrip -> withTrip(action.tripId) { openDialog(Dialog.ConfirmLeave(it)) }
            Action.OnDismissDialog -> if (!ui.value.isSaving) {
                clearSearches()
                ui.update { it.copy(dialog = null, dialogError = null) }
            }
            is Action.OnCreateNameChanged -> updateForm { it.copy(name = action.name) }
            is Action.OnCreateDatesChanged -> updateForm { it.copy(startDate = action.startDate, endDate = action.endDate) }
            is Action.OnDestinationQueryChanged -> searchDestination(action.query)
            is Action.OnDestinationSelected -> selectDestination(action.result)
            is Action.OnCreateVisibilityChanged -> updateForm { it.copy(visibility = action.visibility) }
            is Action.OnMemberQueryChanged -> searchMembers(action.query)
            is Action.OnMemberAdded -> addMember(action.user)
            is Action.OnFriendToggled -> if (ui.value.createTrip.isMember(action.user)) {
                removeMember(action.user.username)
            } else {
                addMember(action.user)
            }
            is Action.OnMemberRemoved -> removeMember(action.username)
            Action.OnConfirmCreateTrip -> createTrip()
            is Action.OnConfirmRenameTrip -> save {
                tripRepository.updateTrip(action.tripId, TripUpdate(name = action.newName))
            }
            is Action.OnConfirmRescheduleTrip -> save {
                tripRepository.updateTrip(action.tripId, TripUpdate(dates = TripDates(action.startDate, action.endDate)))
            }
            is Action.OnConfirmDeleteTrip -> save { tripRepository.deleteTrip(action.tripId) }
            is Action.OnConfirmLeaveTrip -> save { tripRepository.leaveTrip(action.tripId) }
            is Action.OnToggleVisibility -> withTrip(action.tripId) { toggleVisibility(it) }
            is Action.OnRemoveSavedTrip -> withTrip(action.tripId) { removeSavedTrip(it) }
            is Action.OnShareTrip -> withTrip(action.tripId) { sendEvent(Event.ShareTrip(it)) }
            is Action.OnManageMembers -> ui.update { it.copy(membersTripId = action.tripId) }
            Action.OnDismissMembers -> ui.update { it.copy(membersTripId = null) }
        }
    }

    private fun refresh() {
        if (ui.value.isRefreshing) return
        ui.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = tripRepository.refreshMyTrips()
            ui.update {
                it.copy(
                    isRefreshing = false,
                    hasLoaded = true,
                    message = (result as? AppResult.Error)?.error?.let(::refreshMessage),
                )
            }
        }
    }

    private fun refreshMessage(error: DataError.Network): UiText =
        if (error == DataError.Network.NoInternet) UiText.Note(UiText.Resource(Res.string.home_offline_cached)) else error.toUiText(Res.string.home_error_load_trips)

    private fun openDialog(dialog: Dialog) {
        clearSearches()
        ui.update { it.copy(dialog = dialog, dialogError = null) }
    }

    /** A fresh New trip sheet with the user's default visibility; the friends row fills in when they arrive. */
    private fun openCreateTrip() {
        openDialog(Dialog.Create)
        ui.update { it.copy(createTrip = CreateTripForm(visibility = defaultVisibility())) }
        friendsLoad?.cancel()
        friendsLoad = viewModelScope.launch {
            val friends = (userRepository.friends() as? AppResult.Success)?.data.orEmpty()
            updateForm { it.copy(friends = friends.toImmutableList()) }
        }
    }

    private fun defaultVisibility(): TripVisibility = when (val session = sessionRepository.session.value) {
        is SessionState.SignedIn -> session.account.defaultTripVisibility
        is SessionState.Onboarding -> session.account.defaultTripVisibility
        else -> TripVisibility.PUBLIC
    }

    private fun updateForm(change: (CreateTripForm) -> CreateTripForm) {
        ui.update { it.copy(createTrip = change(it.createTrip)) }
    }

    /**
     * Asks the server only once typing pauses for [SEARCH_DEBOUNCE_MILLIS]; every keystroke
     * before that cancels the pending search. A cleared field clears the results at once.
     */
    private fun searchDestination(query: String) {
        destinationSearch?.cancel()
        updateForm { it.copy(destinationQuery = query, destination = null) }
        if (query.isBlank()) {
            updateForm { it.copy(destinationResults = persistentListOf(), isSearchingDestination = false) }
            return
        }
        destinationSearch = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            updateForm { it.copy(isSearchingDestination = true) }
            val results = (placeRepository.searchDestinations(query) as? AppResult.Success)?.data.orEmpty()
            updateForm { it.copy(destinationResults = results.toImmutableList(), isSearchingDestination = false) }
        }
    }

    private fun selectDestination(result: PlaceSearchResult) {
        destinationSearch?.cancel()
        updateForm {
            it.copy(
                destination = Destination(result.name, result.location),
                destinationQuery = result.name,
                destinationResults = persistentListOf(),
                isSearchingDestination = false,
            )
        }
    }

    /** Debounced like [searchDestination]. */
    private fun searchMembers(query: String) {
        memberSearch?.cancel()
        updateForm { it.copy(memberQuery = query) }
        if (query.isBlank()) {
            updateForm { it.copy(memberResults = persistentListOf(), isSearchingMembers = false) }
            return
        }
        memberSearch = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            updateForm { it.copy(isSearchingMembers = true) }
            val results = (userRepository.search(query) as? AppResult.Success)?.data.orEmpty()
            updateForm { it.copy(memberResults = results.toImmutableList(), isSearchingMembers = false) }
        }
    }

    private fun addMember(user: UserSummary) {
        memberSearch?.cancel()
        updateForm { form ->
            val members = if (form.isMember(user)) form.members else form.members + user
            form.copy(
                members = members.toImmutableList(),
                memberQuery = "",
                memberResults = persistentListOf(),
                isSearchingMembers = false,
            )
        }
    }

    /** Removing the last person the server refused also clears the message about them. */
    private fun removeMember(username: String) {
        val form = ui.value.createTrip
        val errors = form.memberErrors - username
        ui.update {
            it.copy(
                createTrip = form.copy(
                    members = form.members.filterNot { member -> member.username == username }.toImmutableList(),
                    memberErrors = errors.toImmutableMap(),
                ),
                dialogError = if (form.memberErrors.isNotEmpty() && errors.isEmpty()) null else it.dialogError,
            )
        }
    }

    private fun clearSearches() {
        destinationSearch?.cancel()
        memberSearch?.cancel()
        updateForm {
            it.copy(
                destinationResults = persistentListOf(),
                isSearchingDestination = false,
                memberResults = persistentListOf(),
                isSearchingMembers = false,
            )
        }
    }

    private fun createTrip() {
        val form = ui.value.createTrip
        if (!form.canCreate || ui.value.isSaving) return
        val usernames = form.members.map { it.username }
        updateForm { it.copy(memberErrors = persistentMapOf()) }
        save(
            refused = { state, error ->
                val memberErrors = memberErrors(error, usernames)
                if (memberErrors.isEmpty()) {
                    state.copy(dialogError = dialogError(error))
                } else {
                    state.copy(
                        dialogError = UiText.Resource(Res.string.create_trip_error_members),
                        createTrip = state.createTrip.copy(memberErrors = memberErrors.toImmutableMap()),
                    )
                }
            },
        ) {
            tripRepository.createTrip(
                NewTrip(
                    name = form.name,
                    startDate = form.startDate,
                    endDate = form.endDate ?: form.startDate,
                    visibility = form.visibility,
                    destination = form.destination,
                    memberUsernames = usernames,
                ),
            )
        }
    }

    /** `fields.memberUsernames.<index>` errors, by the username that was sent at that index. */
    private fun memberErrors(error: DataError.Network, usernames: List<String>): Map<String, UiText> =
        usernames.withIndex().mapNotNull { (index, username) ->
            val field = "memberUsernames.$index"
            when {
                error.hasFieldError(field, TripErrorCodes.MEMBER_NOT_FOUND) ->
                    username to UiText.Resource(Res.string.create_trip_member_not_found)
                error.hasFieldError(field, TripErrorCodes.MEMBER_BLOCKED) ->
                    username to UiText.Resource(Res.string.create_trip_member_blocked)
                else -> null
            }
        }.toMap()

    /** Runs a dialog's change; the dialog stays open with the error if the server refuses it. */
    private fun save(
        refused: (UiState, DataError.Network) -> UiState = { state, error -> state.copy(dialogError = dialogError(error)) },
        change: suspend () -> AppResult<*, DataError.Network>,
    ) {
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true, dialogError = null) }
        viewModelScope.launch {
            val result = change()
            ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, dialog = null)
                    is AppResult.Error -> refused(it.copy(isSaving = false), result.error)
                }
            }
        }
    }

    private fun dialogError(error: DataError.Network): UiText =
        if (error.hasFieldError("endDate", TripErrorCodes.DAYS_NOT_EMPTY)) {
            UiText.Resource(Res.string.reschedule_error_days_not_empty)
        } else {
            error.toUiText()
        }

    private fun toggleVisibility(trip: Trip) {
        val visibility = if (trip.visibility == TripVisibility.PUBLIC) TripVisibility.PRIVATE else TripVisibility.PUBLIC
        viewModelScope.launch {
            val result = tripRepository.updateTrip(trip.id, TripUpdate(visibility = visibility))
            val failed = if (visibility == TripVisibility.PUBLIC) Res.string.home_error_make_public else Res.string.home_error_make_private
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(failed)) }
        }
    }

    /** Someone else's saved trip leaves My Trips once the server agreed; it can be saved again any time. */
    private fun removeSavedTrip(trip: Trip) {
        viewModelScope.launch {
            val result = tripRepository.setSaved(trip, saved = false)
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText(Res.string.home_error_remove_saved)) }
        }
    }

    private fun withTrip(tripId: String, block: (Trip) -> Unit) {
        uiState.value.trips.firstOrNull { it.id == tripId }?.let(block)
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    private data class UiState(
        val selectedTab: Tab = Tab.MyTrips,
        val searchQuery: String = "",
        val peopleQuery: String = "",
        val isRefreshing: Boolean = false,
        val hasLoaded: Boolean = false,
        val message: UiText? = null,
        val dialog: Dialog? = null,
        val isSaving: Boolean = false,
        val dialogError: UiText? = null,
        val membersTripId: String? = null,
        val createTrip: CreateTripForm = CreateTripForm(),
    )

    @Immutable
    data class State(
        val selectedTab: Tab = Tab.MyTrips,
        val searchQuery: String = "",
        /** What's typed in the search bar on the Social tab: people by name. */
        val peopleQuery: String = "",
        /** My Trips matching [searchQuery]. */
        val trips: ImmutableList<Trip> = persistentListOf(),
        /** False only when the user has no trips at all (not just none matching the search). */
        val hasTrips: Boolean = false,
        val isRefreshing: Boolean = false,
        /** False until the first load answered or cached trips exist; shows a spinner instead of "no trips". */
        val hasLoaded: Boolean = false,
        /** A banner, e.g. offline or an error the user should see. */
        val message: UiText? = null,
        val dialog: Dialog? = null,
        val isSaving: Boolean = false,
        val dialogError: UiText? = null,
        /** The trip whose members sheet is open. */
        val membersTripId: String? = null,
        /** The New trip sheet, while [dialog] is [Dialog.Create]. */
        val createTrip: CreateTripForm = CreateTripForm(),
    )

    enum class Tab { MyTrips, Social }

    @Immutable
    sealed interface Dialog {
        data object Create : Dialog
        data class Rename(val trip: Trip) : Dialog
        data class Reschedule(val trip: Trip) : Dialog
        data class ConfirmDelete(val trip: Trip) : Dialog
        data class ConfirmLeave(val trip: Trip) : Dialog
    }

    sealed interface Event {
        data class NavigateToMap(val tripId: String) : Event
        data class ShareTrip(val trip: Trip) : Event
    }

    sealed interface Action {
        data class OnTabSelected(val tab: Tab) : Action
        data class OnSearchQueryChanged(val query: String) : Action
        data class OnPeopleQueryChanged(val query: String) : Action
        data object OnRefresh : Action
        data object OnDismissMessage : Action
        data class OnTripClick(val tripId: String) : Action
        data object OnAddTripClick : Action
        data object OnDismissDialog : Action
        data class OnCreateNameChanged(val name: String) : Action
        data class OnCreateDatesChanged(val startDate: LocalDate?, val endDate: LocalDate?) : Action
        data class OnDestinationQueryChanged(val query: String) : Action
        data class OnDestinationSelected(val result: PlaceSearchResult) : Action
        data class OnCreateVisibilityChanged(val visibility: TripVisibility) : Action
        data class OnMemberQueryChanged(val query: String) : Action
        data class OnMemberAdded(val user: UserSummary) : Action
        data class OnFriendToggled(val user: UserSummary) : Action
        data class OnMemberRemoved(val username: String) : Action
        data object OnConfirmCreateTrip : Action
        data class OnRenameTrip(val tripId: String) : Action
        data class OnConfirmRenameTrip(val tripId: String, val newName: String) : Action
        data class OnRescheduleTrip(val tripId: String) : Action
        data class OnConfirmRescheduleTrip(val tripId: String, val startDate: LocalDate, val endDate: LocalDate) : Action
        data class OnDeleteTrip(val tripId: String) : Action
        data class OnConfirmDeleteTrip(val tripId: String) : Action
        data class OnLeaveTrip(val tripId: String) : Action
        data class OnConfirmLeaveTrip(val tripId: String) : Action
        data class OnToggleVisibility(val tripId: String) : Action
        data class OnRemoveSavedTrip(val tripId: String) : Action
        data class OnShareTrip(val tripId: String) : Action
        data class OnManageMembers(val tripId: String) : Action
        data object OnDismissMembers : Action
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private fun Trip.matches(query: String): Boolean = query.isBlank() || name.contains(query.trim(), ignoreCase = true)

/** Keeps a dialog's trip current (e.g. renamed elsewhere); closes it when the trip is gone. */
private fun HomeViewModel.Dialog.withTrip(trips: List<Trip>): HomeViewModel.Dialog? {
    fun current(trip: Trip) = trips.firstOrNull { it.id == trip.id }
    return when (this) {
        HomeViewModel.Dialog.Create -> this
        is HomeViewModel.Dialog.Rename -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.Reschedule -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.ConfirmDelete -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.ConfirmLeave -> current(trip)?.let { copy(trip = it) }
    }
}
