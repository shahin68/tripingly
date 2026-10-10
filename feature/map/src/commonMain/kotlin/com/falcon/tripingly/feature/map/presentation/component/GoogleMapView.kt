package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    /** Shows this marker's info window (Android) or callout (iOS). */
    selectedMarkerId: String?,
    /** Our places and OpenStreetMap places, drawn under the stops by map square; null draws none. */
    placeSource: MapPlaceSource?,
    /** The tapped place, drawn bigger on top. */
    selectedPlace: MapPlace?,
    isMyLocationEnabled: Boolean,
    /** The camera's center, zoom and visible area (null while the map can't tell yet). */
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    /** Also a tap on a place pin: the pins are part of the map's tiles, see [MapPlaceSource]. */
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
)
