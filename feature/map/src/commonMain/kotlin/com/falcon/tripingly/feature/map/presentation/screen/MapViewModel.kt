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
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
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
import com.falcon.tripingly.feature.map.presentation.component.MapPlaceSource
import com.falcon.tripingly.feature.map.presentation.permission.LocationPermission
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.error_location_denied_manual
import com.falcon.tripingly.feature.map.generated.resources.error_location_permission_required
import com.falcon.tripingly.feature.map.generated.resources.error_location_services_disabled
import com.falcon.tripingly.feature.map.generated.resources.error_location_unavailable
import com.falcon.tripingly.feature.map.generated.resources.error_location_unexpected
import com.falcon.tripingly.feature.map.generated.resources.error_location_unknown
import com.falcon.tripingly.feature.map.generated.resources.map_change_not_saved
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One trip on the map. Days and markers come from the cached trip, reloaded from
 * the server on open; owners and editors add and remove stops and days. Stop
 * changes show at once and are saved in the background; one that fails is
 * undone and explained, with Retry when the connection was the problem. Day
 * changes wait for the server. Place pins load by map square (see [MapPlaceSource]);
 * a tapped place opens a card that adds it to the open day.
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

    private var prefetchJob: Job? = null
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
            is Action.OnMapClick -> onMapClick(action.coordinates)
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
            placeSource = MapPlaceSource(placeRepository, stopPlaceIds(details)),
            selectedPlace = ui.selectedPlace,
            selectedPlaceDetails = ui.selectedPlaceDetails?.takeIf { it.id == ui.selectedPlace?.id },
            selectedPlaceAddress = ui.selectedAddress?.address,
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
        // No name: the server names the stop after the place or address there.
        viewModelScope.launch {
            markerRepository.addMarker(tripId, day.id, NewMarker("", GeoPoint(coordinates.latitude, coordinates.longitude)))
        }
    }

    /**
     * The map loads the squares it shows by itself. Once the camera has rested, the ring of squares around
     * the view loads too (the ones not loaded yet, in one request), so a pan finds its places ready.
     */
    private fun onCameraMove(coordinates: Coordinates, zoom: Float, bounds: GeoBounds?) {
        ui.update { it.copy(cameraTarget = coordinates, zoomLevel = zoom) }
        if (bounds == null) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MILLIS)
            placeRepository.placesIn(PlaceSquare.covering(bounds, floor(zoom).toInt(), ring = 1))
        }
    }

    /**
     * Place pins are drawn into the map's tiles, so a tap is matched here against the places the map drew:
     * the nearest pin or cluster within a finger's reach opens (a cluster zooms in), else the tap adds a stop.
     */
    private fun onMapClick(coordinates: Coordinates) {
        viewModelScope.launch {
            val zoom = ui.value.zoomLevel
            when (val target = pinAt(GeoPoint(coordinates.latitude, coordinates.longitude), zoom)) {
                is PinTarget.Place -> openPlace(target.place)
                is PinTarget.Cluster -> navigateTo(
                    Coordinates(target.cluster.location.lat, target.cluster.location.lng),
                    zoom + CLUSTER_ZOOM_STEP,
                )
                null -> addMarker(coordinates)
            }
        }
    }

    private suspend fun pinAt(point: GeoPoint, zoom: Float): PinTarget? {
        // Screen points per degree of longitude; a degree of latitude spans 1 / cos(latitude) times more.
        val pointsPerDegree = 256 * 2.0.pow(zoom.toDouble()) / 360
        val reach = TAP_REACH_POINTS / pointsPerDegree
        val near = GeoBounds(point.lat - reach, point.lng - reach, point.lat + reach, point.lng + reach)
        val drawn = placeRepository.loadedPlacesIn(PlaceSquare.covering(near, floor(zoom).toInt())).values
        val stopPlaceIds = stopPlaceIds(trip.value)
        fun distance(location: GeoPoint): Double {
            val dx = (location.lng - point.lng) * pointsPerDegree
            val dy = (location.lat - point.lat) * pointsPerDegree / cos(point.lat * PI / 180)
            return sqrt(dx * dx + dy * dy)
        }
        val places = drawn.flatMap { it.places }.filter { it.id !in stopPlaceIds }.map { PinTarget.Place(it) to distance(it.location) }
        val clusters = drawn.flatMap { it.clusters }.map { PinTarget.Cluster(it) to distance(it.location) }
        return (places + clusters).filter { (_, distance) -> distance <= TAP_REACH_POINTS }.minByOrNull { (_, distance) -> distance }?.first
    }

    /** A place that is already a stop shows as the stop only. */
    private fun stopPlaceIds(details: TripDetails?): Set<String> =
        details?.days.orEmpty().flatMapTo(HashSet()) { day -> day.markers.map { it.placeId } }

    private sealed interface PinTarget {
        data class Place(val place: MapPlace) : PinTarget
        data class Cluster(val cluster: PlaceCluster) : PinTarget
    }

    /** Centers the place and shows its card at once with what the pin knows, then adds the place's details. */
    private fun openPlace(place: MapPlace, zoom: Float = ui.value.zoomLevel) {
        ui.update { it.copy(selectedPlace = place, selectedAddress = null, selectedMarkerId = null) }
        navigateTo(Coordinates(place.location.lat, place.location.lng), zoom)
        placeDetailsJob?.cancel()
        placeDetailsJob = viewModelScope.launch {
            placeRepository.place(place.id).onSuccess { details -> ui.update { it.copy(selectedPlaceDetails = details) } }
        }
    }

    /** An address from search has no place of ours yet: its card shows the address and adds it by name. */
    private fun openAddress(result: PlaceSearchResult) {
        placeDetailsJob?.cancel()
        val place = MapPlace(
            id = "",
            name = result.name,
            category = PlaceCategory.Other,
            location = result.location,
            isTripinly = false,
            likeCount = 0,
        )
        ui.update { it.copy(selectedPlace = place, selectedAddress = result, selectedPlaceDetails = null, selectedMarkerId = null) }
        navigateTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
    }

    private fun closePlace() {
        placeDetailsJob?.cancel()
        ui.update { it.copy(selectedPlace = null, selectedAddress = null, selectedPlaceDetails = null) }
    }

    /** A stop at the place or address, named after it; shows at once like a tap on the map. */
    private fun addPlace() {
        val place = ui.value.selectedPlace ?: return
        val address = ui.value.selectedAddress
        val day = activeDay() ?: return
        closePlace()
        if (!canEdit()) return
        val marker = if (address != null) {
            NewMarker(address.name, address.location, osm = address.osm)
        } else {
            NewMarker(place.name, place.location, placeId = place.id)
        }
        viewModelScope.launch { markerRepository.addMarker(tripId, day.id, marker) }
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

    /** One of our places or an address opens its card; a city or larger area only moves the map there. */
    private fun showSearchResult(result: PlaceSearchResult) {
        closeSearch()
        when {
            result.place != null -> openPlace(result.place!!, result.type.zoom())
            result.type in ADDRESS_TYPES -> openAddress(result)
            else -> navigateTo(Coordinates(result.location.lat, result.location.lng), result.type.zoom())
        }
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
        val selectedPlace: MapPlace? = null,
        val selectedPlaceDetails: PlaceDetails? = null,
        /** The address picked from search whose card is open; [selectedPlace] is drawn from it. */
        val selectedAddress: PlaceSearchResult? = null,
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
        /** Where the map gets its place pins; null in previews. */
        val placeSource: MapPlaceSource? = null,
        /** The place whose card is open. */
        val selectedPlace: MapPlace? = null,
        /** Opening hours and website, once loaded. */
        val selectedPlaceDetails: PlaceDetails? = null,
        /** Street, city and country when the card is for an address from search. */
        val selectedPlaceAddress: String? = null,
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
        const val PREFETCH_DELAY_MILLIS = 300L
        /** Half of a 48-point touch target around the tap. */
        const val TAP_REACH_POINTS = 24.0
        /** Typing has paused; see the search rule in 07-architecture. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        /** Search results small enough to add as a stop. */
        val ADDRESS_TYPES = setOf(SearchResultType.Place, SearchResultType.House, SearchResultType.Street, SearchResultType.Other)
        /** A tapped cluster opens up into its places. */
        const val CLUSTER_ZOOM_STEP = 2f
    }
}
