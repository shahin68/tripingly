package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import org.koin.compose.getKoin

/** Google's map once the app has handed the SDK its key (see [GoogleMapsSdk]), else Apple's. */
@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    pinDotBelowZoom: Float,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
    routeLine: List<Coordinates>,
    stopPath: List<Coordinates>,
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
    val koin = getKoin()
    val googleMaps = remember(koin) { koin.getOrNull<GoogleMapsSdk>() }
    if (googleMaps != null) {
        GoogleSdkMapView(
            googleMaps = googleMaps,
            modifier = modifier,
            cameraTarget = cameraTarget,
            zoomLevel = zoomLevel,
            markers = markers,
            placePins = placePins,
            pinDotBelowZoom = pinDotBelowZoom,
            placeClusters = placeClusters,
            selectedPlace = selectedPlace,
            routeLine = routeLine,
            stopPath = stopPath,
            isMyLocationEnabled = isMyLocationEnabled,
            coveredBottom = coveredBottom,
            onCameraMove = onCameraMove,
            onMapTouch = onMapTouch,
            onMapClick = onMapClick,
            onPlacePinClick = onPlacePinClick,
            onClusterClick = onClusterClick,
            onMapLongClick = onMapLongClick,
            onMarkerClick = onMarkerClick,
        )
    } else {
        AppleMapView(
            modifier = modifier,
            cameraTarget = cameraTarget,
            zoomLevel = zoomLevel,
            markers = markers,
            placePins = placePins,
            placeClusters = placeClusters,
            selectedPlace = selectedPlace,
            isMyLocationEnabled = isMyLocationEnabled,
            onCameraMove = onCameraMove,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onMarkerClick = onMarkerClick,
        )
    }
}
