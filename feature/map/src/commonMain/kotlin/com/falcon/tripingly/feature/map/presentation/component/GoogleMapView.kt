package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    /** Our places and OpenStreetMap places, drawn under the stops by map square; null draws none. */
    placeSource: MapPlaceSource?,
    /** The tapped place, drawn bigger on top. */
    selectedPlace: MapPlace?,
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
    /** Also a tap on a place pin: the pins are part of the map's tiles, see [MapPlaceSource]. */
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
)
