package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    selectedMarkerId: String?,
    isMyLocationEnabled: Boolean,
    onCameraMove: (Coordinates, Float) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit
) {
    val initialLatLng = remember { LatLng(cameraTarget.latitude, cameraTarget.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, zoomLevel)
    }

    // Sync camera position back to ViewModel when it stops moving
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val position = cameraPositionState.position
            onCameraMove(
                Coordinates(position.target.latitude, position.target.longitude),
                position.zoom
            )
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    val mapProperties = remember(isMyLocationEnabled) {
        MapProperties(
            isMyLocationEnabled = isMyLocationEnabled
        )
    }

    // Animate camera when cameraTarget or zoomLevel changes externally
    LaunchedEffect(cameraTarget, zoomLevel) {
        val targetLatLng = LatLng(cameraTarget.latitude, cameraTarget.longitude)
        // Only animate if the target is significantly different from current camera state
        // to avoid jitter when syncing back and forth.
        val current = cameraPositionState.position
        val latDiff = Math.abs(current.target.latitude - targetLatLng.latitude)
        val lngDiff = Math.abs(current.target.longitude - targetLatLng.longitude)
        val zoomDiff = Math.abs(current.zoom - zoomLevel)
        
        if (latDiff > 0.00001 || lngDiff > 0.00001 || zoomDiff > 0.01) {
            if (cameraPositionState.projection == null) {
                // The map isn't drawn yet: start it there instead of animating.
                cameraPositionState.position = CameraPosition.fromLatLngZoom(targetLatLng, zoomLevel)
            } else {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel),
                    durationMs = 800
                )
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        // Dark map in dark mode, so the light status bar icons stay readable.
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapClick = { latLng ->
            onMapClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
        }
    ) {
        markers.forEach { marker ->
            key(marker.id) {
                val position = LatLng(marker.position.latitude, marker.position.longitude)
                val markerState = rememberUpdatedMarkerState(position = position)
                val isSelected = marker.id == selectedMarkerId
                LaunchedEffect(isSelected, marker.title) {
                    if (isSelected) markerState.showInfoWindow()
                }

                // The icon is drawn once and redrawn only when its number or color changes.
                MarkerComposable(
                    marker.orderNumber,
                    marker.color,
                    state = markerState,
                    title = marker.title,
                    anchor = Offset(0.5f, 1f),
                    onClick = {
                        onMarkerClick(marker)
                        false // Return false to show standard info window as well
                    }
                ) {
                    TripMarkerIcon(
                        orderNumber = marker.orderNumber,
                        color = Color(marker.color)
                    )
                }
            }
        }
    }
}
