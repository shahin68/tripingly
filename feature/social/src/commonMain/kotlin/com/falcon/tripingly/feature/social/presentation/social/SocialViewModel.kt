package com.falcon.tripingly.feature.social.presentation.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.ExplorePeriod
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_search_people
import com.falcon.tripingly.feature.social.presentation.component.PublicTripActions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.falcon.tripingly.core.model.photo.Photo

/**
 * The Social tab: Explore (public trips, most liked first, filtered by period and optionally near me; liked
 * and added to my trips), Nearby (popular places around me, liked and added to a day of one of my trips)
 * and finding people by name. Explore and Nearby each have their own part ([SocialExplore], [SocialNearby]).
 */
class SocialViewModel(
    socialRepository: SocialRepository,
    private val userRepository: UserRepository,
    placeRepository: PlaceRepository,
    tripRepository: TripRepository,
    markerRepository: MarkerRepository,
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
        trip = { id -> _uiState.value.explore.trips.firstOrNull { it.trip.id == id } },
        updateTrip = { id, change -> _uiState.update { it.copy(explore = it.explore.updateTrip(id, change)) } },
        showMessage = ::showMessage,
    )
    private val explore = SocialExplore(viewModelScope, _uiState, socialRepository, getCurrentLocation, ::sendEvent, ::showMessage)
    private val nearby = SocialNearby(
        viewModelScope, _uiState, placeRepository, socialRepository, tripRepository, markerRepository, getCurrentLocation, ::sendEvent, ::showMessage,
    )
    private var peopleSearch: Job? = null

    init {
        explore.load()
        // Someone blocked here leaves the lists at once.
        viewModelScope.launch {
            userRepository.blocks.collect { userId ->
                _uiState.update { state ->
                    state.copy(
                        explore = state.explore.copy(trips = state.explore.trips.filterNot { it.trip.owner.id == userId }.toImmutableList()),
                        people = state.people.copy(results = state.people.results.filterNot { it.id == userId }.toImmutableList()),
                    )
                }
            }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.SectionSelected -> _uiState.update { it.copy(section = action.section) }
            is Action.LocationPermissionResult -> if (!explore.onPermissionAnswer(action.permission)) nearby.onPermission(action.permission, isAnswer = true)
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
            is Action.Explore -> onExplore(action)
            is Action.Nearby -> onNearby(action)
            is Action.People -> onPeople(action)
            is Action.Report -> onReport(action)
            is Action.AddToTrip -> onAddToTrip(action)
        }
    }

    private fun onExplore(action: Action.Explore) {
        when (action) {
            is Action.Explore.PeriodSelected -> explore.selectPeriod(action.period)
            Action.Explore.ToggleNearby -> explore.toggleNearby()
            is Action.Explore.LocationChecked -> explore.onLocationChecked(action.permission)
            Action.Explore.Refresh -> explore.load()
            Action.Explore.LoadMore -> explore.loadMore()
            is Action.Explore.TripClick -> sendEvent(Event.OpenTrip(action.trip.trip.id))
            is Action.Explore.OwnerClick -> sendEvent(Event.OpenProfile(action.owner.username))
            is Action.Explore.LikeTrip -> tripActions.toggleLike(action.trip.trip.id)
            is Action.Explore.ToggleSaveTrip -> tripActions.toggleSave(action.trip.trip.id)
            is Action.Explore.ReportTrip -> _uiState.update { it.copy(report = ReportState(ReportTarget.Trip, action.trip.trip.id)) }
        }
    }

    private fun onNearby(action: Action.Nearby) {
        when (action) {
            is Action.Nearby.PermissionChecked -> nearby.onPermission(action.permission, isAnswer = false)
            Action.Nearby.AllowLocation -> sendEvent(
                if (_uiState.value.nearby.status == NearbyStatus.Blocked) Event.OpenAppSettings else Event.RequestPermission,
            )
            Action.Nearby.Refresh -> nearby.refresh()
            Action.Nearby.LoadMore -> nearby.loadMore()
            is Action.Nearby.LikePlace -> nearby.toggleLike(action.place.id)
            is Action.Nearby.AddPlace -> nearby.openAddPlace(action.place)
            is Action.Nearby.OpenImage -> _uiState.update { it.copy(nearby = it.nearby.copy(openImage = action.place)) }
            Action.Nearby.CloseImage -> _uiState.update { it.copy(nearby = it.nearby.copy(openImage = null)) }
            // A Wikimedia Commons image has a credit; a trip photo doesn't.
            is Action.Nearby.ReportImage -> _uiState.update {
                it.copy(report = ReportState(if (action.photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo, action.photo.id))
            }
        }
    }

    private fun onPeople(action: Action.People) {
        when (action) {
            is Action.People.QueryChanged -> searchPeople(action.query)
            is Action.People.PersonClick -> sendEvent(Event.OpenProfile(action.user.username))
        }
    }

    private fun onReport(action: Action.Report) {
        when (action) {
            is Action.Report.Send -> sendReport(action.reason, action.details)
            Action.Report.Dismiss -> _uiState.update { it.copy(report = null) }
        }
    }

    private fun onAddToTrip(action: Action.AddToTrip) {
        when (action) {
            is Action.AddToTrip.PickTrip -> nearby.pickTrip(action.trip)
            is Action.AddToTrip.PickDay -> nearby.addPlace(action.day)
            Action.AddToTrip.Dismiss -> _uiState.update { it.copy(addPlace = null) }
        }
    }

    /** Asks the server only once typing pauses; a short or cleared query clears the results at once. */
    private fun searchPeople(query: String) {
        if (query == _uiState.value.people.query) return
        peopleSearch?.cancel()
        _uiState.update { it.copy(people = it.people.copy(query = query)) }
        if (!_uiState.value.people.isSearchingByName) {
            _uiState.update { it.copy(people = it.people.copy(results = persistentListOf(), isSearching = false)) }
            return
        }
        peopleSearch = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { it.copy(people = it.people.copy(isSearching = true)) }
            val result = userRepository.search(query)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(people = it.people.copy(results = result.data.toImmutableList(), isSearching = false))
                    is AppResult.Error -> it.copy(
                        people = it.people.copy(isSearching = false),
                        message = result.error.toUiText(Res.string.social_error_search_people),
                    )
                }
            }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val report = _uiState.value.report?.takeUnless { it.isSending } ?: return
        _uiState.update { it.copy(report = report.copy(isSending = true)) }
        viewModelScope.launch {
            val sent = tripActions.report(report.target, report.id, reason, details)
            _uiState.update { it.copy(report = if (sent) null else it.report?.copy(isSending = false)) }
        }
    }

    private fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    enum class Section { Explore, Nearby }

    enum class NearbyStatus {
        /** Not checked yet. */
        Unknown,
        NeedsPermission,

        /** The system won't ask again; only Settings can allow it. */
        Blocked,
        Locating,
        Unavailable,
        Ready,
    }

    @Immutable
    data class ExploreState(
        val trips: ImmutableList<PublicTrip> = persistentListOf(),
        /** Which likes rank Explore; This month when the tab opens. */
        val period: ExplorePeriod = ExplorePeriod.Month,
        /** Only trips with a stop near me. */
        val isNearbyFilter: Boolean = false,
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
    ) {
        /** Less than everything: a period other than All time, or only trips near me. */
        val isFiltered: Boolean get() = period != ExplorePeriod.All || isNearbyFilter

        internal fun updateTrip(tripId: String, change: (PublicTrip) -> PublicTrip) =
            copy(trips = trips.map { if (it.trip.id == tripId) change(it) else it }.toImmutableList())
    }

    @Immutable
    data class NearbyState(
        val status: NearbyStatus = NearbyStatus.Unknown,
        val places: ImmutableList<NearbyPlace> = persistentListOf(),
        val isLoading: Boolean = false,
        /** The place whose images are open full screen, from its cover. */
        val openImage: NearbyPlace? = null,
    )

    @Immutable
    data class PeopleSearchState(
        val query: String = "",
        val results: ImmutableList<UserSummary> = persistentListOf(),
        val isSearching: Boolean = false,
    ) {
        /** People results replace the tabs while a name is being searched. */
        val isSearchingByName: Boolean get() = query.trim().removePrefix("@").length >= UserRepository.MIN_SEARCH_LENGTH
    }

    /** The report dialog, open for a trip or a place image ([target] with [id]). */
    @Immutable
    data class ReportState(val target: ReportTarget, val id: String, val isSending: Boolean = false)

    /** Adding a Nearby place to a day: first the trip, then its day. */
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
        val section: Section = Section.Explore,
        val explore: ExploreState = ExploreState(),
        val nearby: NearbyState = NearbyState(),
        val people: PeopleSearchState = PeopleSearchState(),
        val report: ReportState? = null,
        val addPlace: AddPlaceState? = null,
        val message: UiText? = null,
    )

    /** Grouped by the part of the tab they come from. */
    sealed interface Action {
        data class SectionSelected(val section: Section) : Action

        /** The answer to a location prompt, from Explore's Nearby filter or the Nearby tab. */
        data class LocationPermissionResult(val permission: LocationPermission) : Action
        data object DismissMessage : Action

        sealed interface Explore : Action {
            data class PeriodSelected(val period: ExplorePeriod) : Explore
            data object ToggleNearby : Explore

            /** The permission, read when [Event.CheckExploreLocation] asked for it. */
            data class LocationChecked(val permission: LocationPermission) : Explore
            data object Refresh : Explore
            data object LoadMore : Explore
            data class TripClick(val trip: PublicTrip) : Explore
            data class OwnerClick(val owner: UserSummary) : Explore
            data class LikeTrip(val trip: PublicTrip) : Explore
            data class ToggleSaveTrip(val trip: PublicTrip) : Explore
            data class ReportTrip(val trip: PublicTrip) : Explore
        }

        sealed interface Nearby : Action {
            data class PermissionChecked(val permission: LocationPermission) : Nearby
            data object AllowLocation : Nearby
            data object Refresh : Nearby
            data object LoadMore : Nearby
            data class LikePlace(val place: NearbyPlace) : Nearby
            data class AddPlace(val place: NearbyPlace) : Nearby
            data class OpenImage(val place: NearbyPlace) : Nearby
            data object CloseImage : Nearby
            data class ReportImage(val photo: Photo) : Nearby
        }

        /** Finding people by the name typed in Home's search bar. */
        sealed interface People : Action {
            data class QueryChanged(val query: String) : People
            data class PersonClick(val user: UserSummary) : People
        }

        sealed interface Report : Action {
            data class Send(val reason: ReportReason, val details: String) : Report
            data object Dismiss : Report
        }

        /** The sheet that adds a Nearby place to a day of one of my trips. */
        sealed interface AddToTrip : Action {
            data class PickTrip(val trip: Trip) : AddToTrip
            data class PickDay(val day: TripDay) : AddToTrip
            data object Dismiss : AddToTrip
        }
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data class OpenProfile(val username: String) : Event
        data object RequestPermission : Event

        /** Read the location permission for Explore's Nearby filter; answered with [Action.Explore.LocationChecked]. */
        data object CheckExploreLocation : Event
        data object OpenAppSettings : Event
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
