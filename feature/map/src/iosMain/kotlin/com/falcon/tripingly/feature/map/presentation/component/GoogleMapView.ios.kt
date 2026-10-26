package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import kotlinx.coroutines.flow.Flow
import org.koin.compose.getKoin

/** Google's map once the app has handed the SDK its key (see [GoogleMapsSdk]), else Apple's. */
@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    initialTarget: Coordinates,
    initialZoom: Float,
    cameraCommands: Flow<CameraCommand.MoveTo>,
    contentPadding: PaddingValues,
    layers: MapLayersState,
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
            initialTarget = initialTarget,
            initialZoom = initialZoom,
            cameraCommands = cameraCommands,
            contentPadding = contentPadding,
            markers = layers.stops,
            placePins = layers.placePins,
            pinDotBelowZoom = layers.pinDotBelowZoom,
            placeClusters = layers.placeClusters,
            selectedPlace = layers.selectedPlace,
            line = layers.line,
            isMyLocationEnabled = layers.isMyLocationEnabled,
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
            initialTarget = initialTarget,
            initialZoom = initialZoom,
            cameraCommands = cameraCommands,
            markers = layers.stops,
            placePins = layers.placePins,
            placeClusters = layers.placeClusters,
            selectedPlace = layers.selectedPlace,
            isMyLocationEnabled = layers.isMyLocationEnabled,
            onCameraMove = onCameraMove,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onMarkerClick = onMarkerClick,
        )
    }
}
