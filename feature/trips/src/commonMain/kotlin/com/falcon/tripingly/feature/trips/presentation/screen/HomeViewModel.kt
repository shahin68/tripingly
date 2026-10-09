package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasFieldError
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDates
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.home_offline_cached
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_error_days_not_empty
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())
    private var destinationSearch: Job? = null

    val uiState: StateFlow<State> = combine(tripRepository.observeMyTrips(), ui) { trips, ui ->
        State(
            selectedTab = ui.selectedTab,
            searchQuery = ui.searchQuery,
            trips = trips.filter { it.matches(ui.searchQuery) }.toImmutableList(),
            hasTrips = trips.isNotEmpty(),
            isRefreshing = ui.isRefreshing,
            hasLoaded = ui.hasLoaded || trips.isNotEmpty(),
            message = ui.message,
            dialog = ui.dialog?.let { dialog -> dialog.withTrip(trips) },
            isSaving = ui.isSaving,
            dialogError = ui.dialogError,
            membersTripId = ui.membersTripId,
            destinationResults = ui.destinationResults,
            isSearchingDestination = ui.isSearchingDestination,
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
            Action.OnRefresh -> refresh()
            Action.OnDismissMessage -> ui.update { it.copy(message = null) }
            is Action.OnTripClick -> sendEvent(Event.NavigateToMap(action.tripId))
            Action.OnAddTripClick -> openDialog(Dialog.Create)
            is Action.OnRenameTrip -> withTrip(action.tripId) { openDialog(Dialog.Rename(it)) }
            is Action.OnRescheduleTrip -> withTrip(action.tripId) { openDialog(Dialog.Reschedule(it)) }
            is Action.OnDeleteTrip -> withTrip(action.tripId) { openDialog(Dialog.ConfirmDelete(it)) }
            is Action.OnLeaveTrip -> withTrip(action.tripId) { openDialog(Dialog.ConfirmLeave(it)) }
            Action.OnDismissDialog -> if (!ui.value.isSaving) {
                clearDestinationSearch()
                ui.update { it.copy(dialog = null, dialogError = null) }
            }
            is Action.OnDestinationQueryChanged -> searchDestination(action.query)
            is Action.OnConfirmCreateTrip -> save {
                tripRepository.createTrip(NewTrip(action.name, action.startDate, action.endDate, destination = action.destination))
            }
            is Action.OnConfirmRenameTrip -> save {
                tripRepository.updateTrip(action.tripId, TripUpdate(name = action.newName))
            }
            is Action.OnConfirmRescheduleTrip -> save {
                tripRepository.updateTrip(action.tripId, TripUpdate(dates = TripDates(action.startDate, action.endDate)))
            }
            is Action.OnConfirmDeleteTrip -> save { tripRepository.deleteTrip(action.tripId) }
            is Action.OnConfirmLeaveTrip -> save { tripRepository.leaveTrip(action.tripId) }
            is Action.OnToggleVisibility -> withTrip(action.tripId) { toggleVisibility(it) }
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
        if (error == DataError.Network.NoInternet) UiText.Resource(Res.string.home_offline_cached) else error.toUiText()

    private fun openDialog(dialog: Dialog) {
        clearDestinationSearch()
        ui.update { it.copy(dialog = dialog, dialogError = null) }
    }

    /**
     * Asks the server only once typing pauses for [SEARCH_DEBOUNCE_MILLIS]; every keystroke
     * before that cancels the pending search. A cleared field clears the results at once.
     */
    private fun searchDestination(query: String) {
        destinationSearch?.cancel()
        if (query.isBlank()) {
            ui.update { it.copy(destinationResults = persistentListOf(), isSearchingDestination = false) }
            return
        }
        destinationSearch = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            ui.update { it.copy(isSearchingDestination = true) }
            val results = (placeRepository.search(query) as? AppResult.Success)?.data.orEmpty()
            ui.update { it.copy(destinationResults = results.toImmutableList(), isSearchingDestination = false) }
        }
    }

    private fun clearDestinationSearch() {
        destinationSearch?.cancel()
        ui.update { it.copy(destinationResults = persistentListOf(), isSearchingDestination = false) }
    }

    /** Runs a dialog's change; the dialog stays open with the error if the server refuses it. */
    private fun save(change: suspend () -> AppResult<*, DataError.Network>) {
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true, dialogError = null) }
        viewModelScope.launch {
            val result = change()
            ui.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, dialog = null)
                    is AppResult.Error -> it.copy(isSaving = false, dialogError = dialogError(result.error))
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
            if (result is AppResult.Error) ui.update { it.copy(message = result.error.toUiText()) }
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
        val isRefreshing: Boolean = false,
        val hasLoaded: Boolean = false,
        val message: UiText? = null,
        val dialog: Dialog? = null,
        val isSaving: Boolean = false,
        val dialogError: UiText? = null,
        val membersTripId: String? = null,
        val destinationResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
        val isSearchingDestination: Boolean = false,
    )

    @Immutable
    data class State(
        val selectedTab: Tab = Tab.MyTrips,
        val searchQuery: String = "",
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
        /** Places matching what was typed into the new trip's destination field. */
        val destinationResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
        val isSearchingDestination: Boolean = false,
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
        data object OnRefresh : Action
        data object OnDismissMessage : Action
        data class OnTripClick(val tripId: String) : Action
        data object OnAddTripClick : Action
        data object OnDismissDialog : Action
        data class OnDestinationQueryChanged(val query: String) : Action
        data class OnConfirmCreateTrip(
            val name: String,
            val startDate: LocalDate,
            val endDate: LocalDate,
            val destination: Destination? = null,
        ) : Action
        data class OnRenameTrip(val tripId: String) : Action
        data class OnConfirmRenameTrip(val tripId: String, val newName: String) : Action
        data class OnRescheduleTrip(val tripId: String) : Action
        data class OnConfirmRescheduleTrip(val tripId: String, val startDate: LocalDate, val endDate: LocalDate) : Action
        data class OnDeleteTrip(val tripId: String) : Action
        data class OnConfirmDeleteTrip(val tripId: String) : Action
        data class OnLeaveTrip(val tripId: String) : Action
        data class OnConfirmLeaveTrip(val tripId: String) : Action
        data class OnToggleVisibility(val tripId: String) : Action
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
