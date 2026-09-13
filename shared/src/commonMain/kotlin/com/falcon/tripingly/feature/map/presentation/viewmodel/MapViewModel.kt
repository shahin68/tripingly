package com.falcon.tripingly.feature.map.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiAction
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiEvent
import com.falcon.tripingly.feature.map.presentation.mvi.MapUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MapViewModel(
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _events = Channel<MapUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: MapUiAction) {
        when (action) {
            is MapUiAction.RequestLocationPermission -> {
                sendEvent(MapUiEvent.RequestPermission)
            }
            is MapUiAction.OnPermissionResult -> {
                handlePermissionResult(action.isGranted)
            }
            is MapUiAction.CenterOnUserLocation -> {
                centerOnUserLocation()
            }
            is MapUiAction.NavigateToLocation -> {
                navigateTo(action.coordinates, action.zoom)
            }
            is MapUiAction.OnMapClick -> {
                addTripMarker(action.coordinates)
            }
            is MapUiAction.OnMarkerClick -> {
                _uiState.update { it.copy(selectedMarker = action.marker) }
            }
            is MapUiAction.OnRemoveMarker -> {
                removeTripMarker(action.markerId)
            }
            is MapUiAction.ClearAllMarkers -> {
                _uiState.update { it.copy(markers = emptyList(), selectedMarker = null) }
                sendEvent(MapUiEvent.ShowSnackbar("Cleared all trip stops"))
            }
            is MapUiAction.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = isGranted) }
        if (isGranted) {
            fetchCurrentLocation(centerOnTarget = true)
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
        } else {
            fetchCurrentLocation(centerOnTarget = true)
        }
    }

    private fun fetchCurrentLocation(centerOnTarget: Boolean) {
        viewModelScope.launch(dispatchers.main) {
            _uiState.update { it.copy(isLoadingLocation = true, errorMessage = null) }
            try {
                when (val result = getCurrentLocationUseCase()) {
                    is AppResult.Success -> {
                        val coords = result.data
                        _uiState.update { state ->
                            state.copy(
                                currentLocation = coords,
                                cameraTarget = if (centerOnTarget) coords else state.cameraTarget,
                                zoomLevel = if (centerOnTarget) 15f else state.zoomLevel,
                                isLoadingLocation = false
                            )
                        }
                        if (centerOnTarget) {
                            sendEvent(MapUiEvent.AnimateCamera(coords, 15f))
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
        sendEvent(MapUiEvent.AnimateCamera(coordinates, zoom))
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
            snippet = "Lat: ${formatCoordinate(coordinates.latitude)}, Lng: ${formatCoordinate(coordinates.longitude)}"
        )

        _uiState.update { state ->
            state.copy(
                markers = state.markers + newMarker,
                selectedMarker = newMarker
            )
        }
        sendEvent(MapUiEvent.ShowSnackbar("Added Stop #$nextOrder to trip itinerary"))
    }

    private fun removeTripMarker(markerId: String) {
        _uiState.update { state ->
            val remaining = state.markers.filterNot { it.id == markerId }
            // Re-number sequentially to maintain trip order integrity
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
        sendEvent(MapUiEvent.ShowSnackbar("Removed stop from trip"))
    }

    private fun formatCoordinate(value: Double): String {
        val rounded = kotlin.math.round(value * 10000) / 10000.0
        return rounded.toString()
    }

    private fun sendEvent(event: MapUiEvent) {
        viewModelScope.launch(dispatchers.main) {
            _events.send(event)
        }
    }
}
