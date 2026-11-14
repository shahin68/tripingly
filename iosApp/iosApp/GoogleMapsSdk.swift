import Shared
import UIKit
import UIKit.UIGestureRecognizerSubclass
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
    /// The pan of a pulled pin: its speed in points per second, and the display link that moves the camera each frame.
    private var panVelocity = CGVector.zero
    private var panLink: CADisplayLink?
    private let listener: any GoogleMapListener
    private var markers: [String: GMSMarker] = [:]
    private var line: GMSPolyline?
    private var lineMarkers: [GMSMarker] = []
    private var lineSweep: GMSPolyline?
    private let touches = TouchRecorder()
    /// A pin is held (the shared code took the long press): the finger pulls its line, the map's gestures are off.
    private var isHoldingPin = false

    var view: UIView { mapView }

    init(listener: any GoogleMapListener) {
        self.listener = listener
        super.init()
        mapView.delegate = self
        // Our own compass sits with the map buttons (MapCompass in the shared code).
        mapView.settings.compassButton = false
        mapView.settings.myLocationButton = false
        mapView.settings.indoorPicker = false
        mapView.addGestureRecognizer(touches)
        touches.onMove = { [weak self] point in
            guard let self, self.isHoldingPin else { return }
            self.listener.onPinHoldMove(x: Double(point.x), y: Double(point.y))
        }
        // A quick tap on a pin opens it as the finger lifts, without waiting for the map's own tap, which first waits
        // to see whether a second tap follows.
        touches.onTap = { [weak self] point in
            guard let self, !self.isHoldingPin, !self.touches.isDoubleTap, !self.touches.isTapHandled else { return }
            self.touches.isTapHandled = self.listener.onPinTap(x: Double(point.x), y: Double(point.y))
        }
        touches.onEnd = { [weak self] in
            guard let self, self.isHoldingPin else { return }
            self.isHoldingPin = false
            self.mapView.settings.setAllGesturesEnabled(true)
            self.listener.onPinHoldEnd()
        }
    }

    func setStyle(json: String) {
        mapView.mapStyle = try? GMSMapStyle(jsonString: json)
    }

    func setMyLocationEnabled(enabled: Bool) {
        mapView.isMyLocationEnabled = enabled
    }

    func setPadding(top: Double, left: Double, bottom: Double, right: Double) {
        // The screen's padding already counts the status bar, the home indicator and the camera cutout.
        mapView.paddingAdjustmentBehavior = .never
        mapView.padding = UIEdgeInsets(top: top, left: left, bottom: bottom, right: right)
    }

    func turnNorthUp() {
        CATransaction.begin()
        CATransaction.setAnimationDuration(0.18)
        mapView.animate(toBearing: 0)
        CATransaction.commit()
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

    func screenPoint(latitude: Double, longitude: Double) -> GoogleMapScreenPoint {
        let point = mapView.projection.point(for: CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        return GoogleMapScreenPoint(x: Double(point.x), y: Double(point.y))
    }

    func setPanVelocity(dx: Double, dy: Double) {
        panVelocity = CGVector(dx: dx, dy: dy)
        if dx == 0 && dy == 0 {
            panLink?.invalidate()
            panLink = nil
        } else if panLink == nil {
            let link = CADisplayLink(target: self, selector: #selector(panStep(_:)))
            link.add(to: .main, forMode: .common)
            panLink = link
        }
    }

    /// One display frame of a pan: the camera moves by the speed times the frame's length.
    @objc private func panStep(_ link: CADisplayLink) {
        let seconds = CGFloat(link.targetTimestamp - link.timestamp)
        mapView.moveCamera(GMSCameraUpdate.scrollBy(x: panVelocity.dx * seconds, y: panVelocity.dy * seconds))
    }

    private func path(of points: [GoogleMapPoint]) -> GMSPath {
        let path = GMSMutablePath()
        for point in points {
            path.add(CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude))
        }
        return path
    }

    /// The corners of the whole view on the map, not only the part inside the padding.
    private func viewCorners() -> [CLLocationCoordinate2D] {
        let size = mapView.bounds.size
        return [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: 0, y: size.height), CGPoint(x: size.width, y: size.height)]
            .map { mapView.projection.coordinate(for: $0) }
    }

    func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
        listener.onBearingChange(bearing: position.bearing)
        let corners = viewCorners()
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
        listener.onBearingChange(bearing: position.bearing)
        let corners = viewCorners()
        listener.onCameraChange(
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

    // The second tap of a double tap (double tap and drag zooms) is no tap or long press of its own.
    func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
        if touches.isDoubleTap || touches.isTapHandled { return }
        listener.onTap(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    func mapView(_ mapView: GMSMapView, didLongPressAt coordinate: CLLocationCoordinate2D) {
        if touches.isDoubleTap { return }
        let point = touches.point ?? mapView.projection.point(for: coordinate)
        // On a pin, the shared code holds it instead of dropping a pin there: the map stops panning under the finger.
        if listener.onLongPress(latitude: coordinate.latitude, longitude: coordinate.longitude, x: Double(point.x), y: Double(point.y)) {
            isHoldingPin = true
            mapView.settings.setAllGesturesEnabled(false)
        }
    }

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        guard let item = marker.userData as? GoogleMapMarker else { return false }
        // Opened already as the finger lifted.
        if touches.isTapHandled { return !item.showsInfoWindow }
        touches.isTapHandled = true
        listener.onMarkerTap(key: aimedMarker(tapped: item).key)
        // True: the map shows no info window and keeps its camera.
        return !item.showsInfoWindow
    }

    /**
     * The map hands a tap on overlapping pins to the one on top. Among the pins of the tapped one's kind (the part of
     * the key before ":", e.g. places), the one whose middle is nearest the finger is the one meant, as long as the
     * finger is on it (4 pt of slop).
     */
    private func aimedMarker(tapped: GoogleMapMarker) -> GoogleMapMarker {
        guard let touch = touches.point, let colon = tapped.key.firstIndex(of: ":") else { return tapped }
        let kind = tapped.key[...colon]
        var aimed = tapped
        var nearest = CGFloat.greatestFiniteMagnitude
        for marker in markers.values {
            guard let item = marker.userData as? GoogleMapMarker, item.key.hasPrefix(kind), let size = marker.icon?.size else { continue }
            let point = mapView.projection.point(for: marker.position)
            let middle = CGPoint(
                x: point.x + (0.5 - marker.groundAnchor.x) * size.width,
                y: point.y + (0.5 - marker.groundAnchor.y) * size.height
            )
            let distance = hypot(middle.x - touch.x, middle.y - touch.y)
            if distance <= size.width / 2 + 4 && distance < nearest {
                aimed = item
                nearest = distance
            }
        }
        return aimed
    }
}

/**
 * Notes where the last touch on the map began, and whether it came this soon after the one before (a double tap),
 * and follows that finger until it's lifted ([onMove], [onEnd]), without taking part in any of the map's gestures:
 * it never recognizes and runs alongside the map's own.
 */
private final class TouchRecorder: UIGestureRecognizer, UIGestureRecognizerDelegate {
    private(set) var point: CGPoint?
    private(set) var isDoubleTap = false
    var onMove: ((CGPoint) -> Void)?
    var onEnd: (() -> Void)?
    /// A quick tap that didn't move: called as the finger lifts, before [onEnd].
    var onTap: ((CGPoint) -> Void)?
    /// This touch's tap was handled (a pin opened): the map's own tap does nothing.
    var isTapHandled = false
    private var lastBegan: TimeInterval = -.infinity
    private var finger: UITouch?
    private var start: CGPoint = .zero
    private var hasMoved = false

    override init(target: Any? = nil, action: Selector? = nil) {
        super.init(target: target, action: action)
        cancelsTouchesInView = false
        delaysTouchesEnded = false
        delegate = self
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard finger == nil, let touch = touches.first else { return }
        finger = touch
        point = touch.location(in: view)
        start = point ?? .zero
        hasMoved = false
        isTapHandled = false
        isDoubleTap = event.timestamp - lastBegan < Self.doubleTapSeconds
        lastBegan = event.timestamp
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let finger, touches.contains(finger) else { return }
        let location = finger.location(in: view)
        point = location
        if hypot(location.x - start.x, location.y - start.y) > Self.tapSlop { hasMoved = true }
        onMove?(location)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let finger, touches.contains(finger) else { return }
        if !hasMoved && event.timestamp - lastBegan < Self.tapSeconds { onTap?(finger.location(in: view)) }
        lift()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let finger, touches.contains(finger) else { return }
        lift()
    }

    override func reset() {
        super.reset()
        finger = nil
    }

    private func lift() {
        finger = nil
        onEnd?()
        // Never recognized; failing resets it for the next touch.
        state = .failed
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }

    /** The first tap's length plus the platform's double tap time. */
    private static let doubleTapSeconds: TimeInterval = 0.5
    /** Longer is a long press, not a tap. */
    private static let tapSeconds: TimeInterval = 0.4
    /** A tap's finger may move this far. */
    private static let tapSlop: CGFloat = 10
}

#endif
