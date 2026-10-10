package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKFeatureDisplayPriorityDefaultLow
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPointOfInterestFilter
import platform.UIKit.UIColor
import platform.darwin.NSObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class)
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
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
) {
    // The squares in view, loaded once the region rests; see MapPlaceSource.
    var view by remember { mutableStateOf<Pair<GeoBounds, Int>?>(null) }
    var shown by remember { mutableStateOf(emptyList<PlacesInView>()) }
    LaunchedEffect(placeSource, view) {
        val (bounds, zoom) = view ?: return@LaunchedEffect
        val source = placeSource ?: return@LaunchedEffect
        delay(REGION_REST_MILLIS)
        val squares = PlaceSquare.covering(bounds, zoom)
        source.placesIn(squares)?.let { shown = it }
        // A square in view reloaded with new places is drawn again.
        source.changes.filter { it in squares }.collect { source.placesIn(squares)?.let { shown = it } }
    }

    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {
            var currentMarkers: List<MapMarker> = emptyList()
            /** Annotations of places and clusters, drawn smaller and grey under the stops. */
            var placeAnnotations: Set<MKPointAnnotation> = emptySet()

            override fun mapViewDidChangeVisibleRegion(mapView: MKMapView) {
                val coordinate = mapView.centerCoordinate
                val center = coordinate.useContents {
                    Coordinates(latitude, longitude)
                }
                // The inverse of the region set below, so a zoom the camera was sent to stays.
                val latitudeMeters = mapView.region.useContents { span.latitudeDelta } * 111_320.0
                val bounds = mapView.region.useContents {
                    GeoBounds(
                        south = center.latitude - span.latitudeDelta / 2,
                        west = center.longitude - span.longitudeDelta / 2,
                        north = center.latitude + span.latitudeDelta / 2,
                        east = center.longitude + span.longitudeDelta / 2,
                    )
                }
                val zoom = log2(40_000_000.0 / latitudeMeters).toFloat()
                view = bounds to floor(zoom).toInt()
                onCameraMove(center, zoom, bounds)
            }

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation is MKPointAnnotation && viewForAnnotation in placeAnnotations) {
                    val identifier = "Place"
                    val annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView
                        ?: MKMarkerAnnotationView(viewForAnnotation, identifier)
                    annotationView.annotation = viewForAnnotation
                    annotationView.canShowCallout = true
                    annotationView.markerTintColor = UIColor.grayColor
                    annotationView.glyphText = viewForAnnotation.subtitle
                    annotationView.displayPriority = MKFeatureDisplayPriorityDefaultLow
                    return annotationView
                }
                if (viewForAnnotation is MKPointAnnotation) {
                    val identifier = "TripMarker"
                    var annotationView = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier) as? MKMarkerAnnotationView
                    if (annotationView == null) {
                        annotationView = MKMarkerAnnotationView(viewForAnnotation, identifier)
                        annotationView.canShowCallout = true
                    } else {
                        annotationView.annotation = viewForAnnotation
                    }

                    // Find corresponding marker to set color and glyph
                    val marker = currentMarkers.find { 
                        it.title == viewForAnnotation.title 
                    }
                    
                    marker?.let { 
                        annotationView.glyphText = it.orderNumber.toString()
                        val color = it.color
                        annotationView.markerTintColor = UIColor(
                            red = ((color shr 16) and 0xFF) / 255.0,
                            green = ((color shr 8) and 0xFF) / 255.0,
                            blue = (color and 0xFF) / 255.0,
                            alpha = ((color shr 24) and 0xFF) / 255.0
                        )
                    }

                    return annotationView
                }
                return null
            }
        }
    }

    UIKitView(
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                // Places on our map come from our server only.
                pointOfInterestFilter = MKPointOfInterestFilter(includingCategories = emptyList<Any>())
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            delegate.currentMarkers = markers
            mapView.showsUserLocation = isMyLocationEnabled

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
                if (marker.id == selectedMarkerId) mapView.selectAnnotation(annotation, animated = true)
            }
            val placeAnnotations = shown.flatMap { it.places }.map { place ->
                MKPointAnnotation().apply {
                    setCoordinate(CLLocationCoordinate2DMake(place.location.lat, place.location.lng))
                    setTitle(place.name)
                }
            } + shown.flatMap { it.clusters }.map { cluster ->
                MKPointAnnotation().apply {
                    setCoordinate(CLLocationCoordinate2DMake(cluster.location.lat, cluster.location.lng))
                    // Shown as the glyph.
                    setSubtitle(cluster.count.toString())
                }
            }
            delegate.placeAnnotations = placeAnnotations.toSet()
            mapView.addAnnotations(placeAnnotations)
        }
    )
}

private const val REGION_REST_MILLIS = 300L
