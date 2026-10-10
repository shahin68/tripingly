package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    selectedMarkerId: String?,
    placeSource: MapPlaceSource?,
    selectedPlace: MapPlace?,
    isMyLocationEnabled: Boolean,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
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

    fun visibleSquares(): List<PlaceSquare> {
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return emptyList()
        return PlaceSquare.covering(
            GeoBounds(bounds.southwest.latitude, bounds.southwest.longitude, bounds.northeast.latitude, bounds.northeast.longitude),
            cameraPositionState.position.zoom.toInt(),
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
        // The first camera report comes before the map can tell its bounds, so the squares around the view load from here.
        onMapLoaded = ::reportCamera,
    ) {
        // Every pin look is drawn once, here where the map can take icons; the tiles copy them.
        val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
        val placeColor = MaterialTheme.colorScheme.secondary
        val onPlaceColor = MaterialTheme.colorScheme.onSecondary
        val hotSpotColor = MaterialTheme.colorScheme.tertiary
        val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        fun drawPin(look: PlacePinLook) = drawPlacePin(
            icon = categoryIcons.getValue(look.category),
            size = look.size,
            fill = if (look.isTripinly) hotSpotColor else placeColor,
            iconColor = if (look.isTripinly) onHotSpotColor else onPlaceColor,
            density = density,
            layoutDirection = layoutDirection,
        ).asAndroidBitmap()

        val tileProvider = remember(placeColor, hotSpotColor, density) {
            val looks = PlaceCategory.entries.flatMap { category ->
                listOf(PlacePinLook(category, false, PlainPinSize)) + HotSpotSizes.map { PlacePinLook(category, true, it) }
            }
            PlaceTileProvider(
                pins = looks.associateWith(::drawPin),
                clusterColor = hotSpotColor.toArgb(),
                onClusterColor = onHotSpotColor.toArgb(),
                density = density.density,
                fontScale = density.fontScale,
            ).also { it.source = placeSource }
        }
        // New stops hide their places: the tiles are drawn again without them.
        val tileOverlayState = rememberTileOverlayState()
        LaunchedEffect(tileProvider, placeSource) {
            if (tileProvider.source != placeSource) {
                tileProvider.source = placeSource
                tileOverlayState.clearTileCache()
            }
            // A square in view reloaded with new places: Google can only drop every tile, so the
            // squares reloaded together are redrawn at once, from what the repository keeps.
            placeSource?.changes?.filter { it in visibleSquares() }?.collectLatest {
                delay(REDRAW_AFTER_MILLIS)
                tileOverlayState.clearTileCache()
            }
        }
        TileOverlay(tileProvider = tileProvider, state = tileOverlayState, fadeIn = true)

        // The tapped place is a real marker, bigger and on top of its pin in the tiles.
        selectedPlace?.let { place ->
            key(place.id) {
                val look = PlacePinLook(place.category, place.isTripinly, placePinSize(place, isSelected = true))
                val icon = remember(look, placeColor, hotSpotColor, density) { BitmapDescriptorFactory.fromBitmap(drawPin(look)) }
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(place.location.lat, place.location.lng)),
                    icon = icon,
                    title = place.name,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 2f,
                    // No info window and no camera move of the map's own: the card and the camera are ours.
                    onClick = { true },
                )
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

/** Lets the squares of one background reload arrive before the tiles are drawn again. */
private const val REDRAW_AFTER_MILLIS = 300L
