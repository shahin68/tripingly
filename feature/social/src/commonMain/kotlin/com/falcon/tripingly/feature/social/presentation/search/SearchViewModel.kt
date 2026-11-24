package com.falcon.tripingly.feature.social.presentation.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.tips.Tip
import com.falcon.tripingly.core.data.tips.TipRepository
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Search tab (Shahin, 2026-11-23): spots around me in a grid ([NearbyPlaces]), and the search for people and spots
 * from the shell's top bar ([TypedSearch]); the people it finds show as their top three rows and a sheet of all ([FoundPeople],
 * only while searching, Shahin 2026-11-24). A tip card on top of the spots says people can be searched too, until it is
 * dismissed. A spot opens its own map.
 */
class SearchViewModel(
    socialRepository: SocialRepository,
    placeRepository: PlaceRepository,
    userRepository: UserRepository,
    getCurrentLocation: GetCurrentLocationUseCase,
    appConfigRepository: AppConfigRepository,
    private val tipRepository: TipRepository,
) : ViewModel() {

    /** The search field in the shell's top bar; each change comes back as [Action.QueryChanged]. */
    val queryField = TextFieldState()

    private val _uiState = MutableStateFlow(State(isOsmCreditShown = appConfigRepository.config.value.osmPinsEnabled))
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val places = NearbyPlaces(viewModelScope, _uiState, placeRepository, socialRepository, getCurrentLocation, ::showMessage)

    private val people = FoundPeople(viewModelScope, _uiState, userRepository, { places.point }, ::showMessage)

    private val search = TypedSearch(viewModelScope, _uiState, userRepository, placeRepository, { places.point }, people, ::showMessage)

    init {
        tipRepository.isDismissed(Tip.SearchPeople)
            .onEach { dismissed -> _uiState.update { it.copy(isPeopleTipShown = !dismissed) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.PermissionChecked -> places.onPermission(action.permission, isAnswer = false)
            is Action.PermissionResult -> places.onPermission(action.permission, isAnswer = true)
            Action.AllowLocation -> sendEvent(if (_uiState.value.status == Status.Blocked) Event.OpenAppSettings else Event.RequestPermission)
            Action.Refresh -> places.refresh()
            Action.LoadMore -> if (_uiState.value.search.isSearching) search.loadMore() else places.loadMore()
            is Action.QueryChanged -> search.onQuery(action.query)
            Action.OpenPeople -> _uiState.update { it.copy(isPeopleSheetOpen = true) }
            Action.ClosePeople -> _uiState.update { it.copy(isPeopleSheetOpen = false) }
            Action.LoadMorePeople -> people.loadMore()
            is Action.ToggleFollow -> people.toggleFollow(action.person)
            Action.DismissPeopleTip -> dismissPeopleTip()
            Action.SearchPeople -> {
                dismissPeopleTip()
                sendEvent(Event.OpenSearch)
            }
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Hidden at once; the store's answer only confirms it. */
    private fun dismissPeopleTip() {
        _uiState.update { it.copy(isPeopleTipShown = false) }
        viewModelScope.launch { tipRepository.dismiss(Tip.SearchPeople) }
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

    /** The people a search found, in the row and the sheet, with how many there are in all (up to 999, "999+"). */
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
        val search: SearchState = SearchState(),
        /**
         * The people sheet, of those the search found. It stays open while a profile opened from it covers the Search tab: the
         * sheet slides away and comes back on the way back.
         */
        val isPeopleSheetOpen: Boolean = false,
        val message: UiText? = null,
        /** The "Travel with friends" tip on top of the spots; false until the device says it wasn't dismissed, so it never flashes. */
        val isPeopleTipShown: Boolean = false,
        /** The server lists OpenStreetMap places (OSM_PINS_ENABLED); off, the Search tab has only users' places and no OSM credit. */
        val isOsmCreditShown: Boolean = true,
    )

    sealed interface Action {
        /** The permission as the Search tab opens or the app comes back, e.g. from Settings. */
        data class PermissionChecked(val permission: LocationPermission) : Action

        /** The answer to the location prompt. */
        data class PermissionResult(val permission: LocationPermission) : Action
        data object AllowLocation : Action
        data object Refresh : Action
        data object LoadMore : Action
        data class QueryChanged(val query: String) : Action
        data object OpenPeople : Action
        data object ClosePeople : Action
        data object LoadMorePeople : Action
        data class ToggleFollow(val person: Person) : Action

        /** The tip's ✕. */
        data object DismissPeopleTip : Action

        /** The tip's Search people: it goes away and the search field opens. */
        data object SearchPeople : Action
        data object DismissMessage : Action
    }

    sealed interface Event {
        data object RequestPermission : Event
        data object OpenAppSettings : Event

        /** Open the tab's search field in the shell's top bar, with the keyboard. */
        data object OpenSearch : Event
    }
}
