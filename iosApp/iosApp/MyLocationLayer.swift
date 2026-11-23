import CoreLocation
import UIKit
#if canImport(GoogleMaps)
import GoogleMaps

/// Our own "you are here" on the iOS map, instead of Google's, which the SDK always draws under every marker
/// (Shahin, 2026-11-23). A blue dot that pops in with one ripple and a halo; once the compass can be trusted the halo
/// gives way to a beam showing where the top of the phone points. Both are markers above every pin.
///
/// The beam is meant to be right, where Google's often isn't: it uses true north (magnetic north only until a location
/// is known), the compass is told how the phone is held (else it's 90° off in landscape), the beam is a flat marker so
/// it stays tied to north whatever the map's rotation and tilt, its width follows iOS's own accuracy and it hides when
/// iOS says the heading can't be trusted. It always turns the short way round.
final class MyLocationLayer: NSObject, CLLocationManagerDelegate {

    private weak var mapView: GMSMapView?
    private let locations = CLLocationManager()
    private let dotView = MyLocationDotView()
    private let dot = GMSMarker()
    private let beam = GMSMarker()
    private var isEnabled = false
    private var isRunning = false
    private var hasHeading = false
    /// The halo keeps the stage until the dot has popped in and the halo has shown for a moment, as on the canvas.
    private var haloHeldUntil = Date.distantPast
    private var headingChange: DispatchWorkItem?
    /// The dot's view is drawn into the map until the last running animation ends.
    private var drawViewUntil = Date.distantPast
    /// The beam's rotation so far, not wrapped to 0–360, so each turn takes the short way.
    private var beamRotation: CLLocationDegrees = 0
    private var beamWidth: BeamWidth?
    private var hiddenObservation: NSKeyValueObservation?

    init(mapView: GMSMapView) {
        self.mapView = mapView
        super.init()
        locations.delegate = self
        locations.desiredAccuracy = kCLLocationAccuracyBest
        // Small enough to feel live, big enough to skip the compass's jitter.
        locations.headingFilter = 2

        dot.iconView = dotView
        dot.tracksViewChanges = false
        dot.groundAnchor = CGPoint(x: 0.5, y: 0.5)
        dot.isTappable = false
        dot.zIndex = Int32.max

        // Flat: lies on the map, so its rotation counts from north and turns with the map.
        beam.isFlat = true
        beam.groundAnchor = CGPoint(x: 0.5, y: 0.5)
        beam.isTappable = false
        beam.zIndex = Int32.max - 1
        beam.opacity = 0

        // The map is hidden, not removed, while a screen covers it: no GPS or compass meanwhile.
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        hiddenObservation = mapView.observe(\.isHidden) { [weak self] _, _ in self?.update() }
        NotificationCenter.default.addObserver(
            self, selector: #selector(orientationChanged), name: UIDevice.orientationDidChangeNotification, object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        UIDevice.current.endGeneratingDeviceOrientationNotifications()
        locations.stopUpdatingLocation()
        locations.stopUpdatingHeading()
    }

    func setEnabled(_ enabled: Bool) {
        isEnabled = enabled
        update()
    }

    private func update() {
        let shouldRun = isEnabled && mapView?.isHidden == false
        guard shouldRun != isRunning else { return }
        isRunning = shouldRun
        if shouldRun {
            orientationChanged()
            locations.startUpdatingLocation()
            if CLLocationManager.headingAvailable() { locations.startUpdatingHeading() }
        } else {
            locations.stopUpdatingLocation()
            locations.stopUpdatingHeading()
            if !isEnabled { removeDot() }
        }
    }

    /// Off the map, ready to appear again with its halo.
    private func removeDot() {
        dot.map = nil
        beam.map = nil
        hasHeading = false
        headingChange?.cancel()
        beam.opacity = 0
        dotView.showHalo(true, animated: false)
    }

    /// The compass measures from the top of the phone as it's held; the beam must point where the screen's top points.
    /// Also checked with every heading, as the screen's orientation settles only after the rotation's animation.
    @objc private func orientationChanged() {
        let orientation: CLDeviceOrientation
        switch mapView?.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: orientation = .landscapeRight
        case .landscapeRight: orientation = .landscapeLeft
        case .portraitUpsideDown: orientation = .portraitUpsideDown
        default: orientation = .portrait
        }
        if locations.headingOrientation != orientation { locations.headingOrientation = orientation }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations updates: [CLLocation]) {
        guard let fix = updates.last, let mapView, fix.horizontalAccuracy >= 0 else { return }
        if dot.map == nil {
            dot.position = fix.coordinate
            beam.position = fix.coordinate
            dot.map = mapView
            beam.map = mapView
            animateView { self.dotView.playAppear() }
            haloHeldUntil = Date().addingTimeInterval(Self.haloHold)
            // The compass may have answered before the first location: the beam takes over once the halo had its moment.
            if hasHeading { swapHaloAndBeam() }
        } else {
            // Glides to the new place instead of jumping.
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.5)
            dot.position = fix.coordinate
            beam.position = fix.coordinate
            CATransaction.commit()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading heading: CLHeading) {
        orientationChanged()
        // Negative accuracy: iOS can't tell (needs calibrating, a magnet nearby). Beyond 60° it's a guess.
        let isTrusted = heading.headingAccuracy >= 0 && heading.headingAccuracy <= 60
        let direction = heading.trueHeading >= 0 ? heading.trueHeading : heading.magneticHeading
        guard isTrusted, direction >= 0 else {
            showHeading(false)
            return
        }
        let width = BeamWidth(accuracy: heading.headingAccuracy)
        if width != beamWidth {
            beamWidth = width
            beam.icon = width.image
        }
        let turn = (direction - beamRotation).truncatingRemainder(dividingBy: 360)
        let shortest = turn > 180 ? turn - 360 : (turn < -180 ? turn + 360 : turn)
        beamRotation += shortest
        CATransaction.begin()
        CATransaction.setAnimationDuration(hasHeading ? 0.2 : 0)
        beam.rotation = beamRotation
        CATransaction.commit()
        showHeading(true)
    }

    /// iOS's own "move your phone in a figure eight" when the compass needs it: a wrong beam is worse than the prompt.
    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool { true }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        if status == .denied || status == .restricted { removeDot() }
    }

    /// The halo and the beam swap slowly, one fading out as the other fades in; never while the dot is still appearing.
    private func showHeading(_ show: Bool) {
        guard show != hasHeading else { return }
        hasHeading = show
        swapHaloAndBeam()
    }

    private func swapHaloAndBeam() {
        headingChange?.cancel()
        // Nothing to swap until the dot is on the map: it appears with its halo first.
        guard dot.map != nil else { return }
        let show = hasHeading
        let change = DispatchWorkItem { [weak self] in
            guard let self else { return }
            CATransaction.begin()
            CATransaction.setAnimationDuration(Self.swap)
            self.beam.opacity = show ? 1 : 0
            CATransaction.commit()
            self.animateView { self.dotView.showHalo(!show, animated: true) }
        }
        headingChange = change
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0, haloHeldUntil.timeIntervalSinceNow), execute: change)
    }

    /// The dot's view is drawn into the map only while it animates, then left as a still image. Animations can overlap
    /// (the halo leaving while the dot still pops in), so it stops only after the last one has ended, never halfway.
    private func animateView(_ animation: () -> TimeInterval) {
        dot.tracksViewChanges = true
        let end = Date().addingTimeInterval(animation() + 0.05)
        drawViewUntil = max(drawViewUntil, end)
        DispatchQueue.main.asyncAfter(deadline: .now() + end.timeIntervalSinceNow) { [weak self] in
            // Only the timer of the animation that ends last stops it.
            guard let self, end >= self.drawViewUntil else { return }
            self.dot.tracksViewChanges = false
        }
    }

    /// From the dot's first appearance: pop and ripple (1.3 s), then the full halo for a moment.
    private static let haloHold: TimeInterval = 2.6
    /// How long the halo and the beam take to swap.
    static let swap: TimeInterval = 0.8
}

/// How wide the beam is drawn: a few steps of iOS's heading accuracy, so its image is redrawn only when the step changes.
private enum BeamWidth: Equatable {
    case narrow, medium, wide

    init(accuracy: CLLocationDegrees) {
        switch accuracy {
        case ..<15: self = .narrow
        case ..<30: self = .medium
        default: self = .wide
        }
    }

    private var degrees: CGFloat {
        switch self {
        case .narrow: return 40
        case .medium: return 60
        case .wide: return 90
        }
    }

    /// A wedge from the middle pointing north, Google's blue fading out to its rim.
    var image: UIImage {
        let side: CGFloat = 112
        let center = CGPoint(x: side / 2, y: side / 2)
        let radius = side / 2
        let half = degrees / 2 * .pi / 180
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { context in
            let path = UIBezierPath()
            path.move(to: center)
            path.addArc(withCenter: center, radius: radius, startAngle: -.pi / 2 - half, endAngle: -.pi / 2 + half, clockwise: true)
            path.close()
            path.addClip()
            let colors = [MyLocationDotView.blue.withAlphaComponent(0.6).cgColor, MyLocationDotView.blue.withAlphaComponent(0).cgColor]
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1]) else { return }
            context.cgContext.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [])
        }
    }
}

/// The dot itself: Google's blue with a white ring and a soft shadow, the halo around it and the ripple it sends once.
private final class MyLocationDotView: UIView {

    static let blue = UIColor(red: 66 / 255, green: 133 / 255, blue: 244 / 255, alpha: 1)
    private static let dotSize: CGFloat = 20
    private static let haloSize: CGFloat = 90

    private let halo = UIView()
    private let ripple = UIView()
    private let dot = UIView()

    init() {
        super.init(frame: CGRect(x: 0, y: 0, width: Self.haloSize, height: Self.haloSize))
        let middle = CGPoint(x: bounds.midX, y: bounds.midY)

        halo.frame = CGRect(x: 0, y: 0, width: Self.haloSize, height: Self.haloSize)
        halo.backgroundColor = Self.blue.withAlphaComponent(0.16)
        halo.layer.borderColor = Self.blue.withAlphaComponent(0.32).cgColor
        halo.layer.borderWidth = 1
        halo.layer.cornerRadius = Self.haloSize / 2

        ripple.frame = CGRect(x: 0, y: 0, width: Self.dotSize, height: Self.dotSize)
        ripple.center = middle
        ripple.layer.borderColor = Self.blue.cgColor
        ripple.layer.borderWidth = 2
        ripple.layer.cornerRadius = Self.dotSize / 2
        ripple.alpha = 0

        dot.frame = CGRect(x: 0, y: 0, width: Self.dotSize, height: Self.dotSize)
        dot.center = middle
        dot.backgroundColor = Self.blue
        dot.layer.cornerRadius = Self.dotSize / 2
        dot.layer.borderColor = UIColor.white.cgColor
        dot.layer.borderWidth = 3
        dot.layer.shadowColor = UIColor.black.cgColor
        dot.layer.shadowOpacity = 0.35
        dot.layer.shadowRadius = 2
        dot.layer.shadowOffset = CGSize(width: 0, height: 1)

        [halo, ripple, dot].forEach(addSubview)
    }

    required init?(coder: NSCoder) { fatalError("Not used from a storyboard") }

    /// The first fix: the dot pops in, sends one ripple, and the halo grows in. Returns how long it runs.
    @discardableResult
    func playAppear() -> TimeInterval {
        dot.transform = CGAffineTransform(scaleX: 0.01, y: 0.01)
        UIView.animate(withDuration: 0.45, delay: 0, usingSpringWithDamping: 0.5, initialSpringVelocity: 0) {
            self.dot.transform = .identity
        }
        ripple.alpha = 0.6
        ripple.transform = CGAffineTransform(scaleX: 0.6, y: 0.6)
        UIView.animate(withDuration: 1.2, delay: 0.1, options: .curveEaseOut) {
            self.ripple.alpha = 0
            self.ripple.transform = CGAffineTransform(scaleX: 3.6, y: 3.6)
        }
        if halo.alpha > 0 {
            halo.alpha = 0
            halo.transform = CGAffineTransform(scaleX: 0.3, y: 0.3)
            UIView.animate(withDuration: 0.6, delay: 0.25, options: .curveEaseOut) {
                self.halo.alpha = 1
                self.halo.transform = .identity
            }
        }
        return 1.3
    }

    /// The halo shows while there's no trusted heading and gives way to the beam once there is. Returns how long it runs.
    @discardableResult
    func showHalo(_ show: Bool, animated: Bool) -> TimeInterval {
        let change = {
            self.halo.alpha = show ? 1 : 0
            self.halo.transform = show ? .identity : CGAffineTransform(scaleX: 0.6, y: 0.6)
        }
        guard animated else {
            change()
            return 0
        }
        UIView.animate(withDuration: MyLocationLayer.swap, delay: 0, options: [.curveEaseInOut, .beginFromCurrentState], animations: change)
        return MyLocationLayer.swap
    }
}
#endif
