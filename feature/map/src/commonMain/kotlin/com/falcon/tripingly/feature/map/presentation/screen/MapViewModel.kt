package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.trips.MarkerChangeFailure
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
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
import kotlinx.coroutines.channels.Channel
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

/**
 * One trip on the map. Days and markers come from the cached trip, reloaded from
 * the server on open; owners and editors add and remove stops and days. Stop
 * changes show at once and are saved in the background; one that fails is
 * undone and explained, with Retry when the connection was the problem. Day
 * changes wait for the server.
 */
class MapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val markerRepository: MarkerRepository,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
) : ViewModel() {

    private val ui = MutableStateFlow(UiState())

    private val trip: StateFlow<TripDetails?> =
        tripRepository.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<State> = combine(trip, ui, ::toState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toState(null, ui.value))

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadTrip()
        watchFailedChanges()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.RequestLocationPermission -> sendEvent(Event.RequestPermission)
            is Action.OnPermissionResult -> handlePermissionResult(action.isGranted)
            is Action.CenterOnUserLocation -> centerOnUserLocation()
            is Action.NavigateToLocation -> navigateTo(action.coordinates, action.zoom)
            is Action.OnMapClick -> addMarker(action.coordinates)
            is Action.OnMarkerClick -> selectMarker(action.marker)
            is Action.OnRemoveMarker -> removeMarker(action.markerId)
            is Action.ClearAllMarkers -> ui.update { it.copy(confirm = Confirm.ClearDay) }
            is Action.DeleteDay -> ui.update { it.copy(confirm = Confirm.DeleteDay) }
            is Action.OnConfirm -> confirm()
            is Action.OnDismissConfirm -> ui.update { it.copy(confirm = null) }
            is Action.AddDay -> addDay()
            is Action.DismissError -> ui.update { it.copy(message = null, failedChanges = emptyList()) }
            is Action.RetryFailedChanges -> retryFailedChanges()
            is Action.OnCameraMove -> ui.update { it.copy(cameraTarget = action.coordinates, zoomLevel = action.zoom) }
            is Action.OnDaySelected -> selectDay(action.dayIndex)
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
            isPermissionGranted = ui.isPermissionGranted,
            isLoadingLocation = ui.isLoadingLocation,
            selectedMarker = markers.firstOrNull { it.id == ui.selectedMarkerId },
            message = ui.message,
            canRetry = ui.message != null && ui.failedChanges.isNotEmpty(),
            isWaitingForFirstLocation = ui.isWaitingForFirstLocation,
            confirm = ui.confirm,
        )
    }

    private fun loadTrip() {
        viewModelScope.launch {
            val result = tripRepository.refreshTrip(tripId)
            ui.update { it.copy(isLoadingTrip = false, message = (result as? AppResult.Error)?.error?.toUiText()) }
        }
        // Show the first day's stops when the trip opens.
        viewModelScope.launch {
            val details = trip.first { it != null && it.days.isNotEmpty() }
            details?.days?.firstOrNull()?.let(::centerOnDay)
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
        currentDays().getOrNull(index)?.let(::centerOnDay)
    }

    /** From the map or a chip: highlight the stop and bring it to the middle. */
    private fun selectMarker(marker: MapMarker) {
        ui.update { it.copy(selectedMarkerId = marker.id) }
        navigateTo(marker.position, ui.value.zoomLevel)
    }

    private fun centerOnDay(day: TripDay) {
        val first = day.markers.firstOrNull() ?: return
        ui.update { it.copy(hasCenteredOnTrip = true) }
        navigateTo(Coordinates(first.location.lat, first.location.lng), ui.value.zoomLevel.coerceAtLeast(13f))
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
                viewModelScope.launch { day.markers.forEach { markerRepository.deleteMarker(it.id) } }
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

    private fun handlePermissionResult(isGranted: Boolean) {
        ui.update { it.copy(isPermissionGranted = isGranted) }
        if (isGranted) {
            startLocationUpdates()
        } else {
            ui.update { it.copy(message = UiText.Resource(Res.string.error_location_denied_manual)) }
        }
    }

    private fun centerOnUserLocation() {
        val current = ui.value.currentLocation
        if (current != null) {
            navigateTo(current, 15f)
        } else {
            ui.update { it.copy(isWaitingForFirstLocation = true) }
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
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarkerId: String? = null,
        val message: UiText? = null,
        /** Stop changes undone because the connection failed; Retry sends them again. */
        val failedChanges: List<MarkerChangeFailure> = emptyList(),
        val isWaitingForFirstLocation: Boolean = false,
        val hasCenteredOnTrip: Boolean = false,
        val confirm: Confirm? = null,
    )

    /** A day tab: [number] counts from 1; [date] is null when the trip has no dates. */
    @Immutable
    data class DayTab(val id: String, val number: Int, val date: LocalDate?)

    enum class Confirm { ClearDay, DeleteDay }

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
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarker: MapMarker? = null,
        val message: UiText? = null,
        /** [message] is about stop changes that didn't save; Retry sends them again. */
        val canRetry: Boolean = false,
        val isWaitingForFirstLocation: Boolean = false,
        val confirm: Confirm? = null,
    )

    sealed interface Event {
        data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : Event
        data object RequestPermission : Event
    }

    sealed interface Action {
        data object RequestLocationPermission : Action
        data class OnPermissionResult(val isGranted: Boolean) : Action
        data class NavigateToLocation(val coordinates: Coordinates, val zoom: Float = 13f) : Action
        data object CenterOnUserLocation : Action
        data class OnMapClick(val coordinates: Coordinates) : Action
        data class OnMarkerClick(val marker: MapMarker) : Action
        data class OnRemoveMarker(val markerId: String) : Action
        data object ClearAllMarkers : Action
        data object AddDay : Action
        data object DeleteDay : Action
        data object OnConfirm : Action
        data object OnDismissConfirm : Action
        data object DismissError : Action
        data object RetryFailedChanges : Action
        data class OnCameraMove(val coordinates: Coordinates, val zoom: Float) : Action
        data class OnDaySelected(val dayIndex: Int) : Action
    }
}
