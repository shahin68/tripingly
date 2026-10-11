package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Point
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
    placeSource: MapPlaceSource?,
    selectedPlace: MapPlace?,
    isMyLocationEnabled: Boolean,
    coveredBottom: Dp,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapTouch: () -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
) {
    val initialLatLng = remember { LatLng(cameraTarget.latitude, cameraTarget.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, zoomLevel)
    }

    // The area the whole view shows. The map's visible region leaves out its padding (see coveredBottom).
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    fun viewBounds(): GeoBounds? {
        val projection = cameraPositionState.projection ?: return null
        if (mapSize == IntSize.Zero) return null
        val corners = listOf(Point(0, 0), Point(mapSize.width, 0), Point(0, mapSize.height), Point(mapSize.width, mapSize.height))
            .map(projection::fromScreenLocation)
        return GeoBounds(
            south = corners.minOf { it.latitude },
            west = corners.minOf { it.longitude },
            north = corners.maxOf { it.latitude },
            east = corners.maxOf { it.longitude },
        )
    }

    fun reportCamera() {
        val position = cameraPositionState.position
        onCameraMove(
            Coordinates(position.target.latitude, position.target.longitude),
            position.zoom,
            viewBounds(),
        )
    }

    fun visibleSquares(): List<PlaceSquare> {
        val bounds = viewBounds() ?: return emptyList()
        return PlaceSquare.covering(bounds, cameraPositionState.position.zoom.toInt())
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
                    durationMs = CAMERA_MOVE_MILLIS
                )
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize().onSizeChanged { mapSize = it },
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        // Keeps Google's logo above the cards. As much room at the top as at the bottom keeps the
        // camera's center in the middle of the view.
        contentPadding = PaddingValues(vertical = coveredBottom),
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
        // Google can only drop every tile of an overlay, and its pins would blink while they are
        // drawn again. So a redraw is a new overlay over the old one: its tiles fade in over the
        // same pins, and the old overlay goes once they are there.
        var overlays by remember { mutableStateOf(listOf(0)) }
        fun redraw() {
            overlays = listOf(overlays.last(), overlays.last() + 1)
        }
        LaunchedEffect(overlays) {
            if (overlays.size > 1) {
                delay(KEEP_REPLACED_OVERLAY_MILLIS)
                overlays = overlays.takeLast(1)
            }
        }
        // New stops hide their places: the tiles are drawn again without them.
        LaunchedEffect(tileProvider, placeSource) {
            if (tileProvider.source != placeSource) {
                tileProvider.source = placeSource
                redraw()
            }
            // A square in view reloaded with new places: the squares reloaded together are drawn
            // again at once, from what the repository keeps.
            placeSource?.changes?.filter { it in visibleSquares() }?.collectLatest {
                delay(REDRAW_AFTER_MILLIS)
                redraw()
            }
        }
        overlays.forEachIndexed { index, overlay ->
            key(overlay) { TileOverlay(tileProvider = tileProvider, fadeIn = true, zIndex = index.toFloat()) }
        }

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
        val coverThumbs = rememberCoverThumbs(markers)
        markers.forEach { marker ->
            key(marker.id) {
                val position = LatLng(marker.position.latitude, marker.position.longitude)
                val markerState = rememberUpdatedMarkerState(position = position)
                // The icon is drawn once and redrawn only when its number, color or cover changes.
                val cover = marker.coverPhotoId?.let { coverThumbs[it] }
                MarkerComposable(
                    marker.orderNumber,
                    marker.color,
                    // Empty until the cover's thumbnail has loaded.
                    marker.coverPhotoId.takeIf { cover != null }.orEmpty(),
                    state = markerState,
                    title = marker.title,
                    anchor = Offset(0.5f, 1f),
                    // Above the places.
                    zIndex = 1f,
                    onClick = {
                        onMarkerClick(marker)
                        // No info window and no camera move of the map's own: the card and the camera are ours.
                        true
                    }
                ) {
                    TripMarkerIcon(
                        orderNumber = marker.orderNumber,
                        color = Color(marker.color),
                        cover = cover,
                    )
                }
            }
        }
    }
}

/** Lets the squares of one background reload arrive before the tiles are drawn again. */
private const val REDRAW_AFTER_MILLIS = 300L

/** Long enough for the new overlay's tiles to be drawn from the kept squares and faded in. */
private const val KEEP_REPLACED_OVERLAY_MILLIS = 1_500L

/** How long the camera takes to fly to a pin, a search result or the user's location. */
private const val CAMERA_MOVE_MILLIS = 400
