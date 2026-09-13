package com.falcon.tripingly.feature.map.presentation.mvi

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker

@Immutable
data class MapUiState(
    val currentLocation: Coordinates? = null,
    val cameraTarget: Coordinates = Coordinates.Paris,
    val zoomLevel: Float = 13f,
    val markers: List<MapMarker> = emptyList(),
    val isPermissionGranted: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val selectedMarker: MapMarker? = null,
    val errorMessage: String? = null
)
