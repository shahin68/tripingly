package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.flow.Flow

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    /** Where the map starts; it moves only by [cameraCommands] and the user's fingers after that. */
    initialTarget: Coordinates,
    initialZoom: Float,
    /** Each command runs once, in order; a fit waits until the map has loaded. */
    cameraCommands: Flow<CameraCommand>,
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
