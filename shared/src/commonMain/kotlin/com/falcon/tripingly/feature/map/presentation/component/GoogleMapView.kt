package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    isMyLocationEnabled: Boolean,
    onMapClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit
)
