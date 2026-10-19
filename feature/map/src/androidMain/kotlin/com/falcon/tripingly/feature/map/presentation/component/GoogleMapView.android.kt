package com.falcon.tripingly.feature.map.presentation.component

import android.graphics.Point
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    pinDotBelowZoom: Float,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
    routeLine: List<Coordinates>,
    stopPath: List<Coordinates>,
    isMyLocationEnabled: Boolean,
    coveredBottom: Dp,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapTouch: () -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onPlacePinClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
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

    // A camera animation started before the map has loaded doesn't run (on a fresh start the trip's stops arrive
    // first), so until then the camera jumps instead.
    var isMapLoaded by remember { mutableStateOf(false) }

    fun reportCamera() {
        val position = cameraPositionState.position
        onCameraMove(
            Coordinates(position.target.latitude, position.target.longitude),
            position.zoom,
            viewBounds(),
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
            if (!isMapLoaded) {
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
        onMapLoaded = {
            isMapLoaded = true
            reportCamera()
        },
    ) {
        // Every pin look is drawn once, here where the map can take icons, and shared by every pin that looks the same.
        val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
        val placeColor = MaterialTheme.colorScheme.secondary
        val onPlaceColor = MaterialTheme.colorScheme.onSecondary
        val hotSpotColor = MaterialTheme.colorScheme.tertiary
        val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val textMeasurer = rememberTextMeasurer()
        val pinPhotos = rememberPinPhotos(placePins)
        val pinIcons = remember(placeColor, hotSpotColor, density) { mutableMapOf<PlacePinLook, BitmapDescriptor>() }
        fun pinIcon(look: PlacePinLook) = pinIcons.getOrPut(look) {
            val fill = when {
                look.isAlongTheWay -> AlongTheWayPinColor
                look.isTripinly -> hotSpotColor
                else -> placeColor
            }
            val photo = look.photoId?.let { pinPhotos[it] }
            val bitmap = if (look.isDot) {
                drawPlaceDot(look.size, fill, density, layoutDirection)
            } else if (photo != null) {
                drawPlacePhotoPin(photo, look.size, fill, density, layoutDirection)
            } else {
                drawPlacePin(
                    icon = categoryIcons.getValue(look.category),
                    size = look.size,
                    fill = fill,
                    iconColor = when {
                        look.isAlongTheWay -> Color.White
                        look.isTripinly -> onHotSpotColor
                        else -> onPlaceColor
                    },
                    density = density,
                    layoutDirection = layoutDirection,
                )
            }
            BitmapDescriptorFactory.fromBitmap(bitmap.asAndroidBitmap())
        }
        // A photo pin that couldn't load its photo shows its icon.
        fun photoId(place: MapPlace, photo: Boolean) = place.coverImageId?.takeIf { photo && pinPhotos[it] != null }
        val clusterIcons = remember(hotSpotColor, density) { mutableMapOf<Int, BitmapDescriptor>() }

        // Place pins are markers, so they keep their size on screen while the camera zooms. The first ones
        // (the most liked, then the best known) are drawn on top; a new pin fades in. Changing a pin's icon makes
        // every pin on the map blink, so a pin whose look changes comes back as a new pin, a photo pin waits for its
        // photo, and the tapped place's pin stays as it is under a bigger copy.
        //
        // Zoomed out below pinDotBelowZoom, the pins turn into dots. Each pin has its dot marker from the start, hidden,
        // so the swap only shows one and hides the other, at once: no markers are made or dropped while the fingers move.
        val isDots by remember(pinDotBelowZoom) { derivedStateOf { cameraPositionState.position.zoom < pinDotBelowZoom } }
        placePins.forEach { pin ->
            if (pin.photo && pin.place.coverImageId !in pinPhotos) return@forEach
            val look = PlacePinLook(pin.place.category, pin.place.isTripinly, pin.size, photoId(pin.place, pin.photo), pin.isAlongTheWay)
            val dotLook = pin.dotLook()
            key(pin.place.id) {
                val fadeIn = remember { Animatable(0f) }
                LaunchedEffect(Unit) { fadeIn.animateTo(1f, tween(PIN_FADE_MILLIS)) }
                val position = LatLng(pin.place.location.lat, pin.place.location.lng)
                // Under the clusters and the stops. Taken from the place, not from its place in the list, so a pin's
                // z-order stays the same when pins come and go: changing it makes pins vanish for a frame.
                val zIndex = PLACE_PINS_Z * pinRank(pin.place)
                // No info window and no camera move of the map's own: the card and the camera are ours.
                fun onClick(): Boolean {
                    onPlacePinClick(pin.place)
                    return true
                }
                key(look) {
                    Marker(
                        state = rememberUpdatedMarkerState(position = position),
                        icon = pinIcon(look),
                        title = pin.place.name,
                        anchor = Offset(0.5f, 0.5f),
                        alpha = fadeIn.value,
                        visible = !isDots,
                        zIndex = zIndex,
                        onClick = { onClick() },
                    )
                }
                key(dotLook) {
                    Marker(
                        state = rememberUpdatedMarkerState(position = position),
                        icon = pinIcon(dotLook),
                        title = pin.place.name,
                        anchor = Offset(0.5f, 0.5f),
                        alpha = fadeIn.value,
                        visible = isDots,
                        zIndex = zIndex,
                        onClick = { onClick() },
                    )
                }
            }
        }
        placeClusters.forEach { cluster ->
            key(cluster.location) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(cluster.location.lat, cluster.location.lng)),
                    icon = clusterIcons.getOrPut(cluster.count) {
                        BitmapDescriptorFactory.fromBitmap(
                            drawClusterPin(cluster.count, hotSpotColor, onHotSpotColor, textMeasurer, density, layoutDirection).asAndroidBitmap(),
                        )
                    },
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = PLACE_PINS_Z,
                    onClick = {
                        onClusterClick(cluster)
                        true
                    },
                )
            }
        }

        // Under every pin.
        if (routeLine.size > 1) {
            Polyline(
                points = remember(routeLine) { routeLine.map { LatLng(it.latitude, it.longitude) } },
                color = RouteColor,
                width = with(density) { RouteWidth.toPx() },
            )
        }
        if (stopPath.size > 1) StopPathLine(stopPath)

        // The tapped place, bigger and on top.
        selectedPlace?.let { place ->
            key(place.id) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(place.location.lat, place.location.lng)),
                    icon = pinIcon(
                        PlacePinLook(
                            place.category,
                            place.isTripinly,
                            SelectedPinSize,
                            photoId(place, placePins.any { it.photo && it.place.id == place.id }),
                            isAlongTheWay = placePins.any { it.isAlongTheWay && it.place.id == place.id },
                        ),
                    ),
                    title = place.name,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 2f,
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

/** The stops joined by straight lines with arrows pointing the way; a streak runs along it once when it changes. */
@Composable
@GoogleMapComposable
private fun StopPathLine(stops: List<Coordinates>) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val shape = remember(stops) { stopPathShape(stops) }
    val points = remember(shape) { shape.points.map { LatLng(it.latitude, it.longitude) } }
    val width = with(density) { StopPathWidth.toPx() }
    Polyline(points = points, color = StopPathColor, width = width, jointType = JointType.ROUND)

    // Flat, so they turn with the map and keep pointing along the line; under every pin (z 0).
    val arrowIcon = remember(density) { BitmapDescriptorFactory.fromBitmap(drawPathArrow(density, layoutDirection).asAndroidBitmap()) }
    shape.arrows.forEach { arrow ->
        key(arrow) {
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(arrow.position.latitude, arrow.position.longitude)),
                icon = arrowIcon,
                rotation = arrow.bearing,
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                onClick = { true },
            )
        }
    }

    // Once, like a progress bar; then nothing moves.
    val sweep = remember(shape) { Animatable(0f) }
    LaunchedEffect(shape) { sweep.animateTo(1f, tween(STOP_PATH_SWEEP_MILLIS, easing = LinearEasing)) }
    val streak = sweepOf(points, sweep.value)
    if (streak.size > 1) Polyline(points = streak, color = StopPathSweepColor, width = width, jointType = JointType.ROUND, zIndex = 1f)
}

/** A new place pin fades in, as Google's own pins do. */
private const val PIN_FADE_MILLIS = 250

/** The pin-to-dot fade in a few steps, each one redrawing every pin. */

/** Between 0 and 1, higher for a better place: liked places by their likes above the others by how well known. */
private fun pinRank(place: MapPlace): Float =
    if (place.likeCount > 0) 0.5f + minOf(place.likeCount, 1000) / 2500f else place.prominence / 250f

/** Place pins sit under this, the stops at 1 and the tapped place at 2. */
private const val PLACE_PINS_Z = 0.5f

/** How long the camera takes to fly to a pin, a search result or the user's location. */
private const val CAMERA_MOVE_MILLIS = 180
