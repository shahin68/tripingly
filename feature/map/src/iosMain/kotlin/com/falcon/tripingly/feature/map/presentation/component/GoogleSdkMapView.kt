package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import platform.UIKit.UIColor
import platform.UIKit.UIImage

/**
 * Google's map on iOS, driven like the Android one: place pins as markers that keep their size on
 * screen, stops as numbered markers, the tapped place as a bigger marker on top.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GoogleSdkMapView(
    googleMaps: GoogleMapsSdk,
    modifier: Modifier,
    initialTarget: Coordinates,
    initialZoom: Float,
    cameraCommands: Flow<CameraCommand.MoveTo>,
    contentPadding: PaddingValues,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    pinDotBelowZoom: Float,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
    line: MapLine?,
    isMyLocationEnabled: Boolean,
    routeEnds: List<RouteEndMark>,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onCameraMoving: (GeoBounds, Float) -> Unit,
    onMapTouch: () -> Unit,
    onUserMove: () -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onPlacePinClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onPinDrop: (from: PinEnd, to: PinEnd) -> Unit,
) {
    val currentMarkers by rememberUpdatedState(markers)
    val currentSelectedPlace by rememberUpdatedState(selectedPlace)
    val currentPinDotBelowZoom by rememberUpdatedState(pinDotBelowZoom)
    val currentOnPinDrop by rememberUpdatedState(onPinDrop)
    val currentPlacePins by rememberUpdatedState(placePins)
    val currentPlaceClusters by rememberUpdatedState(placeClusters)
    val currentOnPlacePinClick by rememberUpdatedState(onPlacePinClick)
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)
    val currentOnCameraMove by rememberUpdatedState(onCameraMove)
    val currentOnCameraMoving by rememberUpdatedState(onCameraMoving)
    val currentOnMapTouch by rememberUpdatedState(onMapTouch)
    val currentOnUserMove by rememberUpdatedState(onUserMove)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)

    // The SDK keeps its camera in the middle of its padding and moves the map whenever the padding changes, a frame
    // apart from any move that would undo it. So the map gets the same padding at the top and the bottom, and its
    // camera stays in the middle of the view's height; the target the screen means (the middle of what its padding
    // leaves free: above the cards) is that far from it. The sides get the screen's own padding (the panel in
    // landscape), which only changes on a rotation, so the camera is in the free middle sideways and the Google logo
    // sits beside the panel, not under it.
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    val left = contentPadding.calculateLeftPadding(LayoutDirection.Ltr)
    val right = contentPadding.calculateRightPadding(LayoutDirection.Ltr)
    val freeMiddle = DpOffset(x = 0.dp, y = (top - bottom) / 2)
    val currentFreeMiddle by rememberUpdatedState(freeMiddle)
    // Where the camera last rested, to report it again when the free middle moves.
    var rest by remember { mutableStateOf<Triple<Coordinates, Float, GeoBounds>?>(null) }
    // The map has rested once, so it has its size: a camera move flies from then on.
    var isMapReady by remember { mutableStateOf(false) }
    // The camera's zoom as it moves, read only to tell when the pins turn into dots and back.
    var liveZoom by remember { mutableStateOf(initialZoom) }
    val isDots by remember(pinDotBelowZoom) { derivedStateOf { liveZoom < pinDotBelowZoom } }
    // Counts the camera's moves, read where something on top of the map has to follow it.
    var cameraFrame by remember { mutableStateOf(0) }
    // A pin held to pull a route from it (see PinHoldLayer), started by the map's long press on a pin.
    val holder = remember { PinHolder() }
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    // The map works in points, the layer on top in px.
    lateinit var map: GoogleMap
    fun project(point: Coordinates): Offset =
        map.screenPoint(point.latitude, point.longitude).let { Offset(it.x.toFloat() * density.density, it.y.toFloat() * density.density) }
    fun shownPins(): List<ShownPin> = MapLayersState(
        stops = currentMarkers.toImmutableList(),
        placePins = currentPlacePins.toImmutableList(),
        pinDotBelowZoom = currentPinDotBelowZoom,
        selectedPlace = currentSelectedPlace,
    ).shownPins(liveZoom, density, ::project)
    fun reportRest() {
        val (center, zoom, bounds) = rest ?: return
        currentOnCameraMove(center.shiftedBy(currentFreeMiddle, zoom), zoom, bounds)
    }
    map = remember(googleMaps) {
        googleMaps.createMap(
            object : GoogleMapListener {
                override fun onCameraIdle(
                    latitude: Double,
                    longitude: Double,
                    zoom: Float,
                    south: Double,
                    west: Double,
                    north: Double,
                    east: Double,
                ) {
                    val bounds = GeoBounds(south, west, north, east)
                    isMapReady = true
                    rest = Triple(Coordinates(latitude, longitude), zoom, bounds)
                    // Not while a held pin moves the camera: once it's let go.
                    if (holder.hold == null) reportRest()
                }

                override fun onGestureStart() {
                    currentOnMapTouch()
                    currentOnUserMove()
                }

                override fun onCameraChange(zoom: Float, south: Double, west: Double, north: Double, east: Double) {
                    liveZoom = zoom
                    cameraFrame++
                    // The map pans itself while a pin is pulled: the handle may be near another pin now.
                    if (holder.hold != null) holder.retarget(shownPins(), density)
                    currentOnCameraMoving(GeoBounds(south, west, north, east), zoom)
                }

                override fun onTap(latitude: Double, longitude: Double) {
                    currentOnMapTouch()
                    currentOnMapClick(Coordinates(latitude, longitude))
                }

                override fun onLongPress(latitude: Double, longitude: Double, x: Double, y: Double): Boolean {
                    currentOnMapTouch()
                    val touch = Offset(x.toFloat() * density.density, y.toFloat() * density.density)
                    // On a pin: it's held to pull a route from it, no pin dropped.
                    val pin = shownPins().pinAt(touch)
                    if (pin != null) {
                        holder.start(pin, touch)
                        return true
                    }
                    currentOnMapLongClick(Coordinates(latitude, longitude))
                    return false
                }

                override fun onPinTap(x: Double, y: Double): Boolean {
                    val pin = shownPins().pinAt(Offset(x.toFloat() * density.density, y.toFloat() * density.density)) ?: return false
                    currentOnMapTouch()
                    haptics.tick()
                    when (pin) {
                        is PinEnd.Stop -> currentOnMarkerClick(pin.stop)
                        // A tap on a place's pin always opens its card, the open one's too: the finger going down closed it (Shahin, 2026-11-10).
                        is PinEnd.Place -> currentOnPlacePinClick(pin.place)
                    }
                    return true
                }

                override fun onPinHoldMove(x: Double, y: Double) {
                    holder.move(Offset(x.toFloat() * density.density, y.toFloat() * density.density), shownPins(), density)
                }

                override fun onPinHoldEnd() {
                    holder.end()?.let { (from, to) -> currentOnPinDrop(from, to) }
                    reportRest()
                }

                override fun onMarkerTap(key: String) {
                    // Read before the touch closes it.
                    val selected = currentSelectedPlace
                    currentOnMapTouch()
                    haptics.tick()
                    when {
                        // The open place's own pin, drawn bigger on top: a tap on it opens its card again, as on any pin.
                        // A second tap within the double-tap time comes this way, not as the quick tap (Shahin, 2026-11-11).
                        key == SELECTED_PLACE_KEY -> selected?.let(currentOnPlacePinClick)
                        key.startsWith(PLACE_KEY) ->
                            currentPlacePins.find { PLACE_KEY + it.place.id == key }?.let { currentOnPlacePinClick(it.place) }
                        key.startsWith(CLUSTER_KEY) ->
                            currentPlaceClusters.find { it.key() == key }?.let(currentOnClusterClick)
                        else -> currentMarkers.find { it.id == key }?.let(currentOnMarkerClick)
                    }
                }
            },
        )
    }

    fun moveCamera(target: Coordinates, zoom: Float, animated: Boolean) {
        val center = target.shiftedBy(DpOffset(-currentFreeMiddle.x, -currentFreeMiddle.y), zoom)
        map.moveCamera(center.latitude, center.longitude, zoom, animated)
    }
    LaunchedEffect(map) { moveCamera(initialTarget, initialZoom, animated = false) }
    LaunchedEffect(cameraCommands) {
        cameraCommands.collect { command ->
            // Until the map has rested once, it starts there instead of flying there.
            moveCamera(command.target, command.zoom, animated = isMapReady)
        }
    }

    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { map.setStyle(if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE) }
    LaunchedEffect(isMyLocationEnabled) { map.setMyLocationEnabled(isMyLocationEnabled) }
    // As much at the top as at the bottom, so the camera stays in the middle: what the logo needs to sit above the cards.
    // Not the top's: in landscape it's most of the height (a tapped pin goes low), which would leave the map no middle.
    val padding = bottom.value.toDouble()
    LaunchedEffect(padding, left, right) { map.setPadding(padding, left.value.toDouble(), padding, right.value.toDouble()) }
    // The middle of what's free moved. A rotation moves the sides (the panel takes or gives back the left half): like
    // Android's map with its padding, what was in the middle of what's free goes back there. Otherwise the map stays
    // put and the view model learns where the target now is.
    var shown by remember { mutableStateOf(Triple(freeMiddle, left, right)) }
    LaunchedEffect(freeMiddle, left, right) {
        val (before, leftBefore, rightBefore) = shown
        shown = Triple(freeMiddle, left, right)
        val (center, zoom, bounds) = rest ?: return@LaunchedEffect
        if (left != leftBefore || right != rightBefore) {
            moveCamera(center.shiftedBy(before, zoom), zoom, animated = false)
        } else {
            currentOnCameraMove(center.shiftedBy(freeMiddle, zoom), zoom, bounds)
        }
    }

    // Every pin look is drawn once and shared by every pin that looks the same.
    val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
    val placeColor = MaterialTheme.extendedColors.place
    val onPlaceColor = MaterialTheme.colorScheme.onSecondary
    val hotSpotColor = MaterialTheme.extendedColors.place
    val stopColor = MaterialTheme.extendedColors.stop
    val onStopColor = MaterialTheme.extendedColors.onStop
    val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
    val layoutDirection = LocalLayoutDirection.current

    // The line under every pin, with its arrows, as on Android.
    val themedLine = line?.themed()
    LaunchedEffect(themedLine) {
        if (themedLine == null || themedLine.points.size < 2) {
            map.setLine(emptyList(), stopColor.toUIColor(), 0.0, emptyList(), null, null)
            map.setLineSweep(emptyList(), MapLineSweepColor.toUIColor(), 0.0)
            return@LaunchedEffect
        }
        val points = themedLine.points.map { GoogleMapPoint(it.latitude, it.longitude) }
        val width = themedLine.style.width.value.toDouble()
        map.setLine(
            points = points,
            color = themedLine.style.color.toUIColor(),
            width = width,
            arrows = themedLine.arrows.map { GoogleMapArrow(it.position.latitude, it.position.longitude, it.bearing.toDouble()) },
            arrowIcon = drawPathArrow(density, layoutDirection, themedLine.style.color).toUIImage(density.density),
            capIcon = drawLineCap(density, layoutDirection, themedLine.style).toUIImage(density.density),
        )
        // Once, like a progress bar; then nothing moves.
        val sweepColor = MapLineSweepColor.toUIColor()
        Animatable(0f).animateTo(1f, tween(MAP_LINE_SWEEP_MILLIS, easing = LinearEasing)) {
            map.setLineSweep(sweepOf(points, value), sweepColor, width)
        }
        map.setLineSweep(emptyList(), sweepColor, width)
    }
    val textMeasurer = rememberTextMeasurer()
    val pinPhotos = rememberPinPhotos(placePins)
    val pinIcons = remember(placeColor, hotSpotColor, density) { mutableMapOf<PlacePinLook, UIImage>() }
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
        bitmap.toUIImage(density.density)
    }
    // A photo pin that couldn't load its photo shows its icon.
    fun photoId(place: MapPlace, photo: Boolean) = place.coverImageId?.takeIf { photo && pinPhotos[it] != null }
    val clusterIcons = remember(hotSpotColor, density) { mutableMapOf<Int, UIImage>() }

    // A stop's pin is drawn once per number and color.
    // By number, cover photo (once its thumbnail has loaded) and "new" dot.
    val stopIcons = remember(density, stopColor) { mutableMapOf<Triple<Int, String?, Boolean>, UIImage>() }
    val coverThumbs = rememberCoverThumbs(markers)
    val stopTitles = markers.map { it.title.asString() }
    // The rings round the route's ends, one per pin size and way of travelling's colour.
    val rings = remember(density) { mutableMapOf<Pair<Dp, Color>, UIImage>() }
    LaunchedEffect(markers, stopTitles, coverThumbs, pinPhotos, selectedPlace, placePins, placeClusters, pinIcons, clusterIcons, isDots, routeEnds) {
        val stops = markers.mapIndexed { index, marker ->
            GoogleMapMarker(
                key = marker.id,
                latitude = marker.position.latitude,
                longitude = marker.position.longitude,
                title = stopTitles[index],
                icon = marker.coverPhotoId?.let { coverThumbs[it] }.let { cover ->
                    stopIcons.getOrPut(Triple(marker.orderNumber, marker.coverPhotoId.takeIf { cover != null }, marker.isNew)) {
                        if (cover == null) {
                            drawTripPin(marker.orderNumber, marker.isNew, stopColor, onStopColor, density.density, density.fontScale)
                        } else {
                            drawCoverPin(marker.orderNumber, cover, marker.isNew, stopColor, onStopColor, density.density, density.fontScale)
                        }
                    }
                },
                // The pin is a circle centered on the stop.
                anchorX = 0.5,
                anchorY = 0.5,
                // Above the places.
                zIndex = 1,
                // The stop's card shows instead.
                showsInfoWindow = false,
                fadesIn = false,
            )
        }
        // The first pins (the most liked, then the best known) on top. A photo pin waits for its photo. The tapped
        // place's pin stays as it is and a bigger copy is drawn over it below, as on Android. Zoomed out below
        // pinDotBelowZoom, or while a route shows ([PlacePin.isDot]), a pin is its dot; the map fades a pin into its new look.
        val pins = placePins.mapIndexedNotNull { index, pin ->
            if (pin.photo && pin.place.coverImageId !in pinPhotos) return@mapIndexedNotNull null
            GoogleMapMarker(
                key = PLACE_KEY + pin.place.id,
                latitude = pin.place.location.lat,
                longitude = pin.place.location.lng,
                title = pin.place.name,
                icon = pinIcon(
                    if (pin.isDotAt(isDots)) pin.dotLook() else PlacePinLook(pin.place.category, pin.place.isTripinly, pin.size, photoId(pin.place, pin.photo), pin.isAlongTheWay),
                ),
                anchorX = 0.5,
                anchorY = 0.5,
                // A route's end and the places tapped while routing on top of the other places.
                zIndex = if (pin.isKept) KEPT_PIN_Z else -1 - index,
                showsInfoWindow = false,
                // Places along the way show at once with their route (Shahin, 2026-11-10).
                fadesIn = !pin.isAlongTheWay,
            )
        }
        val clusters = placeClusters.map { cluster ->
            GoogleMapMarker(
                key = cluster.key(),
                latitude = cluster.location.lat,
                longitude = cluster.location.lng,
                title = cluster.count.toString(),
                icon = clusterIcons.getOrPut(cluster.count) {
                    drawClusterPin(cluster.count, hotSpotColor, onHotSpotColor, textMeasurer, density, layoutDirection).toUIImage(density.density)
                },
                anchorX = 0.5,
                anchorY = 0.5,
                zIndex = 0,
                showsInfoWindow = false,
                fadesIn = true,
            )
        }
        // The tapped place, bigger and on top. No info window and no camera move of the map's own: the
        // card and the camera are ours.
        val place = selectedPlace?.let {
            GoogleMapMarker(
                key = SELECTED_PLACE_KEY,
                latitude = it.location.lat,
                longitude = it.location.lng,
                title = it.name,
                icon = pinIcon(
                    PlacePinLook(
                        it.category,
                        it.isTripinly,
                        SelectedPinSize,
                        photoId(it, placePins.any { pin -> pin.photo && pin.place.id == it.id }),
                        isAlongTheWay = placePins.any { pin -> pin.isAlongTheWay && pin.place.id == it.id },
                    ),
                ),
                anchorX = 0.5,
                anchorY = 0.5,
                zIndex = 2,
                showsInfoWindow = false,
                fadesIn = false,
            )
        }
        // The route's ends: a thick ring in the way of travelling's colour round each end's pin, under it.
        val ends = routeEnds.map { end ->
            GoogleMapMarker(
                key = ROUTE_END_KEY + end.isStart,
                latitude = end.position.lat,
                longitude = end.position.lng,
                title = "",
                icon = rings.getOrPut(end.pinSize to end.color) {
                    drawRouteEndRing(end.pinSize, end.color, density, layoutDirection).toUIImage(density.density)
                },
                anchorX = 0.5,
                anchorY = 0.5,
                zIndex = ROUTE_END_Z,
                showsInfoWindow = false,
                fadesIn = false,
            )
        }
        map.setMarkers(pins + clusters + stops + listOfNotNull(place) + ends)
    }

    // Stopped (another screen covers the map, or the app is in the background): the map draws nothing.
    LifecycleStartEffect(map) {
        map.view.hidden = false
        onStopOrDispose { map.view.hidden = true }
    }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    Box(modifier.fillMaxSize().onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }) {
        UIKitView(
            factory = { map.view },
            modifier = Modifier.fillMaxSize(),
            // The map takes every touch at once, without waiting to see whether Compose scrolls.
            properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
        )
        PinHoldLayer(
            holder = holder,
            freeArea = freeArea(viewSize, contentPadding, density, LocalLayoutDirection.current),
            project = { point ->
                // Read, so the line follows the camera.
                cameraFrame
                project(point)
            },
            pins = ::shownPins,
            // The map moves itself on its own display frames at this speed: stepping it from Compose's frames jitters.
            onPan = { velocity, _ -> map.setPanVelocity(velocity.x.toDouble() / density.density, velocity.y.toDouble() / density.density) },
        )
    }
}

private fun PlaceCluster.key() = "$CLUSTER_KEY${location.lat},${location.lng}"

// Marker keys that aren't stop ids: stop ids are UUIDs.
private const val SELECTED_PLACE_KEY = "selected-place"
private const val ROUTE_END_KEY = "route-end:"

/** The rings round a route's ends: under their pins (kept places and stops at 1), over the other places. */
private const val ROUTE_END_Z = 0

/** A route's ends and the places kept while routing: over the other places (and the rings), with the stops. */
private const val KEPT_PIN_Z = 1
private const val PLACE_KEY = "place:"
private const val CLUSTER_KEY = "cluster:"

private fun Color.toUIColor(): UIColor = UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())

/** The point [offset] from this one on a map at [zoom]: right and down for positive x and y. */
private fun Coordinates.shiftedBy(offset: DpOffset, zoom: Float): Coordinates = shiftedEast(offset.x, zoom).shiftedSouth(offset.y, zoom)
