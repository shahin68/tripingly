package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.presentation.component.ReportState
import com.falcon.tripingly.feature.social.presentation.component.sendReport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Nearby tab: popular places around me, marked hot, their images opened and reported, and added to a day of
 * one of my trips. The places and adding them are [NearbyPlaces].
 */
class NearbyViewModel(
    private val socialRepository: SocialRepository,
    placeRepository: PlaceRepository,
    tripRepository: TripRepository,
    markerRepository: MarkerRepository,
    getCurrentLocation: GetCurrentLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val places = NearbyPlaces(
        viewModelScope, _uiState, placeRepository, socialRepository, tripRepository, markerRepository, getCurrentLocation, ::sendEvent, ::showMessage,
    )

    fun onAction(action: Action) {
        when (action) {
            is Action.PermissionChecked -> places.onPermission(action.permission, isAnswer = false)
            is Action.PermissionResult -> places.onPermission(action.permission, isAnswer = true)
            Action.AllowLocation -> sendEvent(if (_uiState.value.status == Status.Blocked) Event.OpenAppSettings else Event.RequestPermission)
            Action.Refresh -> places.refresh()
            Action.LoadMore -> places.loadMore()
            is Action.MarkHot -> places.toggleHot(action.place.id)
            is Action.AddPlace -> places.openAddPlace(action.place)
            is Action.OpenImage -> _uiState.update { it.copy(openImage = action.place) }
            Action.CloseImage -> _uiState.update { it.copy(openImage = null) }
            // A Wikimedia Commons image has a credit; a trip photo doesn't.
            is Action.ReportImage -> _uiState.update {
                it.copy(report = ReportState(if (action.photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo, action.photo.id))
            }
            is Action.SendReport -> sendReport(action.reason, action.details)
            Action.DismissReport -> _uiState.update { it.copy(report = null) }
            is Action.AddToTrip -> onAddToTrip(action)
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun onAddToTrip(action: Action.AddToTrip) {
        when (action) {
            is Action.AddToTrip.PickTrip -> places.pickTrip(action.trip)
            is Action.AddToTrip.PickDay -> places.addPlace(action.day)
            Action.AddToTrip.Dismiss -> _uiState.update { it.copy(addPlace = null) }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val report = _uiState.value.report?.takeUnless { it.isSending } ?: return
        _uiState.update { it.copy(report = report.copy(isSending = true)) }
        viewModelScope.launch {
            val sent = socialRepository.sendReport(report, reason, details, ::showMessage)
            _uiState.update { it.copy(report = if (sent) null else it.report?.copy(isSending = false)) }
        }
    }

    private fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    enum class Status {
        /** Not checked yet. */
        Unknown,
        NeedsPermission,

        /** The system won't ask again; only Settings can allow it. */
        Blocked,
        Locating,
        Unavailable,
        Ready,
    }

    /** Adding a place to a day: first the trip, then its day. */
    @Immutable
    data class AddPlaceState(
        val place: NearbyPlace,
        val trips: ImmutableList<Trip>,
        val trip: Trip? = null,
        val days: ImmutableList<TripDay> = persistentListOf(),
        val isLoadingDays: Boolean = false,
    )

    @Immutable
    data class State(
        val status: Status = Status.Unknown,
        val places: ImmutableList<NearbyPlace> = persistentListOf(),
        val isLoading: Boolean = false,
        /** Why the places couldn't load when there are none to show: the error screen with a retry. */
        val loadError: UiText? = null,
        /** The place whose images are open full screen, from its cover. */
        val openImage: NearbyPlace? = null,
        val report: ReportState? = null,
        val addPlace: AddPlaceState? = null,
        val message: UiText? = null,
    )

    sealed interface Action {
        /** The permission as Nearby opens or the app comes back, e.g. from Settings. */
        data class PermissionChecked(val permission: LocationPermission) : Action

        /** The answer to the location prompt. */
        data class PermissionResult(val permission: LocationPermission) : Action
        data object AllowLocation : Action
        data object Refresh : Action
        data object LoadMore : Action
        data class MarkHot(val place: NearbyPlace) : Action
        data class AddPlace(val place: NearbyPlace) : Action
        data class OpenImage(val place: NearbyPlace) : Action
        data object CloseImage : Action
        data class ReportImage(val photo: Photo) : Action
        data class SendReport(val reason: ReportReason, val details: String) : Action
        data object DismissReport : Action
        data object DismissMessage : Action

        /** The sheet that adds a place to a day of one of my trips. */
        sealed interface AddToTrip : Action {
            data class PickTrip(val trip: Trip) : AddToTrip
            data class PickDay(val day: TripDay) : AddToTrip
            data object Dismiss : AddToTrip
        }
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data object RequestPermission : Event
        data object OpenAppSettings : Event
    }
}
