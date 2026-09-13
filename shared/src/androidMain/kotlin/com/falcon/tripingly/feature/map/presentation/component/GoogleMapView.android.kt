package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    onMapClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit
) {
    val initialLatLng = remember { LatLng(cameraTarget.latitude, cameraTarget.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, zoomLevel)
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    val mapProperties = remember {
        MapProperties(
            isMyLocationEnabled = false
        )
    }

    // Animate camera when cameraTarget or zoomLevel changes externally
    LaunchedEffect(cameraTarget, zoomLevel) {
        val targetLatLng = LatLng(cameraTarget.latitude, cameraTarget.longitude)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel),
            durationMs = 800
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapClick = { latLng ->
            onMapClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
        }
    ) {
        markers.forEach { marker ->
            val position = LatLng(marker.position.latitude, marker.position.longitude)
            val markerState = rememberUpdatedMarkerState(position = position)

            Marker(
                state = markerState,
                title = marker.title,
                snippet = marker.snippet ?: "Stop #${marker.orderNumber}",
                onClick = {
                    onMarkerClick(marker)
                    false // Return false to show standard info window as well
                }
            )
        }
    }
}
