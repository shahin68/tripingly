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
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDates
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.home_error_load_trips
import com.falcon.tripingly.feature.trips.generated.resources.home_error_make_private
import com.falcon.tripingly.feature.trips.generated.resources.home_error_make_public
import com.falcon.tripingly.feature.trips.generated.resources.home_error_remove_saved
import com.falcon.tripingly.feature.trips.generated.resources.home_offline_cached
import com.falcon.tripingly.feature.trips.generated.resources.reschedule_error_days_not_empty
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
import kotlinx.datetime.LocalDate

/**
 * My Trips. The list comes from the cache and is reloaded from the server on start and on pull-to-refresh;
 * changes wait for the server's answer. The New trip sheet's form is its own part ([HomeCreateTrip]).
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    placeRepository: PlaceRepository,
    userRepository: UserRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    /** Everything but the trips, which come from the cache and are filled in below. */
    private val ui = MutableStateFlow(State())

    val uiState: StateFlow<State> = combine(tripRepository.observeMyTrips(), ui) { trips, ui ->
        ui.copy(
            trips = trips.filter { it.matches(ui.searchQuery) }.toImmutableList(),
            hasTrips = trips.isNotEmpty(),
            hasLoaded = ui.hasLoaded || trips.isNotEmpty(),
            dialog = ui.dialog?.let { state -> state.dialog.withTrip(trips)?.let { state.copy(dialog = it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val createTrip = HomeCreateTrip(viewModelScope, ui, tripRepository, placeRepository, userRepository, sessionRepository, ::save)

    init {
        refresh()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.TabSelected -> ui.update { it.copy(selectedTab = action.tab) }
            is Action.SearchQueryChanged -> ui.update { it.copy(searchQuery = action.query) }
            is Action.PeopleQueryChanged -> ui.update { it.copy(peopleQuery = action.query) }
            Action.DismissMessage -> ui.update { it.copy(message = null) }
            is Action.Trips -> onTrips(action)
            is Action.TripDialog -> onTripDialog(action)
            is Action.CreateTrip -> createTrip.onAction(action)
        }
    }

    private fun onTrips(action: Action.Trips) {
        when (action) {
            Action.Trips.Refresh -> refresh()
            is Action.Trips.Click -> sendEvent(Event.NavigateToMap(action.tripId))
            Action.Trips.Add -> createTrip.open()
            is Action.Trips.Rename -> withTrip(action.tripId) { openDialog(Dialog.Rename(it)) }
            is Action.Trips.Reschedule -> withTrip(action.tripId) { openDialog(Dialog.Reschedule(it)) }
            is Action.Trips.Delete -> withTrip(action.tripId) { openDialog(Dialog.ConfirmDelete(it)) }
            is Action.Trips.Leave -> withTrip(action.tripId) { openDialog(Dialog.ConfirmLeave(it)) }
            is Action.Trips.ToggleVisibility -> withTrip(action.tripId) { toggleVisibility(it) }
            is Action.Trips.RemoveSaved -> withTrip(action.tripId) { removeSavedTrip(it) }
            is Action.Trips.Share -> withTrip(action.tripId) { sendEvent(Event.ShareTrip(it)) }
            is Action.Trips.ManageMembers -> ui.update { it.copy(membersTripId = action.tripId) }
            Action.Trips.DismissMembers -> ui.update { it.copy(membersTripId = null) }
        }
    }

    private fun onTripDialog(action: Action.TripDialog) {
        when (action) {
            Action.TripDialog.Dismiss -> if (ui.value.dialog?.isSaving != true) {
                createTrip.clearSearches()
                ui.update { it.copy(dialog = null) }
            }
            is Action.TripDialog.ConfirmRename -> save { tripRepository.updateTrip(action.tripId, TripUpdate(name = action.newName)) }
            is Action.TripDialog.ConfirmReschedule -> save {
                tripRepository.updateTrip(action.tripId, TripUpdate(dates = TripDates(action.startDate, action.endDate)))
            }
            is Action.TripDialog.ConfirmDelete -> save { tripRepository.deleteTrip(action.tripId) }
            is Action.TripDialog.ConfirmLeave -> save { tripRepository.leaveTrip(action.tripId) }
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
        createTrip.clearSearches()
        ui.update { it.copy(dialog = DialogState(dialog)) }
    }

    /** Runs a dialog's change; the dialog stays open with the error if the server refuses it. */
    private fun save(
        refused: (DialogState, DataError.Network) -> DialogState = { state, error -> state.copy(error = error.toDialogError()) },
        change: suspend () -> AppResult<*, DataError.Network>,
    ) {
        val dialog = ui.value.dialog?.takeUnless { it.isSaving } ?: return
        ui.update { it.copy(dialog = dialog.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            val result = change()
            ui.update { state ->
                when (result) {
                    is AppResult.Success -> state.copy(dialog = null)
                    is AppResult.Error -> state.copy(dialog = state.dialog?.let { refused(it.copy(isSaving = false), result.error) })
                }
            }
        }
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
        /** The open dialog or New trip sheet. */
        val dialog: DialogState? = null,
        /** The trip whose members sheet is open. */
        val membersTripId: String? = null,
    )

    /** A dialog, and its change on its way to the server or refused. */
    @Immutable
    data class DialogState(
        val dialog: Dialog,
        val isSaving: Boolean = false,
        val error: UiText? = null,
    )

    enum class Tab { MyTrips, Social }

    @Immutable
    sealed interface Dialog {
        /** The New trip sheet. */
        data class Create(val form: CreateTripForm) : Dialog
        data class Rename(val trip: Trip) : Dialog
        data class Reschedule(val trip: Trip) : Dialog
        data class ConfirmDelete(val trip: Trip) : Dialog
        data class ConfirmLeave(val trip: Trip) : Dialog
    }

    sealed interface Event {
        data class NavigateToMap(val tripId: String) : Event
        data class ShareTrip(val trip: Trip) : Event
    }

    /** Grouped by the part of the screen they come from. */
    sealed interface Action {
        data class TabSelected(val tab: Tab) : Action
        data class SearchQueryChanged(val query: String) : Action
        data class PeopleQueryChanged(val query: String) : Action
        data object DismissMessage : Action

        /** The My Trips list, its New trip button and each trip's long-press menu. */
        sealed interface Trips : Action {
            data object Refresh : Trips
            data class Click(val tripId: String) : Trips
            data object Add : Trips
            data class Rename(val tripId: String) : Trips
            data class Reschedule(val tripId: String) : Trips
            data class Delete(val tripId: String) : Trips
            data class Leave(val tripId: String) : Trips
            data class ToggleVisibility(val tripId: String) : Trips
            data class RemoveSaved(val tripId: String) : Trips
            data class Share(val tripId: String) : Trips
            data class ManageMembers(val tripId: String) : Trips
            data object DismissMembers : Trips
        }

        /** The rename, reschedule, delete and leave dialogs (and closing the New trip sheet). */
        sealed interface TripDialog : Action {
            data object Dismiss : TripDialog
            data class ConfirmRename(val tripId: String, val newName: String) : TripDialog
            data class ConfirmReschedule(val tripId: String, val startDate: LocalDate, val endDate: LocalDate) : TripDialog
            data class ConfirmDelete(val tripId: String) : TripDialog
            data class ConfirmLeave(val tripId: String) : TripDialog
        }

        /** The New trip sheet's fields. */
        sealed interface CreateTrip : Action {
            data class NameChanged(val name: String) : CreateTrip
            data class DatesChanged(val startDate: LocalDate?, val endDate: LocalDate?) : CreateTrip
            data class DestinationQueryChanged(val query: String) : CreateTrip
            data class DestinationSelected(val result: PlaceSearchResult) : CreateTrip
            data class VisibilityChanged(val visibility: TripVisibility) : CreateTrip
            data class MemberQueryChanged(val query: String) : CreateTrip
            data class MemberAdded(val user: UserSummary) : CreateTrip
            data class FriendToggled(val user: UserSummary) : CreateTrip
            data class MemberRemoved(val username: String) : CreateTrip
            data object Confirm : CreateTrip
        }
    }
}

/** A dialog's refusal; a reschedule that would drop days with stops says so. */
internal fun DataError.Network.toDialogError(): UiText =
    if (hasFieldError("endDate", TripErrorCodes.DAYS_NOT_EMPTY)) UiText.Resource(Res.string.reschedule_error_days_not_empty) else toUiText()

private fun Trip.matches(query: String): Boolean = query.isBlank() || name.contains(query.trim(), ignoreCase = true)

/** Keeps a dialog's trip current (e.g. renamed elsewhere); closes it when the trip is gone. */
private fun HomeViewModel.Dialog.withTrip(trips: List<Trip>): HomeViewModel.Dialog? {
    fun current(trip: Trip) = trips.firstOrNull { it.id == trip.id }
    return when (this) {
        is HomeViewModel.Dialog.Create -> this
        is HomeViewModel.Dialog.Rename -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.Reschedule -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.ConfirmDelete -> current(trip)?.let { copy(trip = it) }
        is HomeViewModel.Dialog.ConfirmLeave -> current(trip)?.let { copy(trip = it) }
    }
}
