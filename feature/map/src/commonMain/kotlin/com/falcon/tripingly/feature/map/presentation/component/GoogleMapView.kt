package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker

@Composable
expect fun GoogleMapView(
    modifier: Modifier = Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    /** Shows this marker's info window (Android) or callout (iOS). */
    selectedMarkerId: String?,
    /** Our places and OpenStreetMap places in view, drawn under the stops. */
    places: List<MapPlace>,
    /** The tapped place, drawn bigger. */
    selectedPlaceId: String?,
    clusters: List<PlaceCluster>,
    isMyLocationEnabled: Boolean,
    /** The camera's center, zoom and visible area (null while the map can't tell yet). */
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onPlaceClick: (MapPlace) -> Unit,
    onClusterClick: (PlaceCluster) -> Unit,
)
