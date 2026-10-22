package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.feature.map.domain.model.CameraCommand
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import kotlinx.coroutines.flow.Flow
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKFeatureDisplayPriorityDefaultLow
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKMapPointForCoordinate
import platform.MapKit.MKMapRect
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPointOfInterestFilter
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.darwin.NSObject
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * Apple's map, shown when the app has no Google Maps key (`MAPS_API_KEY_IOS` in local.properties)
 * or package. Stops and place pins only: it takes no taps.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun AppleMapView(
    modifier: Modifier,
    initialTarget: Coordinates,
    initialZoom: Float,
    cameraCommands: Flow<CameraCommand>,
    contentPadding: PaddingValues,
    markers: List<MapMarker>,
    placePins: List<PlacePin>,
    placeClusters: List<PlaceCluster>,
    selectedPlace: MapPlace?,
    isMyLocationEnabled: Boolean,
    onCameraMove: (Coordinates, Float, GeoBounds?) -> Unit,
    onMapClick: (Coordinates) -> Unit,
    onMapLongClick: (Coordinates) -> Unit,
    onMarkerClick: (MapMarker) -> Unit,
) {
    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {
            /** The stops by their title, which is all an annotation keeps. */
            var currentMarkers: Map<String, MapMarker> = emptyMap()
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
    val marker = currentMarkers[viewForAnnotation.title]

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

    val mapView = remember {
        MKMapView().apply {
            this.delegate = delegate
            // Places on our map come from our server only.
            pointOfInterestFilter = MKPointOfInterestFilter(includingCategories = emptyList<Any>())
            setRegion(regionAround(initialTarget, initialZoom), animated = false)
        }
    }
    // The latest fit holds until the camera is sent elsewhere, and fits again when what the screen covers changes.
    var fit by remember { mutableStateOf<CameraCommand.Fit?>(null) }
    var fitCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(cameraCommands) {
        cameraCommands.collect { command ->
            when (command) {
                is CameraCommand.MoveTo -> {
                    fit = null
                    mapView.setRegion(mapView.regionThatFits(regionAround(command.target, command.zoom)), animated = true)
                }
                is CameraCommand.Fit -> {
                    fit = command
                    fitCount++
                }
            }
        }
    }
    LaunchedEffect(fit, fitCount, contentPadding) {
        val command = fit ?: return@LaunchedEffect
        // MapKit fits the rectangle itself, keeping out of what the header and cards cover.
        mapView.setVisibleMapRect(
            mapRect(command.bounds),
            edgePadding = UIEdgeInsetsMake(
                top = contentPadding.calculateTopPadding().value + FitMargin.value.toDouble(),
                left = FitMargin.value.toDouble(),
                bottom = contentPadding.calculateBottomPadding().value + FitMargin.value.toDouble(),
                right = FitMargin.value.toDouble(),
            ),
            animated = true,
        )
    }
    val titles = markers.map { it.title.asString() }
    UIKitView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            delegate.currentMarkers = titles.zip(markers).toMap()
            mapView.showsUserLocation = isMyLocationEnabled

            // Synchronize annotations
            mapView.removeAnnotations(mapView.annotations)
            markers.forEachIndexed { index, marker ->
                val annotation = MKPointAnnotation().apply {
                    setCoordinate(
                        CLLocationCoordinate2DMake(
                            marker.position.latitude,
                            marker.position.longitude
                        )
                    )
                    setTitle(titles[index])
                    setSubtitle(marker.snippet)
                }
                mapView.addAnnotation(annotation)
            }
            val placeAnnotations = placePins.map { pin ->
                MKPointAnnotation().apply {
                    setCoordinate(CLLocationCoordinate2DMake(pin.place.location.lat, pin.place.location.lng))
                    setTitle(pin.place.name)
                }
            } + placeClusters.map { cluster ->
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

/** [zoom] as Google's: the world is 40,000 km wide at zoom 0. */
@OptIn(ExperimentalForeignApi::class)
private fun regionAround(target: Coordinates, zoom: Float) = MKCoordinateRegionMakeWithDistance(
    CLLocationCoordinate2DMake(target.latitude, target.longitude),
    40_000_000.0 / 2.0.pow(zoom.toDouble()),
    40_000_000.0 / 2.0.pow(zoom.toDouble()),
)

/** The map's own rectangle around [bounds]. */
@OptIn(ExperimentalForeignApi::class)
private fun mapRect(bounds: GeoBounds): CValue<MKMapRect> {
    val southWest = MKMapPointForCoordinate(CLLocationCoordinate2DMake(bounds.south, bounds.west))
    val northEast = MKMapPointForCoordinate(CLLocationCoordinate2DMake(bounds.north, bounds.east))
    val (x1, y1) = southWest.useContents { x to y }
    val (x2, y2) = northEast.useContents { x to y }
    return cValue {
        origin.x = minOf(x1, x2)
        origin.y = minOf(y1, y2)
        size.width = abs(x2 - x1)
        size.height = abs(y2 - y1)
    }
}

