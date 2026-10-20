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
    private var line: GMSPolyline?
    private var lineMarkers: [GMSMarker] = []
    private var lineSweep: GMSPolyline?

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
            // As fast as on Android (180 ms).
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.18)
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

    func setLine(points: [GoogleMapPoint], color: UIColor, width: Double, arrows: [GoogleMapArrow], arrowIcon: UIImage?, capIcon: UIImage?) {
        line?.map = nil
        line = nil
        lineMarkers.forEach { $0.map = nil }
        lineMarkers = []
        guard points.count > 1, let first = points.first, let last = points.last else { return }
        let polyline = GMSPolyline(path: path(of: points))
        polyline.strokeColor = color
        polyline.strokeWidth = CGFloat(width)
        polyline.map = mapView
        line = polyline
        // Flat, so the arrows turn with the map and keep pointing along the line.
        let arrowMarkers: [GMSMarker] = arrowIcon.map { icon in
            arrows.map { arrow in
                flatMarker(latitude: arrow.latitude, longitude: arrow.longitude, icon: icon, rotation: arrow.bearing)
            }
        } ?? []
        // GMSPolyline has square ends: a dot as wide as the line on each end rounds them.
        let capMarkers: [GMSMarker] = capIcon.map { icon in
            [first, last].map { point in flatMarker(latitude: point.latitude, longitude: point.longitude, icon: icon, rotation: 0) }
        } ?? []
        lineMarkers = capMarkers + arrowMarkers
    }

    private func flatMarker(latitude: Double, longitude: Double, icon: UIImage, rotation: Double) -> GMSMarker {
        let marker = GMSMarker(position: CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        marker.icon = icon
        marker.isFlat = true
        marker.rotation = rotation
        marker.groundAnchor = CGPoint(x: 0.5, y: 0.5)
        marker.isTappable = false
        marker.zIndex = 0
        marker.map = mapView
        return marker
    }

    func setLineSweep(points: [GoogleMapPoint], color: UIColor, width: Double) {
        guard points.count > 1 else {
            lineSweep?.map = nil
            lineSweep = nil
            return
        }
        let sweep = lineSweep ?? GMSPolyline()
        sweep.path = path(of: points)
        sweep.strokeColor = color
        sweep.strokeWidth = CGFloat(width)
        sweep.zIndex = 1
        if sweep.map == nil { sweep.map = mapView }
        lineSweep = sweep
    }

    private func path(of points: [GoogleMapPoint]) -> GMSPath {
        let path = GMSMutablePath()
        for point in points {
            path.add(CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude))
        }
        return path
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

    func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
        listener.onZoomChange(zoom: position.zoom)
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
