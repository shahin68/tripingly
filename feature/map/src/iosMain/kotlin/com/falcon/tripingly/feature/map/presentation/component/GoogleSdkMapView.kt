package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import platform.UIKit.UIImage
import kotlin.math.abs

/**
 * Google's map on iOS, driven like the Android one: place pins as markers that keep their size on
 * screen, stops as numbered markers, the tapped place as a bigger marker on top.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GoogleSdkMapView(
    googleMaps: GoogleMapsSdk,
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
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

    // Where the camera last rested, so a camera target that is already there isn't sent again.
    val camera = remember { CameraRest() }
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
                    camera.center = Coordinates(latitude, longitude)
                    camera.zoom = zoom
                    currentOnCameraMove(Coordinates(latitude, longitude), zoom, bounds)
                }

                override fun onGestureStart() = currentOnMapTouch()

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

    LaunchedEffect(cameraTarget, zoomLevel) {
        val center = camera.center
        val moved = center == null ||
            abs(center.latitude - cameraTarget.latitude) > 0.00001 ||
            abs(center.longitude - cameraTarget.longitude) > 0.00001 ||
            abs(camera.zoom - zoomLevel) > 0.01
        // Until the map has rested once, it starts there instead of flying there.
        if (moved) map.moveCamera(cameraTarget.latitude, cameraTarget.longitude, zoomLevel, animated = center != null)
    }

    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { map.setStyle(if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE) }
    LaunchedEffect(isMyLocationEnabled) { map.setMyLocationEnabled(isMyLocationEnabled) }
    // As much room at the top as at the bottom keeps the camera's center in the middle of the view.
    LaunchedEffect(coveredBottom) { map.setPadding(coveredBottom.value.toDouble(), coveredBottom.value.toDouble()) }

    // Every pin look is drawn once and shared by every pin that looks the same.
    val categoryIcons = PlaceCategory.entries.associateWith { rememberVectorPainter(it.icon) }
    val placeColor = MaterialTheme.colorScheme.secondary
    val onPlaceColor = MaterialTheme.colorScheme.onSecondary
    val hotSpotColor = MaterialTheme.colorScheme.tertiary
    val onHotSpotColor = MaterialTheme.colorScheme.onTertiary
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val pinIcons = remember(placeColor, hotSpotColor, density) { mutableMapOf<PlacePinLook, UIImage>() }
    fun pinIcon(look: PlacePinLook) = pinIcons.getOrPut(look) {
        drawPlacePin(
            icon = categoryIcons.getValue(look.category),
            size = look.size,
            fill = if (look.isTripinly) hotSpotColor else placeColor,
            iconColor = if (look.isTripinly) onHotSpotColor else onPlaceColor,
            density = density,
            layoutDirection = layoutDirection,
        ).toUIImage(density.density)
    }
    val clusterIcons = remember(hotSpotColor, density) { mutableMapOf<Int, UIImage>() }

    // A stop's pin is drawn once per number and color.
    // By number, color and cover photo (once its thumbnail has loaded).
    val stopIcons = remember(density) { mutableMapOf<Triple<Int, Long, String?>, UIImage>() }
    val coverThumbs = rememberCoverThumbs(markers)
    LaunchedEffect(markers, coverThumbs, selectedPlace, placePins, placeClusters, pinIcons, clusterIcons) {
        val stops = markers.map { marker ->
            GoogleMapMarker(
                key = marker.id,
                latitude = marker.position.latitude,
                longitude = marker.position.longitude,
                title = marker.title,
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
        // The first pins (the most liked, then the best known) on top; the tapped place is drawn bigger below.
        val pins = placePins.filter { it.place.id != selectedPlace?.id }.mapIndexed { index, pin ->
            GoogleMapMarker(
                key = PLACE_KEY + pin.place.id,
                latitude = pin.place.location.lat,
                longitude = pin.place.location.lng,
                title = pin.place.name,
                icon = pinIcon(PlacePinLook(pin.place.category, pin.place.isTripinly, pin.size)),
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
                icon = pinIcon(PlacePinLook(it.category, it.isTripinly, SelectedPinSize)),
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

/** Where the camera last came to rest. */
private class CameraRest {
    var center: Coordinates? = null
    var zoom: Float = 0f
}

private fun PlaceCluster.key() = "$CLUSTER_KEY${location.lat},${location.lng}"

// Marker keys that aren't stop ids: stop ids are UUIDs.
private const val SELECTED_PLACE_KEY = "selected-place"
private const val PLACE_KEY = "place:"
private const val CLUSTER_KEY = "cluster:"
