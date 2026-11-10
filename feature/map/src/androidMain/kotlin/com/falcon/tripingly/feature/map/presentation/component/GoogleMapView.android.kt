package com.falcon.tripingly.feature.map.presentation.component

import android.graphics.Point
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
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
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    initialTarget: Coordinates,
    initialZoom: Float,
    cameraCommands: Flow<CameraCommand.MoveTo>,
    contentPadding: PaddingValues,
    layers: MapLayersState,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onCameraMoving: (GeoBounds, Float) -> Unit,
    onMapTouch: () -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onPlacePinClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onPinDrop: (from: PinEnd, to: PinEnd) -> Unit,
) {
    val (markers, placePins, pinDotBelowZoom, placeClusters, selectedPlace, line, isMyLocationEnabled) = layers
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialTarget.latitude, initialTarget.longitude), initialZoom)
    }

    // The area the whole view shows. The map's visible region leaves out its padding (see contentPadding).
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

    // Where the last touch began on the map, to tell which of two overlapping pins the finger was aimed at.
    var lastTouch by remember { mutableStateOf<Offset?>(null) }
    // The second tap of a double tap (double tap and drag zooms) is no tap or long press of its own.
    var isDoubleTap by remember { mutableStateOf(false) }

    // A pin held to pull a route from it (see PinHoldLayer). The map's own long press starts it when the finger is on a
    // pin, instead of dropping a named pin; the map doesn't move under the finger while it's held.
    val holder = remember { PinHolder() }
    val isHolding = holder.hold != null
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val currentLayers by rememberUpdatedState(layers)
    val currentOnPinDrop by rememberUpdatedState(onPinDrop)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnPlacePinClick by rememberUpdatedState(onPlacePinClick)
    // This touch was a tap on a pin, handled as the finger lifted: the map's own click, which comes a moment later
    // (it waits to see whether a second tap follows), does nothing.
    var isTapHandled by remember { mutableStateOf(false) }
    fun project(point: Coordinates): Offset? =
        cameraPositionState.projection?.toScreenLocation(LatLng(point.latitude, point.longitude))?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
    fun shownPins(): List<ShownPin> {
        // Read for the zoom, so a pin's dot or pin size counts.
        val zoom = cameraPositionState.position.zoom
        return currentLayers.shownPins(zoom, density, ::project)
    }
    /** A tap on a pin at [touch]: a tick and the pin opens at once. False when no pin is there. */
    fun tapPin(touch: Offset): Boolean {
        val pin = shownPins().pinAt(touch) ?: return false
        haptics.tick()
        when (pin) {
            is PinEnd.Stop -> currentOnMarkerClick(pin.stop)
            // The open place's own pin: as before, a tap on it does nothing more.
            is PinEnd.Place -> if (pin.place.id != currentLayers.selectedPlace?.id) currentOnPlacePinClick(pin.place)
        }
        return true
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
    // Every frame while the camera moves (a finger, a flight, a held pin pulling it), so places load on the way.
    val currentOnCameraMoving by rememberUpdatedState(onCameraMoving)
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position }.collect { position ->
            if (cameraPositionState.isMoving || holder.hold != null) viewBounds()?.let { currentOnCameraMoving(it, position.zoom) }
        }
    }

    // Not while a held pin moves the camera: once it's let go.
    LaunchedEffect(cameraPositionState.isMoving, isHolding) {
        if (!cameraPositionState.isMoving && !isHolding) reportCamera()
    }

    val uiSettings = remember(isHolding) {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false,
            // While a pin is held the finger pulls its line; only the hold moves the camera.
            scrollGesturesEnabled = !isHolding,
            zoomGesturesEnabled = !isHolding,
            rotationGesturesEnabled = !isHolding,
            tiltGesturesEnabled = !isHolding,
        )
    }

    val isDark = isSystemInDarkTheme()
    val mapProperties = remember(isMyLocationEnabled, isDark) {
        MapProperties(
            isMyLocationEnabled = isMyLocationEnabled,
            mapStyleOptions = MapStyleOptions(if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE),
        )
    }

    suspend fun runCamera(update: CameraUpdate) {
        // Before the map has loaded, the camera starts there instead of flying.
        if (!isMapLoaded) {
            cameraPositionState.move(update)
        } else {
            try {
                cameraPositionState.animate(update, durationMs = CAMERA_MOVE_MILLIS)
            } catch (e: CancellationException) {
                // A finger stopped the flight; the next move still runs.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    LaunchedEffect(cameraCommands) {
        cameraCommands.collect { command ->
            runCamera(CameraUpdateFactory.newLatLngZoom(LatLng(command.target.latitude, command.target.longitude), command.zoom))
        }
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { mapSize = it }
                .pointerInput(Unit) {
                    var lastDownMillis = Long.MIN_VALUE / 2
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastTouch = down.position
                        isDoubleTap = down.uptimeMillis - lastDownMillis < DOUBLE_TAP_MILLIS
                        lastDownMillis = down.uptimeMillis
                        isTapHandled = false
                        var isTap = true
                        // Follows the finger to the end of the gesture, for a pin it may hold.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) isTap = false
                            if (!change.pressed) {
                                // A quick tap on a pin opens it as the finger lifts; a long press isn't a tap.
                                val isQuick = change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                                if (isTap && isQuick && !isDoubleTap && holder.hold == null) isTapHandled = tapPin(change.position)
                                break
                            }
                            lastTouch = change.position
                            if (holder.hold != null) holder.move(change.position, shownPins(), density)
                        }
                        holder.end()?.let { (from, to) -> currentOnPinDrop(from, to) }
                    }
                },
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            contentPadding = contentPadding,
            // Dark map in dark mode, so the light status bar icons stay readable.
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapClick = { latLng ->
                if (!isDoubleTap && !isTapHandled) onMapClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
            },
            onMapLongClick = { latLng ->
                val touch = lastTouch
                val pin = touch?.let { shownPins().pinAt(it) }
                when {
                    isDoubleTap -> Unit
                    // On a pin: it's held to pull a route from it, no pin dropped.
                    pin != null && touch != null -> holder.start(pin, touch)
                    else -> onMapLongClick(Coordinates(latitude = latLng.latitude, longitude = latLng.longitude))
                }
            },
            // The first camera report comes before the map can tell its bounds, so the squares around the view load from here.
            onMapLoaded = {
                isMapLoaded = true
                reportCamera()
            },
        ) {
            // Every pin look is drawn once, here where the map can take icons, and shared by every pin that looks the same.
            val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
            val placeColor = MaterialTheme.extendedColors.place
            val onPlaceColor = MaterialTheme.colorScheme.onSecondary
            val hotSpotColor = MaterialTheme.extendedColors.place
            val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
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
            val shownPins = placePins.filter { !it.photo || it.place.coverImageId in pinPhotos }

            // The map hands a tap on overlapping pins to the one on top; the pin whose middle is nearest the finger is the
            // one meant, as long as the finger is on it.
            fun aimedPin(tapped: PlacePin): PlacePin {
                val touch = lastTouch ?: return tapped
                val projection = cameraPositionState.projection ?: return tapped
                val slop = with(density) { PIN_TAP_SLOP.toPx() }
                return shownPins
                    .map { pin ->
                        val point = projection.toScreenLocation(LatLng(pin.place.location.lat, pin.place.location.lng))
                        pin to hypot(point.x - touch.x, point.y - touch.y)
                    }
                    .filter { (pin, distance) ->
                        val size = if (isDots || pin.isDot) PlaceDotSize else pin.size
                        distance <= with(density) { size.toPx() } / 2 + slop
                    }
                    .minByOrNull { (_, distance) -> distance }
                    ?.first ?: tapped
            }

            // All pins go on the map in the same frame, along-the-way ones too, so a change shows at once (Shahin, 2026-11-10).
            // A pin that leaves is only hidden, up to MAX_HIDDEN_PINS of the latest ones: making a native marker is the
            // costly part, so a pin coming back (a pan back, the route switched off) just shows again.
            val hiddenPins = rememberHiddenPins(shownPins)
            (shownPins.map { it to true } + hiddenPins.map { it to false }).forEach { (pin, isShown) ->
                val look = PlacePinLook(pin.place.category, pin.place.isTripinly, pin.size, photoId(pin.place, pin.photo), pin.isAlongTheWay)
                val dotLook = pin.dotLook()
                key(pin.place.id) {
                    // Places along the way show at once with their route (Shahin, 2026-11-10).
                    val fadeIn = remember { Animatable(if (pin.isAlongTheWay) 1f else 0f) }
                    LaunchedEffect(Unit) { fadeIn.animateTo(1f, tween(PIN_FADE_MILLIS)) }
                    val position = LatLng(pin.place.location.lat, pin.place.location.lng)
                    // Under the clusters and the stops. Taken from the place, not from its place in the list, so a pin's
                    // z-order stays the same when pins come and go: changing it makes pins vanish for a frame.
                    val zIndex = PLACE_PINS_Z * pinRank(pin.place)
                    // No info window and no camera move of the map's own: the card and the camera are ours.
                    fun onClick(): Boolean {
                        if (isTapHandled) return true
                        haptics.tick()
                        onPlacePinClick(aimedPin(pin).place)
                        return true
                    }
                    key(look) {
                        Marker(
                            state = rememberUpdatedMarkerState(position = position),
                            icon = pinIcon(look),
                            title = pin.place.name,
                            anchor = Offset(0.5f, 0.5f),
                            alpha = fadeIn.value,
                            visible = isShown && !isDots && !pin.isDot,
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
                            visible = isShown && (isDots || pin.isDot),
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
                            haptics.tick()
                            onClusterClick(cluster)
                            true
                        },
                    )
                }
            }

            // Under every pin.
            if (line != null && line.points.size > 1) MapLineView(line.themed())

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
                    // The icon is drawn once and redrawn only when its number or cover changes.
                    val cover = marker.coverPhotoId?.let { coverThumbs[it] }
                    MarkerComposable(
                        marker.orderNumber,
                        // Empty until the cover's thumbnail has loaded.
                        marker.coverPhotoId.takeIf { cover != null }.orEmpty(),
                        marker.isNew,
                        state = markerState,
                        title = marker.title.asString(),
                        // The pin is a circle centered on the stop.
                        anchor = Offset(0.5f, 0.5f),
                        // Above the places.
                        zIndex = 1f,
                        onClick = {
                            if (!isTapHandled) {
                                haptics.tick()
                                onMarkerClick(marker)
                            }
                            // No info window and no camera move of the map's own: the card and the camera are ours.
                            true
                        }
                    ) {
                        TripMarkerIcon(
                            orderNumber = marker.orderNumber,
                            cover = cover,
                            isNew = marker.isNew,
                        )
                    }
                }
            }
        }
        PinHoldLayer(
            holder = holder,
            freeArea = freeArea(Size(mapSize.width.toFloat(), mapSize.height.toFloat()), contentPadding, density, LocalLayoutDirection.current),
            project = { point ->
                // Read, so the line follows the camera.
                cameraPositionState.position
                project(point)
            },
            pins = ::shownPins,
            onPan = { velocity, seconds -> if (velocity != Offset.Zero) cameraPositionState.move(CameraUpdateFactory.scrollBy(velocity.x * seconds, velocity.y * seconds)) },
        )
    }
}

/** A line with rounded ends and arrows pointing the way; a streak runs along it once when it changes. */
@Composable
@GoogleMapComposable
private fun MapLineView(line: MapLine) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val points = remember(line) { line.points.map { LatLng(it.latitude, it.longitude) } }
    val width = with(density) { line.style.width.toPx() }
    Polyline(points = points, color = line.style.color, width = width, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())

    // Flat, so they turn with the map and keep pointing along the line; under every pin (z 0).
    val arrowColor = line.style.color
    val arrowIcon = remember(density, arrowColor) {
        BitmapDescriptorFactory.fromBitmap(drawPathArrow(density, layoutDirection, arrowColor).asAndroidBitmap())
    }
    line.arrows.forEach { arrow ->
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
    val sweep = remember(line) { Animatable(0f) }
    LaunchedEffect(line) { sweep.animateTo(1f, tween(MAP_LINE_SWEEP_MILLIS, easing = LinearEasing)) }
    val streak = sweepOf(points, sweep.value)
    if (streak.size > 1) {
        Polyline(
            points = streak,
            color = MapLineSweepColor,
            width = width,
            jointType = JointType.ROUND,
            startCap = RoundCap(),
            endCap = RoundCap(),
            zIndex = 1f,
        )
    }
}

/**
 * The latest pins that left [shown], newest last, at most [MAX_HIDDEN_PINS]: their markers stay on the map, hidden. A pin
 * back in [shown] leaves this list.
 */
@Composable
private fun rememberHiddenPins(shown: List<PlacePin>): List<PlacePin> {
    val hidden = remember { LinkedHashMap<String, PlacePin>() }
    val previous = remember { arrayOf(emptyList<PlacePin>()) }
    return remember(shown) {
        val shownIds = shown.mapTo(HashSet()) { it.place.id }
        previous[0].filter { it.place.id !in shownIds }.forEach { pin ->
            hidden.remove(pin.place.id)
            hidden[pin.place.id] = pin
        }
        shownIds.forEach(hidden::remove)
        while (hidden.size > MAX_HIDDEN_PINS) hidden.remove(hidden.keys.first())
        previous[0] = shown
        hidden.values.toList()
    }
}

/** Hidden markers cost a little memory and nothing to draw; enough for a pan back and forth over a full map. */
private const val MAX_HIDDEN_PINS = 300

/** A new place pin fades in, as Google's own pins do. */
private const val PIN_FADE_MILLIS = 250


/** Between 0 and 1, higher for a better place: liked places by their likes above the others by how well known. */
private fun pinRank(place: MapPlace): Float =
    if (place.hotCount > 0) 0.5f + minOf(place.hotCount, 1000) / 2500f else place.prominence / 250f

/** Two touches this close together are a double tap: the first tap's length plus the platform's double tap time. */
private const val DOUBLE_TAP_MILLIS = 500L

/** How far outside a pin's circle a touch still counts as aimed at it. */
private val PIN_TAP_SLOP = 4.dp

/** Place pins sit under this, the stops at 1 and the tapped place at 2. */
private const val PLACE_PINS_Z = 0.5f

