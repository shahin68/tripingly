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
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_add_place_no_trips
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_location_unavailable
import com.falcon.tripingly.feature.social.generated.resources.social_reported
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Social tab: Explore (public trips, liked and added to my trips), Nearby (popular places around me,
 * liked and added to a day of one of my trips) and finding people by name.
 */
class SocialViewModel(
    private val socialRepository: SocialRepository,
    private val userRepository: UserRepository,
    private val placeRepository: PlaceRepository,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var exploreCursor: String? = null
    private var nearbyCursor: String? = null

    /** Where Nearby looked, for its next pages; kept in memory only while the tab is open. */
    private var nearbyPoint: GeoPoint? = null
    private var peopleSearch: Job? = null
    private var loadingMore: Job? = null

    init {
        loadExplore()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnSectionSelected -> _uiState.update { it.copy(section = action.section) }
            is Action.OnQueryChanged -> searchPeople(action.query)
            Action.OnRefresh -> refresh()
            Action.OnLoadMore -> loadMore()
            is Action.OnTripClick -> sendEvent(Event.OpenTrip(action.trip.trip.id))
            is Action.OnPersonClick -> sendEvent(Event.OpenProfile(action.user.username))
            is Action.OnLikeTrip -> likeTrip(action.trip)
            is Action.OnToggleSaveTrip -> toggleSave(action.trip)
            is Action.OnReportTrip -> _uiState.update { it.copy(reporting = action.trip) }
            is Action.OnSendReport -> sendReport(action.reason, action.details)
            Action.OnDismissReport -> _uiState.update { it.copy(reporting = null) }
            is Action.OnPermissionChecked -> onPermission(action.permission, isAnswer = false)
            is Action.OnPermissionResult -> onPermission(action.permission, isAnswer = true)
            Action.OnAllowLocation -> sendEvent(
                if (_uiState.value.nearbyStatus == NearbyStatus.Blocked) Event.OpenAppSettings else Event.RequestPermission,
            )
            is Action.OnLikePlace -> likePlace(action.place)
            is Action.OnAddPlace -> openAddPlace(action.place)
            is Action.OnPickTrip -> pickTrip(action.trip)
            is Action.OnPickDay -> addPlace(action.day)
            Action.OnDismissAddPlace -> _uiState.update { it.copy(addPlace = null) }
            Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun loadExplore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExplore = true) }
            when (val result = socialRepository.explore(cursor = null)) {
                is AppResult.Success -> {
                    exploreCursor = result.data.nextCursor
                    _uiState.update {
                        it.copy(explore = result.data.items.toImmutableList(), isLoadingExplore = false, hasLoadedExplore = true)
                    }
                }
                is AppResult.Error -> _uiState.update { it.copy(isLoadingExplore = false, message = result.error.toUiText()) }
            }
        }
    }

    private fun refresh() {
        when (_uiState.value.section) {
            Section.Explore -> loadExplore()
            Section.Nearby -> nearbyPoint?.let { loadNearby(it) }
        }
    }

    /** The next page of the list on show; one request at a time. */
    private fun loadMore() {
        if (loadingMore?.isActive == true) return
        loadingMore = viewModelScope.launch {
            when (_uiState.value.section) {
                Section.Explore -> {
                    val cursor = exploreCursor ?: return@launch
                    val result = socialRepository.explore(cursor)
                    if (result is AppResult.Success) {
                        exploreCursor = result.data.nextCursor
                        _uiState.update { state ->
                            val known = state.explore.map { it.trip.id }.toSet()
                            state.copy(explore = (state.explore + result.data.items.filterNot { it.trip.id in known }).toImmutableList())
                        }
                    }
                }
                Section.Nearby -> {
                    val cursor = nearbyCursor ?: return@launch
                    val point = nearbyPoint ?: return@launch
                    val result = placeRepository.nearby(point, cursor)
                    if (result is AppResult.Success) {
                        nearbyCursor = result.data.nextCursor
                        _uiState.update { state ->
                            val known = state.nearby.map { it.id }.toSet()
                            state.copy(nearby = (state.nearby + result.data.items.filterNot { it.id in known }).toImmutableList())
                        }
                    }
                }
            }
        }
    }

    /** Asks the server only once typing pauses; a short or cleared query clears the results at once. */
    private fun searchPeople(query: String) {
        if (query == _uiState.value.peopleQuery) return
        peopleSearch?.cancel()
        _uiState.update { it.copy(peopleQuery = query) }
        if (query.trim().removePrefix("@").length < UserRepository.MIN_SEARCH_LENGTH) {
            _uiState.update { it.copy(people = persistentListOf(), isSearchingPeople = false) }
            return
        }
        peopleSearch = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isSearchingPeople = true) }
            val result = userRepository.search(query)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(people = result.data.toImmutableList(), isSearchingPeople = false)
                    is AppResult.Error -> it.copy(isSearchingPeople = false, message = result.error.toUiText())
                }
            }
        }
    }

    /** Shows the like at once and puts it back when the server refuses. */
    private fun likeTrip(trip: PublicTrip) {
        val liked = !trip.likedByMe
        updateTrip(trip.trip.id) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val result = socialRepository.setLiked(LikeTarget.Trip, trip.trip.id, liked)) {
                is AppResult.Success -> updateTrip(trip.trip.id) { it.copy(likedByMe = result.data.liked, likeCount = result.data.likeCount) }
                is AppResult.Error -> {
                    updateTrip(trip.trip.id) { it.copy(likedByMe = trip.likedByMe, likeCount = trip.likeCount) }
                    _uiState.update { it.copy(message = result.error.toUiText()) }
                }
            }
        }
    }

    private fun updateTrip(tripId: String, change: (PublicTrip) -> PublicTrip) {
        _uiState.update { state -> state.copy(explore = state.explore.map { if (it.trip.id == tripId) change(it) else it }.toImmutableList()) }
    }

    /** Saves to (or removes from) My Trips at once and puts it back when the server refuses. */
    private fun toggleSave(trip: PublicTrip) {
        val saved = !trip.trip.savedByMe
        updateTrip(trip.trip.id) {
            it.copy(trip = it.trip.copy(savedByMe = saved), saveCount = (it.saveCount + if (saved) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            val result = tripRepository.setSaved(trip.trip, saved)
            if (result is AppResult.Error) {
                updateTrip(trip.trip.id) { it.copy(trip = it.trip.copy(savedByMe = trip.trip.savedByMe), saveCount = trip.saveCount) }
                _uiState.update { it.copy(message = result.error.toUiText()) }
            }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val trip = _uiState.value.reporting ?: return
        if (_uiState.value.isReporting) return
        _uiState.update { it.copy(isReporting = true) }
        viewModelScope.launch {
            val result = socialRepository.report(ReportTarget.Trip, trip.trip.id, reason, details)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isReporting = false, reporting = null, message = UiText.Resource(Res.string.social_reported))
                    is AppResult.Error -> it.copy(isReporting = false, message = result.error.toUiText())
                }
            }
        }
    }

    /** Nearby asks for the location only once its tab is open; the place list loads once it's allowed. */
    private fun onPermission(permission: LocationPermission, isAnswer: Boolean) {
        val status = when (permission) {
            LocationPermission.Granted -> null
            LocationPermission.Blocked -> NearbyStatus.Blocked
            LocationPermission.NotAsked, LocationPermission.ShouldExplain -> NearbyStatus.NeedsPermission
        }
        if (status != null) {
            // A first check that finds nothing allowed yet shows the ask; an answer of no keeps it.
            if (isAnswer || _uiState.value.nearbyStatus != NearbyStatus.Ready) _uiState.update { it.copy(nearbyStatus = status) }
            return
        }
        if (_uiState.value.nearbyStatus == NearbyStatus.Ready || _uiState.value.nearbyStatus == NearbyStatus.Locating) return
        locate()
    }

    private fun locate() {
        _uiState.update { it.copy(nearbyStatus = NearbyStatus.Locating) }
        viewModelScope.launch {
            when (val location = getCurrentLocation().first()) {
                is AppResult.Success -> {
                    val point = GeoPoint(location.data.latitude, location.data.longitude)
                    nearbyPoint = point
                    loadNearby(point)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(nearbyStatus = NearbyStatus.Unavailable, message = UiText.Resource(Res.string.social_nearby_location_unavailable))
                }
            }
        }
    }

    private fun loadNearby(point: GeoPoint) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNearby = true) }
            when (val result = placeRepository.nearby(point, cursor = null)) {
                is AppResult.Success -> {
                    nearbyCursor = result.data.nextCursor
                    _uiState.update {
                        it.copy(nearby = result.data.items.toImmutableList(), isLoadingNearby = false, nearbyStatus = NearbyStatus.Ready)
                    }
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingNearby = false, nearbyStatus = NearbyStatus.Ready, message = result.error.toUiText())
                }
            }
        }
    }

    private fun likePlace(place: NearbyPlace) {
        val liked = !place.likedByMe
        updatePlace(place.id) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val result = socialRepository.setLiked(LikeTarget.Place, place.id, liked)) {
                is AppResult.Success -> updatePlace(place.id) { it.copy(likedByMe = result.data.liked, likeCount = result.data.likeCount) }
                is AppResult.Error -> {
                    updatePlace(place.id) { it.copy(likedByMe = place.likedByMe, likeCount = place.likeCount) }
                    _uiState.update { it.copy(message = result.error.toUiText()) }
                }
            }
        }
    }

    private fun updatePlace(placeId: String, change: (NearbyPlace) -> NearbyPlace) {
        _uiState.update { state -> state.copy(nearby = state.nearby.map { if (it.id == placeId) change(it) else it }.toImmutableList()) }
    }

    /** The trips I can add stops to, from the device; the picked trip's days come from the server. */
    private fun openAddPlace(place: NearbyPlace) {
        viewModelScope.launch {
            val trips = tripRepository.observeMyTrips().first().filter { it.role.canEdit }
            if (trips.isEmpty()) {
                _uiState.update { it.copy(message = UiText.Resource(Res.string.social_add_place_no_trips)) }
                return@launch
            }
            _uiState.update { it.copy(addPlace = AddPlace(place, trips.toImmutableList())) }
        }
    }

    private fun pickTrip(trip: Trip) {
        _uiState.update { state -> state.copy(addPlace = state.addPlace?.copy(trip = trip, days = persistentListOf(), isLoadingDays = true)) }
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(trip.id)
            _uiState.update { state ->
                val addPlace = state.addPlace?.takeIf { it.trip?.id == trip.id } ?: return@update state
                when (result) {
                    is AppResult.Success -> state.copy(addPlace = addPlace.copy(days = result.data.days.toImmutableList(), isLoadingDays = false))
                    is AppResult.Error -> state.copy(addPlace = addPlace.copy(isLoadingDays = false), message = result.error.toUiText())
                }
            }
        }
    }

    /** Added at once, like a stop added on the map, and the trip opens to show it. */
    private fun addPlace(day: TripDay) {
        val addPlace = _uiState.value.addPlace ?: return
        val trip = addPlace.trip ?: return
        val place = addPlace.place
        _uiState.update { it.copy(addPlace = null) }
        viewModelScope.launch {
            markerRepository.addMarker(trip.id, day.id, NewMarker(name = place.name, location = place.location, placeId = place.id))
            sendEvent(Event.OpenTrip(trip.id))
        }
    }

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

    /** Adding a Nearby place to a day: first the trip, then its day. */
    @Immutable
    data class AddPlace(
        val place: NearbyPlace,
        val trips: ImmutableList<Trip>,
        val trip: Trip? = null,
        val days: ImmutableList<TripDay> = persistentListOf(),
        val isLoadingDays: Boolean = false,
    )

    @Immutable
    data class State(
        val section: Section = Section.Explore,
        val explore: ImmutableList<PublicTrip> = persistentListOf(),
        val isLoadingExplore: Boolean = false,
        val hasLoadedExplore: Boolean = false,
        val nearby: ImmutableList<NearbyPlace> = persistentListOf(),
        val nearbyStatus: NearbyStatus = NearbyStatus.Unknown,
        val isLoadingNearby: Boolean = false,
        val peopleQuery: String = "",
        val people: ImmutableList<UserSummary> = persistentListOf(),
        val isSearchingPeople: Boolean = false,
        val reporting: PublicTrip? = null,
        val isReporting: Boolean = false,
        val addPlace: AddPlace? = null,
        val message: UiText? = null,
    ) {
        /** People results replace the tabs while a name is being searched. */
        val isSearchingByName: Boolean get() = peopleQuery.trim().removePrefix("@").length >= UserRepository.MIN_SEARCH_LENGTH
    }

    sealed interface Action {
        data class OnSectionSelected(val section: Section) : Action
        data class OnQueryChanged(val query: String) : Action
        data object OnRefresh : Action
        data object OnLoadMore : Action
        data class OnTripClick(val trip: PublicTrip) : Action
        data class OnPersonClick(val user: UserSummary) : Action
        data class OnLikeTrip(val trip: PublicTrip) : Action
        data class OnToggleSaveTrip(val trip: PublicTrip) : Action
        data class OnReportTrip(val trip: PublicTrip) : Action
        data class OnSendReport(val reason: ReportReason, val details: String) : Action
        data object OnDismissReport : Action
        data class OnPermissionChecked(val permission: LocationPermission) : Action
        data class OnPermissionResult(val permission: LocationPermission) : Action
        data object OnAllowLocation : Action
        data class OnLikePlace(val place: NearbyPlace) : Action
        data class OnAddPlace(val place: NearbyPlace) : Action
        data class OnPickTrip(val trip: Trip) : Action
        data class OnPickDay(val day: TripDay) : Action
        data object OnDismissAddPlace : Action
        data object OnDismissMessage : Action
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data class OpenProfile(val username: String) : Event
        data object RequestPermission : Event
        data object OpenAppSettings : Event
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
