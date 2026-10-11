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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import platform.UIKit.UIImage
import kotlin.math.abs

/**
 * Google's map on iOS, driven like the Android one: place pins painted into tiles, stops as
 * numbered markers, the tapped place as a bigger marker on top.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GoogleSdkMapView(
    googleMaps: GoogleMapsSdk,
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
    val currentMarkers by rememberUpdatedState(markers)
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
                    camera.bounds = bounds
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
                    currentMarkers.find { it.id == key }?.let(currentOnMarkerClick)
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

    // Every pin look is drawn once; the tiles copy them.
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
    )

    val tiles = remember(placeColor, hotSpotColor, density) {
        val looks = PlaceCategory.entries.flatMap { category ->
            listOf(PlacePinLook(category, false, PlainPinSize)) + HotSpotSizes.map { PlacePinLook(category, true, it) }
        }
        PlaceTilePainter(
            pins = looks.associateWith(::drawPin),
            clusterColor = hotSpotColor.toArgb(),
            onClusterColor = onHotSpotColor.toArgb(),
            density = density.density,
            fontScale = density.fontScale,
        ).also { it.source = placeSource }
    }
    LaunchedEffect(tiles) { map.setTiles(tiles) }
    // New stops hide their places: the tiles are drawn again without them.
    LaunchedEffect(tiles, placeSource) {
        if (tiles.source != placeSource) {
            tiles.source = placeSource
            map.redrawTiles()
        }
        // A square in view reloaded with new places: the squares reloaded together are drawn again
        // at once, from what the repository keeps.
        placeSource?.changes?.filter { it in camera.visibleSquares() }?.collectLatest {
            delay(REDRAW_AFTER_MILLIS)
            map.redrawTiles()
        }
    }

    // A stop's pin is drawn once per number and color.
    // By number, color and cover photo (once its thumbnail has loaded).
    val stopIcons = remember(density) { mutableMapOf<Triple<Int, Long, String?>, UIImage>() }
    val coverThumbs = rememberCoverThumbs(markers)
    val selectedPlaceIcon = remember(selectedPlace?.id, selectedPlace?.category, selectedPlace?.isTripinly, placeColor, hotSpotColor, density) {
        selectedPlace?.let { place ->
            drawPin(PlacePinLook(place.category, place.isTripinly, placePinSize(place, isSelected = true))).toUIImage(density.density)
        }
    }
    LaunchedEffect(markers, coverThumbs, selectedPlace, selectedPlaceIcon) {
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
            )
        }
        // The tapped place is a real marker, bigger and on top of its pin in the tiles. No info
        // window and no camera move of the map's own: the card and the camera are ours.
        val place = if (selectedPlace != null && selectedPlaceIcon != null) {
            GoogleMapMarker(
                key = SELECTED_PLACE_KEY,
                latitude = selectedPlace.location.lat,
                longitude = selectedPlace.location.lng,
                title = selectedPlace.name,
                icon = selectedPlaceIcon,
                anchorX = 0.5,
                anchorY = 0.5,
                zIndex = 2,
                showsInfoWindow = false,
            )
        } else {
            null
        }
        map.setMarkers(stops + listOfNotNull(place))
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
    var bounds: GeoBounds? = null

    fun visibleSquares(): List<PlaceSquare> = bounds?.let { PlaceSquare.covering(it, zoom.toInt()) }.orEmpty()
}

/** Not a stop id: stop ids are UUIDs. */
private const val SELECTED_PLACE_KEY = "selected-place"

/** Lets the squares of one background reload arrive before the tiles are drawn again. */
private const val REDRAW_AFTER_MILLIS = 300L
