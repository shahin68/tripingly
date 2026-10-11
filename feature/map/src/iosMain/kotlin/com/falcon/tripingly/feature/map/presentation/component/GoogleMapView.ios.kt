package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import org.koin.compose.getKoin

/** Google's map once the app has handed the SDK its key (see [GoogleMapsSdk]), else Apple's. */
@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    selectedMarkerId: String?,
    placeSource: MapPlaceSource?,
    selectedPlace: MapPlace?,
    isMyLocationEnabled: Boolean,
    coveredBottom: Dp,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
    onSelectedPlaceMoved: (Offset) -> Unit,
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
            selectedMarkerId = selectedMarkerId,
            placeSource = placeSource,
            selectedPlace = selectedPlace,
            isMyLocationEnabled = isMyLocationEnabled,
            coveredBottom = coveredBottom,
            onCameraMove = onCameraMove,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onMarkerClick = onMarkerClick,
            onSelectedPlaceMoved = onSelectedPlaceMoved,
        )
    } else {
        AppleMapView(
            modifier = modifier,
            cameraTarget = cameraTarget,
            zoomLevel = zoomLevel,
            markers = markers,
            selectedMarkerId = selectedMarkerId,
            placeSource = placeSource,
            selectedPlace = selectedPlace,
            isMyLocationEnabled = isMyLocationEnabled,
            onCameraMove = onCameraMove,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onMarkerClick = onMarkerClick,
        )
    }
}
