package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.MarkerChangeFailure
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.domain.model.CameraFrame
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.permission.LocationPermission
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.error_location_services_disabled
import com.falcon.tripingly.feature.map.generated.resources.error_location_unavailable
import com.falcon.tripingly.feature.map.generated.resources.error_location_unexpected
import com.falcon.tripingly.feature.map.generated.resources.error_location_unknown
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
import com.falcon.tripingly.feature.map.generated.resources.map_stop_title_format
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor

/**
 * One trip on the map. Days and markers come from the cached trip, reloaded from
 * the server on open; owners and editors add and remove stops and days. Stop
 * changes show at once and are saved in the background; one that fails is
 * undone and explained, with Retry when the connection was the problem. Day
 * changes wait for the server. Places in view load when the camera rests; a place
 * opens a sheet that adds it to the open day.
 */
class MapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val placeRepository: PlaceRepository,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    private val trip: StateFlow<TripDetails?> =
        tripRepository.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<State> = combine(trip, ui, ::toState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toState(null, ui.value))

    private var placesJob: Job? = null
    private var placeDetailsJob: Job? = null
    private var searchJob: Job? = null

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadTrip()
        watchFailedChanges()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnPermissionChecked -> onPermissionChecked(action.permission)
            is Action.OnPermissionResult -> onPermissionResult(action.permission)
            is Action.OnConfirmLocationPrompt -> confirmLocationPrompt()
            is Action.OnDismissLocationPrompt -> ui.update { it.copy(locationPrompt = null, isWaitingForFirstLocation = false) }
            is Action.CenterOnUserLocation -> centerOnUserLocation()
            is Action.NavigateToLocation -> navigateTo(action.coordinates, action.zoom)
            is Action.OnMapClick -> addMarker(action.coordinates)
            is Action.OnMapLongClick -> if (canEdit() && activeDay() != null) ui.update { it.copy(pinToName = action.coordinates) }
            is Action.OnConfirmPinName -> addNamedPin(action.name)
            is Action.OnDismissPinName -> ui.update { it.copy(pinToName = null) }
            is Action.OnMarkerClick -> selectMarker(action.marker)
            is Action.OnRemoveMarker -> removeMarker(action.markerId)
            is Action.ClearAllMarkers -> ui.update { it.copy(confirm = Confirm.ClearDay) }
            is Action.DeleteDay -> ui.update { it.copy(confirm = Confirm.DeleteDay) }
            is Action.OnConfirm -> confirm()
            is Action.OnDismissConfirm -> ui.update { it.copy(confirm = null) }
            is Action.AddDay -> addDay()
            is Action.DismissError -> ui.update { it.copy(message = null, failedChanges = emptyList()) }
            is Action.RetryFailedChanges -> retryFailedChanges()
            is Action.OnCameraMove -> onCameraMove(action.coordinates, action.zoom, action.bounds)
            is Action.OnPlaceClick -> openPlace(action.place)
            is Action.OnClusterClick -> navigateTo(
                Coordinates(action.cluster.location.lat, action.cluster.location.lng),
                ui.value.zoomLevel + CLUSTER_ZOOM_STEP,
            )
            is Action.OnDismissPlace -> closePlace()
            is Action.OnAddPlaceToDay -> addPlace()
            is Action.OnOpenSearch -> ui.update { it.copy(isSearchOpen = true, selectedMarkerId = null) }
            is Action.OnSearchQueryChanged -> search(action.query)
            is Action.OnSearchResultClick -> showSearchResult(action.result)
            is Action.OnCloseSearch -> closeSearch()
            is Action.OnDaySelected -> selectDay(action.dayIndex)
            is Action.OnBackClick -> sendEvent(Event.NavigateBack)
        }
    }

    private fun toState(details: TripDetails?, ui: UiState): State {
        val days = details?.days.orEmpty()
        val activeIndex = ui.activeDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
        val markers = days.getOrNull(activeIndex)?.markers.orEmpty().mapIndexed { index, it -> it.toMapMarker(index + 1) }
        // A place that is already a stop shows as the stop only.
        val stopPlaceIds = days.flatMap { day -> day.markers.map { it.placeId } }.toSet()
        return State(
            tripName = details?.trip?.name.orEmpty(),
            days = days.map { DayTab(it.id, it.position + 1, it.date) }.toImmutableList(),
            activeDayIndex = activeIndex,
            markers = markers.toImmutableList(),
            canEdit = details?.trip?.role?.canEdit == true,
            isLoading = days.isEmpty() && ui.isLoadingTrip,
            isSaving = ui.isSaving,
            currentLocation = ui.currentLocation,
            cameraTarget = ui.cameraTarget,
            zoomLevel = ui.zoomLevel,
            frame = ui.frame,
            isPermissionGranted = ui.permission == LocationPermission.Granted,
            isLoadingLocation = ui.isLoadingLocation,
            selectedMarker = markers.firstOrNull { it.id == ui.selectedMarkerId },
            places = ui.places.filter { it.id !in stopPlaceIds }.toImmutableList(),
            clusters = ui.clusters.toImmutableList(),
            selectedPlace = ui.selectedPlace,
            selectedPlaceDetails = ui.selectedPlaceDetails?.takeIf { it.id == ui.selectedPlace?.id },
            isSearchOpen = ui.isSearchOpen,
            searchQuery = ui.searchQuery,
            searchResults = ui.searchResults.toImmutableList(),
            isSearching = ui.isSearching,
            isNamingPin = ui.pinToName != null,
            message = ui.message,
            canRetry = ui.message != null && ui.failedChanges.isNotEmpty(),
            isWaitingForFirstLocation = ui.isWaitingForFirstLocation,
            confirm = ui.confirm,
            locationPrompt = ui.locationPrompt,
        )
    }

    private fun loadTrip() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            ui.update { it.copy(isLoadingTrip = false, message = (result as? AppResult.Error)?.error?.toUiText()) }
        }
        // Show the open day's stops when the trip opens, or the whole trip's while that day has none,
        // or the trip's destination while it has no stops at all.
        viewModelScope.launch {
            val details = trip.first { it != null && it.days.isNotEmpty() } ?: return@launch
            val stops = activeDay()?.markers.orEmpty().ifEmpty { details.days.flatMap { it.markers } }
            val destination = details.destination
            if (stops.isEmpty() && destination != null) {
                ui.update { it.copy(hasCenteredOnTrip = true) }
                navigateTo(Coordinates(destination.location.lat, destination.location.lng), DESTINATION_ZOOM)
            } else {
                frameStops(stops)
            }
        }
    }

    private fun currentDays(): List<TripDay> = trip.value?.days.orEmpty()

    private fun activeDay(): TripDay? {
        val days = currentDays()
        return days.getOrNull(ui.value.activeDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0)))
    }

    private fun canEdit(): Boolean = trip.value?.trip?.role?.canEdit == true

    private fun selectDay(index: Int) {
        ui.update { it.copy(activeDayIndex = index, selectedMarkerId = null) }
        currentDays().getOrNull(index)?.let { frameStops(it.markers) }
    }

    /** From the map or a chip: highlight the stop and bring it to the middle. */
    private fun selectMarker(marker: MapMarker) {
        ui.update { it.copy(selectedMarkerId = marker.id) }
        navigateTo(marker.position, ui.value.zoomLevel)
    }

    /** The screen fits the camera to these stops; without stops the camera stays where it is. */
    private fun frameStops(markers: List<TripMarker>) {
        if (markers.isEmpty()) return
        val points = markers.map { Coordinates(it.location.lat, it.location.lng) }
        ui.update { it.copy(hasCenteredOnTrip = true, frame = CameraFrame(points, (it.frame?.id ?: 0) + 1)) }
    }

    private fun watchFailedChanges() {
        viewModelScope.launch {
            markerRepository.failures.collect { failure ->
                if (failure.tripId != tripId) return@collect
                ui.update {
                    if (failure.isConnectionProblem) {
                        it.copy(
                            message = UiText.Resource(Res.string.map_change_not_saved),
                            failedChanges = it.failedChanges + failure,
                        )
                    } else {
                        // Trying again won't change the server's mind.
                        it.copy(message = failure.error.toUiText(), failedChanges = emptyList())
                    }
                }
            }
        }
    }

    private fun retryFailedChanges() {
        val failed = ui.value.failedChanges
        ui.update { it.copy(message = null, failedChanges = emptyList()) }
        viewModelScope.launch { failed.forEach { markerRepository.retry(it) } }
    }

    /** Shows at once; see [MarkerRepository]. */
    private fun addMarker(coordinates: Coordinates) {
        val day = activeDay() ?: return
        if (!canEdit()) return
        viewModelScope.launch {
            val name = getString(Res.string.map_stop_title_format, day.markers.size + 1)
            markerRepository.addMarker(tripId, day.id, NewMarker(name, GeoPoint(coordinates.latitude, coordinates.longitude)))
        }
    }

    private fun onCameraMove(coordinates: Coordinates, zoom: Float, bounds: GeoBounds?) {
        ui.update { it.copy(cameraTarget = coordinates, zoomLevel = zoom) }
        if (bounds != null) loadPlaces(bounds, zoom)
    }

    /**
     * Waits until the camera has rested a moment, so a pan or a fling asks once. The pins on the map
     * stay until the new ones arrive, and stay as they are when the request fails. Pins still in view
     * stay on the map when the new answer leaves them out, unless the map zoomed out, where they'd crowd it.
     */
    private fun loadPlaces(bounds: GeoBounds, zoom: Float) {
        placesJob?.cancel()
        placesJob = viewModelScope.launch {
            delay(PLACES_DEBOUNCE_MILLIS)
            val zoomLevel = floor(zoom).toInt()
            placeRepository.placesInView(bounds, zoom).onSuccess { inView ->
                ui.update { state ->
                    val ids = inView.places.mapTo(HashSet()) { it.id }
                    val kept = if (zoomLevel >= state.placesZoomLevel) {
                        state.places.filter { it.id !in ids && it.location in bounds }
                    } else {
                        emptyList()
                    }
                    state.copy(places = inView.places + kept, placesZoomLevel = zoomLevel, clusters = inView.clusters)
                }
            }
        }
    }

    /** Centers the place and shows its card at once with what the pin knows, then adds the place's details. */
    private fun openPlace(place: MapPlace) {
        ui.update { it.copy(selectedPlace = place, selectedMarkerId = null) }
        navigateTo(Coordinates(place.location.lat, place.location.lng), ui.value.zoomLevel)
        placeDetailsJob?.cancel()
        placeDetailsJob = viewModelScope.launch {
            placeRepository.place(place.id).onSuccess { details -> ui.update { it.copy(selectedPlaceDetails = details) } }
        }
    }

    private fun closePlace() {
        placeDetailsJob?.cancel()
        ui.update { it.copy(selectedPlace = null, selectedPlaceDetails = null) }
    }

    /** A stop at the place, named after it; shows at once like a tap on the map. */
    private fun addPlace() {
        val place = ui.value.selectedPlace ?: return
        val day = activeDay() ?: return
        closePlace()
        if (!canEdit()) return
        viewModelScope.launch {
            markerRepository.addMarker(tripId, day.id, NewMarker(place.name, place.location, placeId = place.id))
        }
    }

    /**
     * Like the destination search: asks the server once typing pauses, ranked near the map's center.
     * A cleared field clears the results at once.
     */
    private fun search(query: String) {
        ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            ui.update { it.copy(isSearching = true) }
            val center = ui.value.cameraTarget
            val results = (placeRepository.search(query, GeoPoint(center.latitude, center.longitude)) as? AppResult.Success)?.data
            ui.update { it.copy(searchResults = results ?: it.searchResults, isSearching = false) }
        }
    }

    private fun showSearchResult(result: PlaceSearchResult) {
        closeSearch()
        navigateTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
    }

    private fun closeSearch() {
        searchJob?.cancel()
        ui.update { it.copy(isSearchOpen = false, searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    /** Close enough to see what was picked: a country whole, a café with its street. */
    private fun SearchResultType.zoom(): Float = when (this) {
        SearchResultType.Country -> 5f
        SearchResultType.State -> 7f
        SearchResultType.County -> 9f
        SearchResultType.City -> 12f
        SearchResultType.District, SearchResultType.Locality -> 14f
        SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other -> 17f
    }

    /** A long press drops a pin the user names; it shows at once like a tap. */
    private fun addNamedPin(name: String) {
        val location = ui.value.pinToName ?: return
        val day = activeDay() ?: return
        ui.update { it.copy(pinToName = null) }
        if (name.isBlank() || !canEdit()) return
        viewModelScope.launch {
            markerRepository.addMarker(tripId, day.id, NewMarker(name, GeoPoint(location.latitude, location.longitude)))
        }
    }

    private fun removeMarker(markerId: String) {
        if (!canEdit()) return
        ui.update { if (it.selectedMarkerId == markerId) it.copy(selectedMarkerId = null) else it }
        viewModelScope.launch { markerRepository.deleteMarker(markerId) }
    }

    private fun addDay() {
        if (!canEdit()) return
        save {
            tripRepository.addDay(tripId).also { result ->
                if (result is AppResult.Success) {
                    ui.update { it.copy(activeDayIndex = result.data.days.lastIndex, selectedMarkerId = null) }
                }
            }
        }
    }

    private fun confirm() {
        val confirm = ui.value.confirm ?: return
        val day = activeDay()
        ui.update { it.copy(confirm = null) }
        if (day == null || !canEdit()) return
        when (confirm) {
            Confirm.ClearDay -> {
                ui.update { it.copy(selectedMarkerId = null) }
                viewModelScope.launch { markerRepository.clearDay(tripId, day.id) }
            }
            Confirm.DeleteDay -> save {
                tripRepository.deleteDay(tripId, day.id).also { result ->
                    if (result is AppResult.Success) {
                        ui.update { it.copy(activeDayIndex = (it.activeDayIndex - 1).coerceAtLeast(0), selectedMarkerId = null) }
                    }
                }
            }
        }
    }

    /** Runs one day change at a time; a refusal shows the server's message. */
    private fun save(change: suspend () -> AppResult<*, DataError.Network>) {
        if (ui.value.isSaving) return
        ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = change()
            ui.update {
                it.copy(isSaving = false, message = (result as? AppResult.Error)?.error?.toUiText() ?: it.message)
            }
        }
    }

    /** The permission as read when the map opens or comes back to the front (e.g. from Settings). */
    private fun onPermissionChecked(permission: LocationPermission) {
        val before = ui.value
        ui.update { it.copy(permission = permission, hasCheckedPermission = true) }
        when {
            permission == LocationPermission.Granted && before.permission != LocationPermission.Granted ->
                startLocationUpdates()
            // Ask once when the map first opens; after that only the location button asks.
            permission == LocationPermission.NotAsked && !before.hasCheckedPermission ->
                sendEvent(Event.RequestPermission)
        }
    }

    /** The user answered the system prompt. */
    private fun onPermissionResult(permission: LocationPermission) {
        val before = ui.value
        ui.update { it.copy(permission = permission) }
        if (permission == LocationPermission.Granted) {
            if (before.permission != LocationPermission.Granted) startLocationUpdates()
        } else if (permission == LocationPermission.Blocked && before.isWaitingForFirstLocation) {
            // The location button asked, but the system can't show its prompt any more.
            ui.update { it.copy(locationPrompt = LocationPrompt.OpenSettings) }
        } else {
            ui.update {
                it.copy(
                    isWaitingForFirstLocation = false,
                    message = UiText.Resource(Res.string.error_location_denied_manual),
                )
            }
        }
    }

    /** The location button: moves to the user, asking for the permission first when it can. */
    private fun centerOnUserLocation() {
        when (ui.value.permission) {
            LocationPermission.Granted -> {
                val current = ui.value.currentLocation
                if (current != null) {
                    navigateTo(current, 15f)
                } else {
                    ui.update { it.copy(isWaitingForFirstLocation = true) }
                }
            }
            LocationPermission.NotAsked, null -> {
                ui.update { it.copy(isWaitingForFirstLocation = true) }
                sendEvent(Event.RequestPermission)
            }
            LocationPermission.ShouldExplain ->
                ui.update { it.copy(isWaitingForFirstLocation = true, locationPrompt = LocationPrompt.Explain) }
            LocationPermission.Blocked ->
                ui.update { it.copy(isWaitingForFirstLocation = true, locationPrompt = LocationPrompt.OpenSettings) }
        }
    }

    private fun confirmLocationPrompt() {
        val prompt = ui.value.locationPrompt ?: return
        ui.update { it.copy(locationPrompt = null) }
        when (prompt) {
            LocationPrompt.Explain -> sendEvent(Event.RequestPermission)
            LocationPrompt.OpenSettings -> sendEvent(Event.OpenAppSettings)
        }
    }

    private fun startLocationUpdates() {
        viewModelScope.launch(dispatchers.main) {
            ui.update { it.copy(isLoadingLocation = true, message = null) }
            try {
                getCurrentLocationUseCase().collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            val coords = result.data
                            ui.update { state ->
                                val shouldZoom = (state.currentLocation == null && !state.hasCenteredOnTrip) ||
                                    state.isWaitingForFirstLocation
                                if (shouldZoom) {
                                    sendEvent(Event.AnimateCamera(coords, 15f))
                                }
                                state.copy(
                                    currentLocation = coords,
                                    cameraTarget = if (shouldZoom) coords else state.cameraTarget,
                                    zoomLevel = if (shouldZoom) 15f else state.zoomLevel,
                                    isLoadingLocation = false,
                                    isWaitingForFirstLocation = false
                                )
                            }
                        }
                        is AppResult.Error -> {
                            val resource = when (result.error) {
                                is DataError.Location.PermissionDenied -> Res.string.error_location_permission_required
                                is DataError.Location.ServiceDisabled -> Res.string.error_location_services_disabled
                                is DataError.Location.Unavailable -> Res.string.error_location_unavailable
                                is DataError.Location.Unknown -> Res.string.error_location_unknown
                            }
                            ui.update { it.copy(isLoadingLocation = false, message = UiText.Resource(resource)) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.update {
                    it.copy(isLoadingLocation = false, message = UiText.Resource(Res.string.error_location_unexpected))
                }
            }
        }
    }

    private fun navigateTo(coordinates: Coordinates, zoom: Float) {
        ui.update { it.copy(cameraTarget = coordinates, zoomLevel = zoom) }
        sendEvent(Event.AnimateCamera(coordinates, zoom))
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch(dispatchers.main) {
            _events.send(event)
        }
    }

    /** [orderNumber] is the place in the day's list, which stays 1, 2, 3 while a delete waits for the reload. */
    private fun TripMarker.toMapMarker(orderNumber: Int) = MapMarker(
        id = id,
        position = Coordinates(location.lat, location.lng),
        title = name,
        orderNumber = orderNumber,
        snippet = time,
    )

    private data class UiState(
        val activeDayIndex: Int = 0,
        val isLoadingTrip: Boolean = true,
        val isSaving: Boolean = false,
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        /** Null until the screen has read it. */
        val permission: LocationPermission? = null,
        val hasCheckedPermission: Boolean = false,
        val locationPrompt: LocationPrompt? = null,
        val isLoadingLocation: Boolean = false,
        val selectedMarkerId: String? = null,
        val message: UiText? = null,
        /** Stop changes undone because the connection failed; Retry sends them again. */
        val failedChanges: List<MarkerChangeFailure> = emptyList(),
        val isWaitingForFirstLocation: Boolean = false,
        val hasCenteredOnTrip: Boolean = false,
        val frame: CameraFrame? = null,
        val confirm: Confirm? = null,
        val places: List<MapPlace> = emptyList(),
        /** The zoom level [places] were last loaded at. */
        val placesZoomLevel: Int = 0,
        val clusters: List<PlaceCluster> = emptyList(),
        val selectedPlace: MapPlace? = null,
        val selectedPlaceDetails: PlaceDetails? = null,
        val isSearchOpen: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<PlaceSearchResult> = emptyList(),
        val isSearching: Boolean = false,
        /** Where a long press dropped a pin that waits for its name. */
        val pinToName: Coordinates? = null,
    )

    /** A day tab: [number] counts from 1; [date] is null when the trip has no dates. */
    @Immutable
    data class DayTab(val id: String, val number: Int, val date: LocalDate?)

    enum class Confirm { ClearDay, DeleteDay }

    /** Shown when the location button can't simply ask: explain first (Android), or send the user to Settings. */
    enum class LocationPrompt { Explain, OpenSettings }

    @Immutable
    data class State(
        val tripName: String = "",
        val days: ImmutableList<DayTab> = persistentListOf(),
        val activeDayIndex: Int = 0,
        /** The active day's stops in order. */
        val markers: ImmutableList<MapMarker> = persistentListOf(),
        /** Owners and editors add and remove stops and days. */
        val canEdit: Boolean = false,
        /** The trip hasn't loaded yet (nothing cached). */
        val isLoading: Boolean = false,
        /** A day change is waiting for the server. Stop changes don't wait. */
        val isSaving: Boolean = false,
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        /** The stops to fit on screen: the open day's, framed on open, on a day change and on every return. */
        val frame: CameraFrame? = null,
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarker: MapMarker? = null,
        val message: UiText? = null,
        /** [message] is about stop changes that didn't save; Retry sends them again. */
        val canRetry: Boolean = false,
        val isWaitingForFirstLocation: Boolean = false,
        val confirm: Confirm? = null,
        val locationPrompt: LocationPrompt? = null,
        /** Places in view, without those already stops of this trip. */
        val places: ImmutableList<MapPlace> = persistentListOf(),
        /** Bubbles of Tripinly places while zoomed out. */
        val clusters: ImmutableList<PlaceCluster> = persistentListOf(),
        /** The place whose sheet is open. */
        val selectedPlace: MapPlace? = null,
        /** Opening hours and website, once loaded. */
        val selectedPlaceDetails: PlaceDetails? = null,
        /** The header shows the search field instead of the trip name. */
        val isSearchOpen: Boolean = false,
        val searchQuery: String = "",
        val searchResults: ImmutableList<PlaceSearchResult> = persistentListOf(),
        /** A search is waiting for the server. */
        val isSearching: Boolean = false,
        /** A long press dropped a pin; the name dialog is open. */
        val isNamingPin: Boolean = false,
    )

    sealed interface Event {
        data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : Event
        data object RequestPermission : Event
        data object OpenAppSettings : Event
        data object NavigateBack : Event
    }

    sealed interface Action {
        data class OnPermissionChecked(val permission: LocationPermission) : Action
        data class OnPermissionResult(val permission: LocationPermission) : Action
        data object OnConfirmLocationPrompt : Action
        data object OnDismissLocationPrompt : Action
        data class NavigateToLocation(val coordinates: Coordinates, val zoom: Float = 13f) : Action
        data object CenterOnUserLocation : Action
        data class OnMapClick(val coordinates: Coordinates) : Action
        data class OnMapLongClick(val coordinates: Coordinates) : Action
        data class OnConfirmPinName(val name: String) : Action
        data object OnDismissPinName : Action
        data class OnMarkerClick(val marker: MapMarker) : Action
        data class OnRemoveMarker(val markerId: String) : Action
        data object ClearAllMarkers : Action
        data object AddDay : Action
        data object DeleteDay : Action
        data object OnConfirm : Action
        data object OnDismissConfirm : Action
        data object DismissError : Action
        data object RetryFailedChanges : Action
        data class OnCameraMove(val coordinates: Coordinates, val zoom: Float, val bounds: GeoBounds? = null) : Action
        data class OnPlaceClick(val place: MapPlace) : Action
        data class OnClusterClick(val cluster: PlaceCluster) : Action
        data object OnDismissPlace : Action
        data object OnAddPlaceToDay : Action
        data object OnOpenSearch : Action
        data class OnSearchQueryChanged(val query: String) : Action
        data class OnSearchResultClick(val result: PlaceSearchResult) : Action
        data object OnCloseSearch : Action
        data class OnDaySelected(val dayIndex: Int) : Action
        data object OnBackClick : Action
    }

    private companion object {
        /** A city fills the screen. */
        const val DESTINATION_ZOOM = 12f
        /** Same wait as for search: the camera has rested. */
        const val PLACES_DEBOUNCE_MILLIS = 300L
        /** Typing has paused; see the search rule in 07-architecture. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}
