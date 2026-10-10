package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    selectedMarkerId: String?,
    places: List<MapPlace>,
    clusters: List<PlaceCluster>,
    isMyLocationEnabled: Boolean,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onPlaceClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
) {
    val initialLatLng = remember { LatLng(cameraTarget.latitude, cameraTarget.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, zoomLevel)
    }

    fun reportCamera() {
        val position = cameraPositionState.position
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds?.let {
            GeoBounds(it.southwest.latitude, it.southwest.longitude, it.northeast.latitude, it.northeast.longitude)
        }
        onCameraMove(
            Coordinates(position.target.latitude, position.target.longitude),
            position.zoom,
            bounds,
        )
    }

    // Sync camera position back to ViewModel when it stops moving
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) reportCamera()
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    val isDark = isSystemInDarkTheme()
    val mapProperties = remember(isMyLocationEnabled, isDark) {
        MapProperties(
            isMyLocationEnabled = isMyLocationEnabled,
            mapStyleOptions = MapStyleOptions(if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE),
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

    // The places drawn: the current ones, and removed ones until they have faded out.
    val shownPlaces = remember { mutableStateMapOf<String, MapPlace>() }
    val currentPlaceIds = remember(places) { places.mapTo(HashSet()) { it.id } }
    LaunchedEffect(places) { places.forEach { shownPlaces[it.id] = it } }


    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        // Dark map in dark mode, so the light status bar icons stay readable.
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapClick = { latLng ->
            onMapClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
        },
        onMapLongClick = { latLng ->
            onMapLongClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
        },
        // The first camera report comes before the map can tell its bounds, so places load from here.
        onMapLoaded = ::reportCamera,
    ) {
        // Every place pin is one of four icons: each is drawn once, not once per place. Built here,
        // once the map is ready to take icons.
        val osmPlaceIcon = rememberPlaceIcon(isTripinly = false, likeCount = 0)
        val hotSpotIcons = HOT_SPOT_LIKES.associate { likes ->
            hotSpotSize(likes) to rememberPlaceIcon(isTripinly = true, likeCount = likes)
        }
        // Places fade in, and fade out before they leave the map, as in Google Maps.
        shownPlaces.values.forEach { place ->
            key(place.id) {
                val isCurrent = place.id in currentPlaceIds
                val alpha = remember { Animatable(0f) }
                LaunchedEffect(isCurrent) {
                    alpha.animateTo(if (isCurrent) 1f else 0f, tween(PLACE_FADE_MILLIS))
                    if (!isCurrent) shownPlaces.remove(place.id)
                }
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(place.location.lat, place.location.lng)),
                    icon = if (place.isTripinly) hotSpotIcons.getValue(hotSpotSize(place.likeCount)) else osmPlaceIcon,
                    title = place.name,
                    alpha = alpha.value,
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        if (isCurrent) onPlaceClick(place)
                        true
                    },
                )
            }
        }
        clusters.forEach { cluster ->
            key(cluster.location) {
                MarkerComposable(
                    cluster.count,
                    state = rememberUpdatedMarkerState(position = LatLng(cluster.location.lat, cluster.location.lng)),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        onClusterClick(cluster)
                        true
                    },
                ) {
                    PlaceClusterPin(count = cluster.count)
                }
            }
        }
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
                    // Above the places.
                    zIndex = 1f,
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

@Composable
private fun rememberPlaceIcon(isTripinly: Boolean, likeCount: Int): BitmapDescriptor {
    val bitmap = rememberPlacePinBitmap(isTripinly, likeCount)
    return remember(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap.asAndroidBitmap()) }
}

private const val PLACE_FADE_MILLIS = 250

// One like count per hot spot size (see hotSpotSize).
private val HOT_SPOT_LIKES = listOf(0, 5, 20)
