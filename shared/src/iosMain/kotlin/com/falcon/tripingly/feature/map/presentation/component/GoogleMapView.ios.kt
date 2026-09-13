package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GoogleMapView(
    modifier: Modifier,
    cameraTarget: Coordinates,
    zoomLevel: Float,
    markers: List<MapMarker>,
    onMapClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit
) {
    UIKitView(
        factory = {
            MKMapView().apply {
                showsUserLocation = true
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            val center = CLLocationCoordinate2DMake(cameraTarget.latitude, cameraTarget.longitude)
            val distance = (40_000_000.0 / 2.0.pow(zoomLevel.toDouble()))
            val region = MKCoordinateRegionMakeWithDistance(center, distance, distance)
            mapView.setRegion(region, animated = true)

            // Synchronize annotations
            mapView.removeAnnotations(mapView.annotations)
            markers.forEach { marker ->
                val annotation = MKPointAnnotation().apply {
                    setCoordinate(
                        CLLocationCoordinate2DMake(
                            marker.position.latitude,
                            marker.position.longitude
                        )
                    )
                    setTitle(marker.title)
                    setSubtitle(marker.snippet)
                }
                mapView.addAnnotation(annotation)
            }
        }
    )
}
