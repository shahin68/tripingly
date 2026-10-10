package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    selectedMarkerId: String?,
    places: List<MapPlace>,
    selectedPlaceId: String?,
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

    // Places fade in and out together: one fade per answer, however many places it brings or drops.
    // The map adds and removes markers on the main thread, so a big answer goes onto the map a few
    // markers per frame and the map keeps moving.
    var shownPlaces by remember { mutableStateOf(emptyList<MapPlace>()) }
    var arrivingIds by remember { mutableStateOf(emptySet<String>()) }
    var leavingPlaces by remember { mutableStateOf(emptyList<MapPlace>()) }
    val fadeIn = remember { Animatable(1f) }
    val fadeOut = remember { Animatable(0f) }
    LaunchedEffect(places) {
        val ids = places.mapTo(HashSet()) { it.id }
        val shownIds = shownPlaces.mapTo(HashSet()) { it.id }
        val arriving = places.filter { it.id !in shownIds }
        arrivingIds = arriving.mapTo(HashSet()) { it.id }
        leavingPlaces = shownPlaces.filter { it.id !in ids }
        shownPlaces = places.filter { it.id in shownIds }
        fadeIn.snapTo(0f)
        fadeOut.snapTo(1f)
        coroutineScope {
            launch {
                fadeOut.animateTo(0f, tween(PLACE_FADE_MILLIS))
                while (leavingPlaces.isNotEmpty()) {
                    leavingPlaces = leavingPlaces.drop(MARKERS_PER_FRAME)
                    withFrameNanos {}
                }
            }
            arriving.chunked(MARKERS_PER_FRAME).forEach { chunk ->
                shownPlaces = shownPlaces + chunk
                withFrameNanos {}
            }
            fadeIn.animateTo(1f, tween(PLACE_FADE_MILLIS))
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
        },
        onMapLongClick = { latLng ->
            onMapLongClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
        },
        // The first camera report comes before the map can tell its bounds, so places load from here.
        onMapLoaded = ::reportCamera,
    ) {
        // Pins that look the same share one bitmap, drawn the first time that look is needed. Built
        // here, once the map is ready to take icons.
        val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
        val placeColor = MaterialTheme.colorScheme.secondary
        val onPlaceColor = MaterialTheme.colorScheme.onSecondary
        val hotSpotColor = MaterialTheme.colorScheme.tertiary
        val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val pinIcons = remember(placeColor, hotSpotColor, density) { mutableMapOf<PlacePinLook, BitmapDescriptor>() }
        fun pinIcon(place: MapPlace): BitmapDescriptor {
            val look = PlacePinLook(place.category, place.isTripinly, placePinSize(place, place.id == selectedPlaceId))
            return pinIcons.getOrPut(look) {
                val bitmap = drawPlacePin(
                    icon = categoryIcons.getValue(look.category),
                    size = look.size,
                    fill = if (look.isTripinly) hotSpotColor else placeColor,
                    iconColor = if (look.isTripinly) onHotSpotColor else onPlaceColor,
                    density = density,
                    layoutDirection = layoutDirection,
                )
                BitmapDescriptorFactory.fromBitmap(bitmap.asAndroidBitmap())
            }
        }

        shownPlaces.forEach { place ->
            key(place.id) {
                PlaceMarker(
                    place = place,
                    icon = pinIcon(place),
                    isSelected = place.id == selectedPlaceId,
                    alpha = if (place.id in arrivingIds) ({ fadeIn.value }) else ({ 1f }),
                    onClick = { onPlaceClick(place) },
                )
            }
        }
        leavingPlaces.forEach { place ->
            key("leaving", place.id) {
                PlaceMarker(place = place, icon = pinIcon(place), isSelected = false, alpha = { fadeOut.value }, onClick = {})
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

/** A place pin; reads its [alpha] here, so a fade redraws only the pins that fade. */
@Composable
private fun PlaceMarker(place: MapPlace, icon: BitmapDescriptor, isSelected: Boolean, alpha: () -> Float, onClick: () -> Unit) {
    Marker(
        state = rememberUpdatedMarkerState(position = LatLng(place.location.lat, place.location.lng)),
        icon = icon,
        title = place.name,
        alpha = alpha(),
        anchor = Offset(0.5f, 0.5f),
        // The tapped place above everything else.
        zIndex = if (isSelected) 2f else 0f,
        onClick = {
            onClick()
            // No info window and no camera move of the map's own: the card and the camera are ours.
            true
        },
    )
}

private data class PlacePinLook(val category: PlaceCategory, val isTripinly: Boolean, val size: Dp)

private const val PLACE_FADE_MILLIS = 250

// Markers added or removed in one frame.
private const val MARKERS_PER_FRAME = 15

