package com.falcon.tripingly.feature.map.presentation.mvi

import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker

sealed interface MapUiAction {
    data object RequestLocationPermission : MapUiAction
    data class OnPermissionResult(val isGranted: Boolean) : MapUiAction
    data class NavigateToLocation(val coordinates: Coordinates, val zoom: Float = 13f) : MapUiAction
    data object CenterOnUserLocation : MapUiAction
    data class OnMapClick(val coordinates: Coordinates) : MapUiAction
    data class OnMarkerClick(val marker: MapMarker) : MapUiAction
    data class OnRemoveMarker(val markerId: String) : MapUiAction
    data object ClearAllMarkers : MapUiAction
    data object DismissError : MapUiAction
}
