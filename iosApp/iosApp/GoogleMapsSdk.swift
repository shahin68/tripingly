import Shared
import UIKit
#if canImport(GoogleMaps)
import GoogleMaps
#endif

/// The Google Maps SDK for the shared map screen. The SDK has no Kotlin binding, so this draws what
/// the shared code asks for and reports the user's touches and the camera back.
final class AppGoogleMapsSdk: NSObject, GoogleMapsSdk {

    static let shared = AppGoogleMapsSdk()

    /// The key comes from `MAPS_API_KEY_IOS` in local.properties; without it the map is Apple's.
    func start(apiKey: String) -> Bool {
        #if canImport(GoogleMaps)
        guard !apiKey.isEmpty else { return false }
        return GMSServices.provideAPIKey(apiKey)
        #else
        return false
        #endif
    }

    func createMap(listener: any GoogleMapListener) -> any GoogleMap {
        #if canImport(GoogleMaps)
        return AppGoogleMap(listener: listener)
        #else
        // The shared code asks for a map only after start(apiKey:) returned true.
        fatalError("The GoogleMaps package is missing")
        #endif
    }
}

#if canImport(GoogleMaps)
private final class AppGoogleMap: NSObject, GoogleMap, GMSMapViewDelegate {

    private let mapView = GMSMapView(options: GMSMapViewOptions())
    private let listener: any GoogleMapListener
    private var markers: [String: GMSMarker] = [:]

    var view: UIView { mapView }

    init(listener: any GoogleMapListener) {
        self.listener = listener
        super.init()
        mapView.delegate = self
        mapView.settings.compassButton = true
        mapView.settings.myLocationButton = false
        mapView.settings.indoorPicker = false
    }

    func setStyle(json: String) {
        mapView.mapStyle = try? GMSMapStyle(jsonString: json)
    }

    func setMyLocationEnabled(enabled: Bool) {
        mapView.isMyLocationEnabled = enabled
    }

    func setPadding(top: Double, bottom: Double) {
        mapView.padding = UIEdgeInsets(top: top, left: 0, bottom: bottom, right: 0)
    }

    func moveCamera(latitude: Double, longitude: Double, zoom: Float, animated: Bool) {
        let camera = GMSCameraPosition(latitude: latitude, longitude: longitude, zoom: zoom)
        if animated {
            // As fast as on Android (250 ms).
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.25)
            mapView.animate(to: camera)
            CATransaction.commit()
        } else {
            mapView.camera = camera
        }
    }

    func setMarkers(markers items: [GoogleMapMarker]) {
        var kept: [String: GMSMarker] = [:]
        for item in items {
            let marker = markers[item.key] ?? GMSMarker()
            if marker.map == nil { marker.appearAnimation = item.fadesIn ? .fadeIn : .none }
            marker.position = CLLocationCoordinate2D(latitude: item.latitude, longitude: item.longitude)
            marker.title = item.title
            if marker.icon !== item.icon { marker.icon = item.icon }
            marker.groundAnchor = CGPoint(x: item.anchorX, y: item.anchorY)
            marker.zIndex = item.zIndex
            marker.userData = item
            if marker.map == nil { marker.map = mapView }
            kept[item.key] = marker
        }
        for (key, marker) in markers where kept[key] == nil {
            marker.map = nil
        }
        markers = kept
    }

    func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
        // The whole view, not only the part inside the padding.
        let size = mapView.bounds.size
        let corners = [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: 0, y: size.height), CGPoint(x: size.width, y: size.height)]
            .map { mapView.projection.coordinate(for: $0) }
        listener.onCameraIdle(
            latitude: position.target.latitude,
            longitude: position.target.longitude,
            zoom: position.zoom,
            south: corners.map(\.latitude).min() ?? position.target.latitude,
            west: corners.map(\.longitude).min() ?? position.target.longitude,
            north: corners.map(\.latitude).max() ?? position.target.latitude,
            east: corners.map(\.longitude).max() ?? position.target.longitude
        )
    }

    func mapView(_ mapView: GMSMapView, willMove gesture: Bool) {
        if gesture { listener.onGestureStart() }
    }

    func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
        listener.onTap(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    func mapView(_ mapView: GMSMapView, didLongPressAt coordinate: CLLocationCoordinate2D) {
        listener.onLongPress(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        guard let item = marker.userData as? GoogleMapMarker else { return false }
        listener.onMarkerTap(key: item.key)
        // True: the map shows no info window and keeps its camera.
        return !item.showsInfoWindow
    }
}

#endif
