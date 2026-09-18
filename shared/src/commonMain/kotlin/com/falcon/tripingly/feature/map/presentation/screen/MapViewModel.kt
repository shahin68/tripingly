package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

class MapViewModel(
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.RequestLocationPermission -> {
                sendEvent(Event.RequestPermission)
            }
            is Action.OnPermissionResult -> {
                handlePermissionResult(action.isGranted)
            }
            is Action.CenterOnUserLocation -> {
                centerOnUserLocation()
            }
            is Action.NavigateToLocation -> {
                navigateTo(action.coordinates, action.zoom)
            }
            is Action.OnMapClick -> {
                addTripMarker(action.coordinates)
            }
            is Action.OnMarkerClick -> {
                _uiState.update { it.copy(selectedMarker = action.marker) }
            }
            is Action.OnRemoveMarker -> {
                removeTripMarker(action.markerId)
            }
            is Action.ClearAllMarkers -> {
                _uiState.update { it.copy(markers = emptyList(), selectedMarker = null) }
                sendEvent(Event.ShowSnackbar("Cleared all trip stops"))
            }
            is Action.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = isGranted) }
        if (isGranted) {
            startLocationUpdates()
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = "Location permission was denied. You can still explore destinations manually."
                )
            }
        }
    }

    private fun centerOnUserLocation() {
        val current = _uiState.value.currentLocation
        if (current != null) {
            navigateTo(current, 15f)
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
                                val isFirstLocation = state.currentLocation == null
                                state.copy(
                                    currentLocation = coords,
                                    cameraTarget = if (isFirstLocation) coords else state.cameraTarget,
                                    zoomLevel = if (isFirstLocation) 15f else state.zoomLevel,
                                    isLoadingLocation = false
                                )
                            }
                        }
                        is AppResult.Error -> {
                            val errorText = when (val error = result.error) {
                                is DataError.Location.PermissionDenied -> "Location permission required to detect your location."
                                is DataError.Location.ServiceDisabled -> "Location services are disabled on your device."
                                is DataError.Location.Unavailable -> "Unable to determine current location. Showing default view."
                                is DataError.Location.Unknown -> error.message ?: "Failed to acquire location."
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
                        errorMessage = e.message ?: "An unexpected error occurred while fetching location."
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
        val currentMarkers = _uiState.value.markers
        val nextOrder = currentMarkers.size + 1
        val markerId = "stop_${nextOrder}_${coordinates.latitude.hashCode()}_${coordinates.longitude.hashCode()}"
        val newMarker = MapMarker(
            id = markerId,
            position = coordinates,
            title = "Stop #$nextOrder",
            orderNumber = nextOrder,
            snippet = "Lat: ${formatCoordinate(coordinates.latitude)}, Lng: ${
                formatCoordinate(
                    coordinates.longitude
                )
            }"
        )

        _uiState.update { state ->
            state.copy(
                markers = state.markers + newMarker,
                selectedMarker = newMarker
            )
        }
        sendEvent(Event.ShowSnackbar("Added Stop #$nextOrder to trip itinerary"))
    }

    private fun removeTripMarker(markerId: String) {
        _uiState.update { state ->
            val remaining = state.markers.filterNot { it.id == markerId }
            val reordered = remaining.mapIndexed { index, marker ->
                val newOrder = index + 1
                marker.copy(
                    orderNumber = newOrder,
                    title = "Stop #$newOrder"
                )
            }
            state.copy(
                markers = reordered,
                selectedMarker = if (state.selectedMarker?.id == markerId) null else state.selectedMarker
            )
        }
        sendEvent(Event.ShowSnackbar("Removed stop from trip"))
    }

    private fun formatCoordinate(value: Double): String {
        val rounded = round(value * 10000) / 10000.0
        return rounded.toString()
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch(dispatchers.main) {
            _events.send(event)
        }
    }

    @Immutable
    data class State(
        val currentLocation: Coordinates? = null,
        val cameraTarget: Coordinates = Coordinates.Paris,
        val zoomLevel: Float = 13f,
        val markers: List<MapMarker> = emptyList(),
        val isPermissionGranted: Boolean = false,
        val isLoadingLocation: Boolean = false,
        val selectedMarker: MapMarker? = null,
        val errorMessage: String? = null
    )

    sealed interface Event {
        data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : Event
        data object RequestPermission : Event
        data class ShowSnackbar(val message: String) : Event
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
    }
}