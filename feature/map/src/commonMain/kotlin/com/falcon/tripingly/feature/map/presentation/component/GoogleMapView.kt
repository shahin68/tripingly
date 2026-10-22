package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    /** Where the map starts; it moves only by [cameraCommands] and the user's fingers after that. */
    initialTarget: Coordinates,
    initialZoom: Float,
    /** Each move runs once, in order; fits are worked out into moves by the screen (see [fittedCamera]). */
    cameraCommands: Flow<CameraCommand.MoveTo>,
    /**
     * The parts the screen's header and cards cover. The camera centers and fits within the rest, and Google's
     * logo stays clear of them.
     */
    contentPadding: PaddingValues,
    markers: List<MapMarker>,
    /**
     * Our places and OpenStreetMap places, drawn under the stops as markers that keep their size on screen,
     * the first ones on top.
     */
    placePins: List<PlacePin>,
    /** Below this camera zoom the place pins turn into small dots, and grow back when zoomed in. */
    pinDotBelowZoom: Float,
    /** Bubbles of liked places, drawn while zoomed out. */
    placeClusters: List<PlaceCluster>,
    /** The tapped place, drawn bigger on top. */
    selectedPlace: MapPlace?,
    /** The line under the pins: a real route, or the stops joined in order; null for none. */
    line: MapLine?,
    isMyLocationEnabled: Boolean,
    /** The camera's center, zoom and visible area (null while the map can't tell yet). */
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    /**
     * A finger touched the map: a tap, a long press, or a drag or pinch starting, reported before the
     * click. Only iOS calls it: Compose doesn't see touches on the iOS map view, while on Android they
     * also reach the screen's own touch handling.
     */
    onMapTouch: () -> Unit,
    /** A tap on the map, not on a pin. */
    onMapClick: (Coordinates) -> Unit,
    onPlacePinClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
)

/** Room kept free around the points of a fit, inside the content padding. */
internal val FitMargin = 48.dp

/** How long the camera takes to fly to a pin, a search result, the user's location or a fit. */
internal const val CAMERA_MOVE_MILLIS = 180

/**
 * Where a fit of [bounds] puts the camera in a view [width] by [height] that the map sees after its content padding,
 * worked out the way the map SDKs fit (a 256 dp world at zoom 0, Web Mercator, [FitMargin] inside). The screen fits
 * this way every time, so a new map starts already fitted and a fit can keep out of what floats over the map: with
 * [raise], the bounds sit that far above the middle of the padded map, for a [height] that is less than it.
 */
internal fun fittedCamera(bounds: GeoBounds, width: Dp, height: Dp, raise: Dp = 0.dp): Pair<Coordinates, Float> {
    fun mercatorY(latitude: Double) = ln(tan(PI / 4 + latitude * PI / 360))
    val north = mercatorY(bounds.north)
    val south = mercatorY(bounds.south)
    val spanX = (bounds.east - bounds.west) / 360
    val spanY = (north - south) / (2 * PI)
    val fitWidth = (width - FitMargin * 2).value.coerceAtLeast(1f)
    val fitHeight = (height - FitMargin * 2).value.coerceAtLeast(1f)
    val zoom = log2(min(fitWidth / (WORLD_DP * spanX), fitHeight / (WORLD_DP * spanY))).toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM)
    val center = Coordinates(latitude = atan(sinh((north + south) / 2)) * 180 / PI, longitude = (bounds.west + bounds.east) / 2)
    // The camera's target is the middle of the padded map; moving it south by [raise] lifts the bounds by as much.
    return center.shiftedSouth(raise, zoom) to zoom
}

/** The point [by] south of this one on a map at [zoom] (north for a negative [by]), along Web Mercator as the SDKs draw. */
internal fun Coordinates.shiftedSouth(by: Dp, zoom: Float): Coordinates {
    val y = ln(tan(PI / 4 + latitude * PI / 360)) - by.value / (WORLD_DP * 2.0.pow(zoom.toDouble())) * 2 * PI
    return Coordinates(latitude = atan(sinh(y)) * 180 / PI, longitude = longitude)
}

/** How wide the whole world is at zoom 0. */
private const val WORLD_DP = 256.0
private const val MIN_ZOOM = 2f
private const val MAX_ZOOM = 21f
