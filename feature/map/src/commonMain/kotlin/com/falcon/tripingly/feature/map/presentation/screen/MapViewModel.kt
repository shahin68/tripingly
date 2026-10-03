package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.feature.map.domain.usecase.GetTripByIdUseCase
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.SaveMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkersForDayUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*
import kotlin.coroutines.cancellation.CancellationException

class MapViewModel(
    private val tripId: String,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val getMarkersForDayUseCase: GetMarkersForDayUseCase,
    private val saveMarkerUseCase: SaveMarkerUseCase,
    private val deleteMarkerUseCase: DeleteMarkerUseCase,
    private val deleteMarkersForDayUseCase: DeleteMarkersForDayUseCase,
    private val getTripByIdUseCase: GetTripByIdUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var markersJob: Job? = null

    init {
        loadTripDetails()
        observeMarkers()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.RequestLocationPermission -> sendEvent(Event.RequestPermission)
            is Action.OnPermissionResult -> handlePermissionResult(action.isGranted)
            is Action.CenterOnUserLocation -> centerOnUserLocation()
            is Action.NavigateToLocation -> navigateTo(action.coordinates, action.zoom)
            is Action.OnMapClick -> addTripMarker(action.coordinates)
            is Action.OnMarkerClick -> _uiState.update { it.copy(selectedMarker = action.marker) }
            is Action.OnRemoveMarker -> removeTripMarker(action.markerId)
            is Action.ClearAllMarkers -> clearMarkers()
            is Action.DismissError -> _uiState.update { it.copy(errorMessage = null) }
            is Action.OnCameraMove -> _uiState.update { it.copy(cameraTarget = action.coordinates, zoomLevel = action.zoom) }
            is Action.OnDaySelected -> {
                _uiState.update { it.copy(activeDayIndex = action.dayIndex) }
                observeMarkers()
            }
        }
    }

    private fun loadTripDetails() {
        viewModelScope.launch {
            val trip = getTripByIdUseCase(tripId)
            _uiState.update { it.copy(
                tripName = trip?.name ?: "Unknown Trip",
                startDate = trip?.startDate,
                endDate = trip?.endDate
            ) }
        }
    }

    private fun observeMarkers() {
        markersJob?.cancel()
        markersJob = viewModelScope.launch {
            getMarkersForDayUseCase(tripId, _uiState.value.activeDayIndex).collect { markers ->
                _uiState.update { it.copy(markers = markers) }
            }
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = isGranted) }
        if (isGranted) {
            startLocationUpdates()
        } else {
            viewModelScope.launch {
                val error = getString(Res.string.error_location_denied_manual)
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    private fun centerOnUserLocation() {
        val current = _uiState.value.currentLocation
        if (current != null) {
            navigateTo(current, 15f)
        } else {
            _uiState.update { it.copy(isWaitingForFirstLocation = true) }
        }
    }

    private fun startLocationUpdates() {
        viewModelScope.launch(dispatchers.main) {
            _uiState.update { it.copy(isLoadingLocation = true, errorMessage = null) }
            try {
                getCurrentLocationUseCase().collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            val coords = result.data
                            _uiState.update { state ->
                                val shouldZoom = (state.currentLocation == null) || state.isWaitingForFirstLocation
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
                            val errorText = when (result.error) {
                                is DataError.Location.PermissionDenied -> getString(Res.string.error_location_permission_required)
                                is DataError.Location.ServiceDisabled -> getString(Res.string.error_location_services_disabled)
                                is DataError.Location.Unavailable -> getString(Res.string.error_location_unavailable)
                                is DataError.Location.Unknown -> result.error.message 
                                    ?: getString(Res.string.error_location_unknown)
                            }
                            _uiState.update {
                                it.copy(isLoadingLocation = false, errorMessage = errorText)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingLocation = false,
                        errorMessage = e.message ?: getString(Res.string.error_location_unexpected)
                    )
                }
            }
        }
    }

    private fun navigateTo(coordinates: Coordinates, zoom: Float) {
        _uiState.update {
            it.copy(cameraTarget = coordinates, zoomLevel = zoom)
        }
        sendEvent(Event.AnimateCamera(coordinates, zoom))
    }

    private fun addTripMarker(coordinates: Coordinates) {
        viewModelScope.launch {
            val currentMarkers = _uiState.value.markers
            val nextOrder = currentMarkers.size + 1
            val markerId = "stop_${nextOrder}_${coordinates.latitude.hashCode()}_${coordinates.longitude.hashCode()}"
            
            val title = getString(Res.string.map_stop_title_format, nextOrder)
            val snippet = getString(Res.string.map_stop_snippet_format, coordinates.latitude, coordinates.longitude)
            
            val newMarker = MapMarker(
                id = markerId,
                position = coordinates,
                title = title,
                orderNumber = nextOrder,
                snippet = snippet,
                color = 0xFF2196F3
            )
            saveMarkerUseCase(tripId, _uiState.value.activeDayIndex, newMarker)
        }
    }

    private fun removeTripMarker(markerId: String) {
        viewModelScope.launch {
            deleteMarkerUseCase(markerId)
        }
    }

    private fun clearMarkers() {
        viewModelScope.launch {
            deleteMarkersForDayUseCase(tripId, _uiState.value.activeDayIndex)
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch(dispatchers.main) {
            _events.send(event)
        }
    }

    @Immutable
    data class State(
        val tripName: String = "",
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val activeDayIndex: Int = 0,
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        val markers: List<MapMarker> = emptyList(),
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarker: MapMarker? = null,
        val errorMessage: String? = null,
        val isWaitingForFirstLocation: Boolean = false
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
        data object DismissError : Action
        data class OnCameraMove(val coordinates: Coordinates, val zoom: Float) : Action
        data class OnDaySelected(val dayIndex: Int) : Action
    }
}
