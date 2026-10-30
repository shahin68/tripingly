package com.falcon.tripingly.feature.social.presentation.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.presentation.component.PublicTripActions
import com.falcon.tripingly.feature.social.presentation.component.ReportState
import com.falcon.tripingly.feature.social.presentation.component.sendReport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Explore tab: public trips, most liked first, filtered by period and optionally near me; liked, saved to My
 * Trips and reported. The list and its filters are [ExploreFeed]; likes and saves are [PublicTripActions].
 */
class ExploreViewModel(
    private val socialRepository: SocialRepository,
    userRepository: UserRepository,
    tripRepository: TripRepository,
    getCurrentLocation: GetCurrentLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val tripActions = PublicTripActions(
        scope = viewModelScope,
        socialRepository = socialRepository,
        tripRepository = tripRepository,
        trip = { id -> _uiState.value.trips.firstOrNull { it.trip.id == id } },
        updateTrip = { id, change -> _uiState.update { it.updateTrip(id, change) } },
        showMessage = ::showMessage,
    )
    private val feed = ExploreFeed(viewModelScope, _uiState, socialRepository, getCurrentLocation, ::sendEvent, ::showMessage)

    init {
        feed.load()
        // Someone blocked here leaves the list at once.
        viewModelScope.launch {
            userRepository.blocks.collect { userId ->
                _uiState.update { state -> state.copy(trips = state.trips.filterNot { it.trip.owner.id == userId }.toImmutableList()) }
            }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.PeriodSelected -> feed.selectPeriod(action.period)
            Action.ToggleNearby -> feed.toggleNearby()
            is Action.LocationChecked -> feed.onLocationChecked(action.permission)
            is Action.LocationPermissionResult -> feed.onPermissionAnswer(action.permission)
            Action.Refresh -> feed.load()
            Action.LoadMore -> feed.loadMore()
            is Action.TripClick -> sendEvent(Event.OpenTrip(action.trip.trip.id))
            is Action.OwnerClick -> sendEvent(Event.OpenProfile(action.owner.username))
            is Action.LikeTrip -> tripActions.toggleLike(action.trip.trip.id)
            is Action.ToggleSaveTrip -> tripActions.toggleSave(action.trip.trip.id)
            is Action.AskToJoin -> tripActions.requestToJoin(action.trip.trip.id)
            is Action.ReportTrip -> _uiState.update { it.copy(report = ReportState(ReportTarget.Trip, action.trip.trip.id)) }
            is Action.SendReport -> sendReport(action.reason)
            Action.DismissReport -> _uiState.update { it.copy(report = null) }
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun sendReport(reason: ReportReason) {
        val report = _uiState.value.report?.takeUnless { it.isSending } ?: return
        _uiState.update { it.copy(report = report.copy(isSending = true)) }
        viewModelScope.launch {
            val sent = socialRepository.sendReport(report, reason, ::showMessage)
            _uiState.update { it.copy(report = if (sent) null else it.report?.copy(isSending = false)) }
        }
    }

    private fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    @Immutable
    data class State(
        val trips: ImmutableList<PublicTrip> = persistentListOf(),
        /** Which likes rank Explore; This month when the tab opens. */
        val period: ExplorePeriod = ExplorePeriod.Month,
        /** Only trips with a stop near me. */
        val isNearbyFilter: Boolean = false,
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
        /** Why the first page couldn't load: the error screen with a retry. */
        val loadError: UiText? = null,
        val report: ReportState? = null,
        val message: UiText? = null,
    ) {
        /** Less than everything: a period other than All time, or only trips near me. */
        val isFiltered: Boolean get() = period != ExplorePeriod.All || isNearbyFilter

        internal fun updateTrip(tripId: String, change: (PublicTrip) -> PublicTrip) =
            copy(trips = trips.map { if (it.trip.id == tripId) change(it) else it }.toImmutableList())
    }

    sealed interface Action {
        data class PeriodSelected(val period: ExplorePeriod) : Action
        data object ToggleNearby : Action

        /** The permission, read when [Event.CheckLocation] asked for it. */
        data class LocationChecked(val permission: LocationPermission) : Action

        /** The answer to the location prompt the Near me filter asked for. */
        data class LocationPermissionResult(val permission: LocationPermission) : Action
        data object Refresh : Action
        data object LoadMore : Action
        data class TripClick(val trip: PublicTrip) : Action
        data class OwnerClick(val owner: UserSummary) : Action
        data class LikeTrip(val trip: PublicTrip) : Action
        data class ToggleSaveTrip(val trip: PublicTrip) : Action
        data class AskToJoin(val trip: PublicTrip) : Action
        data class ReportTrip(val trip: PublicTrip) : Action
        data class SendReport(val reason: ReportReason) : Action
        data object DismissReport : Action
        data object DismissMessage : Action
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data class OpenProfile(val username: String) : Event
        data object RequestPermission : Event

        /** Read the location permission for the Near me filter; answered with [Action.LocationChecked]. */
        data object CheckLocation : Event
    }
}
