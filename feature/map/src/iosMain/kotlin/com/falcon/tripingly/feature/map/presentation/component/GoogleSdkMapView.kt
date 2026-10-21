package com.falcon.tripingly.feature.map.presentation.component

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    cameraCommands: Flow<CameraCommand>,
    contentPadding: PaddingValues,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    pinDotBelowZoom: Float,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
    line: MapLine?,
    isMyLocationEnabled: Boolean,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapTouch: () -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onPlacePinClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
) {
    val currentMarkers by rememberUpdatedState(markers)
    val currentPlacePins by rememberUpdatedState(placePins)
    val currentPlaceClusters by rememberUpdatedState(placeClusters)
    val currentOnPlacePinClick by rememberUpdatedState(onPlacePinClick)
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)
    val currentOnCameraMove by rememberUpdatedState(onCameraMove)
    val currentOnMapTouch by rememberUpdatedState(onMapTouch)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)

    // The map has rested once, so it has its size: a camera move flies from then on, and a fit can run.
    var isMapReady by remember { mutableStateOf(false) }
    // The camera's zoom as it moves, read only to tell when the pins turn into dots and back.
    var liveZoom by remember { mutableStateOf(initialZoom) }
    val isDots by remember(pinDotBelowZoom) { derivedStateOf { liveZoom < pinDotBelowZoom } }
    val map = remember(googleMaps) {
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
                    currentOnCameraMove(Coordinates(latitude, longitude), zoom, bounds)
                }

                override fun onGestureStart() = currentOnMapTouch()

                override fun onZoomChange(zoom: Float) {
                    liveZoom = zoom
                }

                override fun onTap(latitude: Double, longitude: Double) {
                    currentOnMapTouch()
                    currentOnMapClick(Coordinates(latitude, longitude))
                }

                override fun onLongPress(latitude: Double, longitude: Double) {
                    currentOnMapTouch()
                    currentOnMapLongClick(Coordinates(latitude, longitude))
                }

                override fun onMarkerTap(key: String) {
                    currentOnMapTouch()
                    when {
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

    LaunchedEffect(map) { map.moveCamera(initialTarget.latitude, initialTarget.longitude, initialZoom, animated = false) }
    LaunchedEffect(cameraCommands) {
        cameraCommands.collect { command ->
            when (command) {
                // Until the map has rested once, it starts there instead of flying there.
                is CameraCommand.MoveTo -> map.moveCamera(command.target.latitude, command.target.longitude, command.zoom, animated = isMapReady)
                is CameraCommand.Fit -> {
                    snapshotFlow { isMapReady }.first { it }
                    awaitPaddingFrames()
                    with(command.bounds) { map.fitBounds(south, west, north, east, FitMargin.value.toDouble(), animated = command.isAnimated) }
                }
            }
        }
    }

    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { map.setStyle(if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE) }
    LaunchedEffect(isMyLocationEnabled) { map.setMyLocationEnabled(isMyLocationEnabled) }
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    LaunchedEffect(top, bottom) { map.setPadding(top.value.toDouble(), bottom.value.toDouble()) }

    // Every pin look is drawn once and shared by every pin that looks the same.
    val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
    val placeColor = MaterialTheme.colorScheme.secondary
    val onPlaceColor = MaterialTheme.colorScheme.onSecondary
    val hotSpotColor = MaterialTheme.colorScheme.tertiary
    val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // The line under every pin, with its arrows, as on Android.
    LaunchedEffect(line) {
        if (line == null || line.points.size < 2) {
            map.setLine(emptyList(), StopPathColor.toUIColor(), 0.0, emptyList(), null, null)
            map.setLineSweep(emptyList(), MapLineSweepColor.toUIColor(), 0.0)
            return@LaunchedEffect
        }
        val points = line.points.map { GoogleMapPoint(it.latitude, it.longitude) }
        val width = line.style.width.value.toDouble()
        map.setLine(
            points = points,
            color = line.style.color.toUIColor(),
            width = width,
            arrows = line.arrows.map { GoogleMapArrow(it.position.latitude, it.position.longitude, it.bearing.toDouble()) },
            arrowIcon = drawPathArrow(density, layoutDirection, line.style.color).toUIImage(density.density),
            capIcon = drawLineCap(density, layoutDirection, line.style).toUIImage(density.density),
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
    // By number, color and cover photo (once its thumbnail has loaded).
    val stopIcons = remember(density) { mutableMapOf<Triple<Int, Long, String?>, UIImage>() }
    val coverThumbs = rememberCoverThumbs(markers)
    val stopTitles = markers.map { it.title.asString() }
    LaunchedEffect(markers, stopTitles, coverThumbs, pinPhotos, selectedPlace, placePins, placeClusters, pinIcons, clusterIcons, isDots) {
        val stops = markers.mapIndexed { index, marker ->
            GoogleMapMarker(
                key = marker.id,
                latitude = marker.position.latitude,
                longitude = marker.position.longitude,
                title = stopTitles[index],
                icon = marker.coverPhotoId?.let { coverThumbs[it] }.let { cover ->
                    stopIcons.getOrPut(Triple(marker.orderNumber, marker.color, marker.coverPhotoId.takeIf { cover != null })) {
                        if (cover == null) {
                            drawTripPin(marker.orderNumber, marker.color.toInt(), density.density, density.fontScale)
                        } else {
                            drawCoverPin(marker.orderNumber, marker.color.toInt(), cover, density.density, density.fontScale)
                        }
                    }
                },
                anchorX = 0.5,
                anchorY = 1.0,
                // Above the places.
                zIndex = 1,
                // The stop's card shows instead.
                showsInfoWindow = false,
                fadesIn = false,
            )
        }
        // The first pins (the most liked, then the best known) on top. A photo pin waits for its photo. The tapped
        // place's pin stays as it is and a bigger copy is drawn over it below, as on Android. Zoomed out below
        // pinDotBelowZoom, every pin is its dot; the map fades a pin into its new look.
        val pins = placePins.mapIndexedNotNull { index, pin ->
            if (pin.photo && pin.place.coverImageId !in pinPhotos) return@mapIndexedNotNull null
            GoogleMapMarker(
                key = PLACE_KEY + pin.place.id,
                latitude = pin.place.location.lat,
                longitude = pin.place.location.lng,
                title = pin.place.name,
                icon = pinIcon(
                    if (isDots) pin.dotLook() else PlacePinLook(pin.place.category, pin.place.isTripinly, pin.size, photoId(pin.place, pin.photo), pin.isAlongTheWay),
                ),
                anchorX = 0.5,
                anchorY = 0.5,
                zIndex = -1 - index,
                showsInfoWindow = false,
                fadesIn = true,
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
        map.setMarkers(pins + clusters + stops + listOfNotNull(place))
    }

    UIKitView(
        factory = { map.view },
        modifier = modifier.fillMaxSize(),
        // The map takes every touch at once, without waiting to see whether Compose scrolls.
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
    )
}

private fun PlaceCluster.key() = "$CLUSTER_KEY${location.lat},${location.lng}"

// Marker keys that aren't stop ids: stop ids are UUIDs.
private const val SELECTED_PLACE_KEY = "selected-place"
private const val PLACE_KEY = "place:"
private const val CLUSTER_KEY = "cluster:"

private fun Color.toUIColor(): UIColor = UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
