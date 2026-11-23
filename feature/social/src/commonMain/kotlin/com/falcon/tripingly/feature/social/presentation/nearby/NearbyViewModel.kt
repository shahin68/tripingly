package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.permission.LocationPermission
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.social.Person
import com.falcon.tripingly.core.ui.UiText
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
 * The Nearby tab (Shahin, 2026-11-23): spots around me in a grid ([NearbyPlaces]), people as a row of faces and a
 * sheet ([NearbyPeople]), and the search for both from the shell's top bar ([NearbySearch]). A spot opens its own map.
 */
class NearbyViewModel(
    socialRepository: SocialRepository,
    placeRepository: PlaceRepository,
    userRepository: UserRepository,
    getCurrentLocation: GetCurrentLocationUseCase,
    appConfigRepository: AppConfigRepository,
) : ViewModel() {

    /** The search field in the shell's top bar; each change comes back as [Action.QueryChanged]. */
    val queryField = TextFieldState()

    private val _uiState = MutableStateFlow(State(isOsmCreditShown = appConfigRepository.config.value.osmPinsEnabled))
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val people = NearbyPeople(viewModelScope, _uiState, userRepository, ::showMessage)

    private val places = NearbyPlaces(viewModelScope, _uiState, placeRepository, socialRepository, getCurrentLocation, people::load, ::showMessage)

    private val search = NearbySearch(viewModelScope, _uiState, userRepository, placeRepository, { places.point }, people, ::showMessage)

    fun onAction(action: Action) {
        when (action) {
            is Action.PermissionChecked -> places.onPermission(action.permission, isAnswer = false)
            is Action.PermissionResult -> places.onPermission(action.permission, isAnswer = true)
            Action.AllowLocation -> sendEvent(if (_uiState.value.status == Status.Blocked) Event.OpenAppSettings else Event.RequestPermission)
            Action.Refresh -> places.refresh()
            Action.LoadMore -> if (_uiState.value.search.isSearching) search.loadMore() else places.loadMore()
            is Action.MarkHot -> places.toggleHot(action.place.id)
            is Action.QueryChanged -> search.onQuery(action.query)
            Action.OpenPeople -> _uiState.update { it.copy(isPeopleSheetOpen = true) }
            Action.ClosePeople -> _uiState.update { it.copy(isPeopleSheetOpen = false) }
            Action.LoadMorePeople -> people.loadMore()
            is Action.ToggleFollow -> people.toggleFollow(action.person)
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
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

    /** People in the row and the sheet, with how many there are in all (up to 999, "999+"). */
    @Immutable
    data class PeopleState(
        val people: ImmutableList<Person> = persistentListOf(),
        val total: Int = 0,
        val hasLoaded: Boolean = false,
    )

    /** What the search found for [query]; it replaces the grid while [isSearching]. */
    @Immutable
    data class SearchState(
        val query: String = "",
        val people: PeopleState = PeopleState(),
        val spots: ImmutableList<NearbyPlace> = persistentListOf(),
        val isLoading: Boolean = false,
        /** The first answer for a query came; until then the last results stay (no flash of an empty screen). */
        val hasLoaded: Boolean = false,
    ) {
        /** Long enough to search: the results replace the grid. */
        val isSearching: Boolean get() = query.trim().removePrefix("@").length >= UserRepository.MIN_SEARCH_LENGTH
    }

    @Immutable
    data class State(
        val status: Status = Status.Unknown,
        val places: ImmutableList<NearbyPlace> = persistentListOf(),
        val isLoading: Boolean = false,
        /** Why the places couldn't load when there are none to show: the error screen with a retry. */
        val loadError: UiText? = null,
        val people: PeopleState = PeopleState(),
        val search: SearchState = SearchState(),
        /**
         * The people sheet, of the people around or those the search found. It stays open while a profile opened from
         * it covers Nearby: the sheet slides away and comes back on the way back.
         */
        val isPeopleSheetOpen: Boolean = false,
        val message: UiText? = null,
        /** The server lists OpenStreetMap places (OSM_PINS_ENABLED); off, Nearby has only users' places and no OSM credit. */
        val isOsmCreditShown: Boolean = true,
    ) {
        /** The people the row and the sheet show: those the search found while it's in use, else the people around. */
        val shownPeople: PeopleState get() = if (search.isSearching) search.people else people
    }

    sealed interface Action {
        /** The permission as Nearby opens or the app comes back, e.g. from Settings. */
        data class PermissionChecked(val permission: LocationPermission) : Action

        /** The answer to the location prompt. */
        data class PermissionResult(val permission: LocationPermission) : Action
        data object AllowLocation : Action
        data object Refresh : Action
        data object LoadMore : Action
        data class MarkHot(val place: NearbyPlace) : Action
        data class QueryChanged(val query: String) : Action
        data object OpenPeople : Action
        data object ClosePeople : Action
        data object LoadMorePeople : Action
        data class ToggleFollow(val person: Person) : Action
        data object DismissMessage : Action
    }

    sealed interface Event {
        data object RequestPermission : Event
        data object OpenAppSettings : Event
    }
}
