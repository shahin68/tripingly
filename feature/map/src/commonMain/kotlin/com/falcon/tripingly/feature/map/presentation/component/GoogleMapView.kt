package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.MapMarker

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    /**
     * Our places and OpenStreetMap places, drawn under the stops as markers that keep their size on screen,
     * the first ones on top.
     */
    placePins: List<PlacePin>,
    /** Bubbles of liked places, drawn while zoomed out. */
    placeClusters: List<PlaceCluster>,
    /** The tapped place, drawn bigger on top. */
    selectedPlace: MapPlace?,
    /** The route to draw under the pins, from start to end; empty for none. */
    routeLine: List<Coordinates>,
    /** The stops in order, joined by curved direction lines; empty for none. */
    stopPath: List<Coordinates>,
    isMyLocationEnabled: Boolean,
    /** How much of the map's bottom the screen's cards cover: Google's logo stays above it. */
    coveredBottom: Dp,
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

/** The route's line, in the stops' blue. */
internal val RouteColor = Color(0xFF2196F3)

internal val RouteWidth = 5.dp
