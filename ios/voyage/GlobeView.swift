import SwiftUI
import SceneKit
import Metal
import UIKit
import simd

struct GlobeView: UIViewRepresentable {
    let globeState: GlobeState
    /// When set, tapping a country calls this instead of selecting it —
    /// used by challenge games to treat taps as guesses.
    var onCountryTapped: ((String) -> Void)? = nil

    func makeUIView(context: Context) -> SCNView {
        let sceneView = SCNView()
        sceneView.scene = GlobeScene.createScene(globeState: globeState, coordinator: context.coordinator)
        sceneView.backgroundColor = .clear
        sceneView.allowsCameraControl = false
        sceneView.antialiasingMode = .multisampling4X
        // SceneKit's default, stated because the globe relies on it: the layers sit a
        // few ten-thousandths above each other (see `GlobeLayer`), which a reverse-Z
        // depth buffer resolves at every zoom.
        sceneView.usesReverseZ = true
        sceneView.autoenablesDefaultLighting = false
        // Per-frame far-side outline culling + drag/inertia motion (see renderer(_:updateAtTime:))
        sceneView.delegate = context.coordinator

        // Add gesture recognizers
        let panGesture = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handlePan(_:)))
        sceneView.addGestureRecognizer(panGesture)

        let pinchGesture = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handlePinch(_:)))
        sceneView.addGestureRecognizer(pinchGesture)

        let tapGesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        sceneView.addGestureRecognizer(tapGesture)

        // Double-tap-drag to zoom (like Google Maps)
        let doubleTapDragGesture = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTapDrag(_:)))
        doubleTapDragGesture.numberOfTapsRequired = 1
        doubleTapDragGesture.minimumPressDuration = 0.1
        sceneView.addGestureRecognizer(doubleTapDragGesture)

        context.coordinator.sceneView = sceneView

        return sceneView
    }

    /// Reads everything the globe draws from. `GlobeState` is `@Observable`, so
    /// SwiftUI calls `updateUIView` again only for properties read during it, and
    /// the coordinator's updates below return early — before the scene has
    /// loaded, say — without reaching some of them. Reading them all here keeps
    /// the globe subscribed whichever path an update takes.
    private func observeState() {
        _ = (globeState.selectedCountry, globeState.targetCountryCenter,
             globeState.pendingCameraTarget, globeState.homeRequest,
             globeState.isAutoRotating, globeState.visitedCountries,
             globeState.wishlistCountries, globeState.countryHighlightColors,
             globeState.globeStyle, globeState.showsTerrain, globeState.viewMode)
    }

    func updateUIView(_ uiView: SCNView, context: Context) {
        observeState()
        context.coordinator.onCountryTapped = onCountryTapped
        context.coordinator.updateVisibility()
        context.coordinator.updateGlobeStyle()
        context.coordinator.updateTerrain()
        context.coordinator.updateHighlights()
        context.coordinator.updateAutoRotation()
        context.coordinator.centerOnSelectedCountry()
        context.coordinator.applyPendingCameraTarget()
        context.coordinator.returnHomeIfRequested()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(globeState: globeState, onCountryTapped: onCountryTapped)
    }

    class Coordinator: NSObject {
        var globeState: GlobeState
        var onCountryTapped: ((String) -> Void)?
        weak var sceneView: SCNView?
        // The scene's nodes, stored once by `GlobeScene` when it builds the scene
        // so nothing here searches the node tree by name.
        var globeNode: SCNNode?
        var cameraNode: SCNNode?
        var oceanNode: SCNNode?
        /// Each country's fill: a polygon mesh, or a microstate's disc.
        var countryNodes: [String: SCNNode] = [:]
        /// The ring or disc behind each microstate's dot, by country name.
        var dotOutlineNodes: [String: SCNNode] = [:]
        /// Material shared by all outline sector nodes (one uniform update per zoom change)
        var sharedOutlineMaterial: SCNMaterial?
        /// Overlay node drawing the selected country's outline (thicker, colored, raised)
        private var selectedOutlineNode: SCNNode?
        private var selectedOutlineGeometries: [String: SCNGeometry] = [:]
        /// The selection `selectedOutlineNode` was last updated for.
        private var selectedOutlineCountry: String?

        /// Outline sector nodes with precomputed bounding spheres, culled per frame
        /// when beyond the globe's horizon (see renderer(_:updateAtTime:))
        private struct OutlineSector {
            let node: SCNNode
            let center: simd_float3   // bounding sphere center, globe-local
            let radius: Float
        }
        private var outlineSectors: [OutlineSector] = []

        func registerOutlineSectors(_ nodes: [SCNNode]) {
            outlineSectors = nodes.map { node in
                let sphere = node.boundingSphere
                return OutlineSector(node: node,
                                     center: simd_float3(Float(sphere.center.x), Float(sphere.center.y), Float(sphere.center.z)),
                                     radius: Float(sphere.radius))
            }
        }

        /// Keeps the horizon test from popping sectors right at the limb.
        static let horizonMargin: Float = 0.02

        /// Whether a mesh with this bounding sphere lies entirely beyond the globe's
        /// horizon — everything it draws is on the far side, hidden by the globe itself.
        ///
        /// A point p on the unit sphere is visible from a camera at distance d iff
        /// dot(p, camDir) >= 1/d. For a bounding sphere (center c, radius r),
        /// dot(p, camDir) <= dot(c, camDir) + r for every point it contains, so the whole
        /// mesh is safely behind the horizon once that upper bound falls below it.
        ///
        /// Conservative by construction: it can answer false for a mesh that happens to
        /// be invisible, never true for one with a visible vertex. Android's
        /// `GlobeCamera.isBeyondHorizon` is the same test.
        static func isBeyondHorizon(center: simd_float3,
                                    radius: Float,
                                    cameraDirection: simd_float3,
                                    distance: Float) -> Bool {
            guard distance > 1.0 else { return false }
            return simd_dot(center, cameraDirection) + radius < 1.0 / distance - horizonMargin
        }

        // Outline thickness (world units) at the default camera distance. Zoomed in,
        // the shader uniform is scaled down so outlines keep a constant on-screen width
        // instead of drowning small countries in black.
        static let baseOutlineThickness: Float = 0.0015
        static let selectedOutlineFactor: Float = 5.0 / 3.0   // matches the old 0.0025 selected width
        static let selectedOutlineRaise = GlobeLayer.selectedOutlineRaise  // lift above neighbours' outlines
        private static let referenceCameraDistance: Float = 4.0
        private static let globeRadius: Float = 1.0
        private static let basePanRotationSpeed: Float = 0.005
        /// Fraction of finger speed the globe tracks at when fully zoomed in. Below
        /// 1.0 the surface deliberately lags the finger, trading 1:1 feel for finer
        /// control when a single country fills the screen.
        private static let fullZoomTrackingFactor: Float = 0.6

        /// Outer radius of the capital seal, in world units at the default distance.
        private static let capitalMarkerRadius: Float = 0.015

        /// How much of the perspective shrink the capital marker compensates for.
        ///
        /// Scaling by the raw zoom scale would hold a fixed on-screen size; not
        /// scaling at all would balloon it to a third of the screen up close. On
        /// screen the seal goes as `zoomScale^-0.15`: it still grows as you zoom in,
        /// but gently — a camera 8x closer draws it about 1.4x larger rather than
        /// the 2.8x of a square root — so it neither shrinks to a speck framing
        /// Russia nor swamps a dot country. Android's `GlobeCamera.capitalMarkerRadius`
        /// ports this and the radius above, pinned by `GlobeCameraTest`.
        private static func capitalMarkerScale(zoomScale: Float) -> Float {
            pow(zoomScale, 0.85)
        }

        /// On-screen scale at `cameraDistance` relative to the reference distance.
        ///
        /// Perspective makes anything on the globe's near face cover a span
        /// proportional to `1 / (distance - radius)`, so both the outline width and
        /// the pan speed scale by this ratio to keep a constant on-screen size.
        private static func screenScale(cameraDistance: Float) -> Float {
            (cameraDistance - globeRadius) / (referenceCameraDistance - globeRadius)
        }

        /// Radians of globe rotation per point of finger travel.
        ///
        /// Speed grows with camera distance so the globe stays quick to spin when
        /// zoomed out. The quadratic falloff alone overshoots close in, though —
        /// because the on-screen arc a rotation sweeps grows as `1 / (distance -
        /// radius)`, at the minimum distance the surface ran ~35% ahead of the
        /// finger. Zoomed in past the reference distance the speed is therefore
        /// capped at finger tracking; zoomed out it is left alone.
        ///
        /// The cap tightens to `fullZoomTrackingFactor` of finger speed at the
        /// closest zoom and ramps back to 1:1 by the reference distance, so the
        /// extra damping lands where fine control matters and fades out before the
        /// default view. Both branches agree at the reference distance, so speed is
        /// continuous across zoom.
        private static func panRotationSpeed(cameraDistance: Float) -> Float {
            let distanceRatio = cameraDistance / referenceCameraDistance
            let quadratic = basePanRotationSpeed * distanceRatio * distanceRatio
            guard cameraDistance < referenceCameraDistance else { return quadratic }

            let zoomOutProgress = (cameraDistance - GlobeCamera.minCameraDistance) / (referenceCameraDistance - GlobeCamera.minCameraDistance)
            let tracking = fullZoomTrackingFactor
                + (1 - fullZoomTrackingFactor) * max(0, min(1, zoomOutProgress))
            return min(quadratic, basePanRotationSpeed * tracking * screenScale(cameraDistance: cameraDistance))
        }

        /// Last scale pushed to the outline materials (throttles uniform updates)
        private var currentOutlineScale: Float = 1.0
        /// Latest zoom scale, re-applied to the capital marker each time it is rebuilt
        private var currentZoomScale: Float = 1.0

        private var lastPanLocation: CGPoint = .zero
        // The camera's tilt and the globe's turn, in radians. Written by the render
        // thread (`applyInteractiveSpin`) and by main-thread gestures and flights,
        // so the stored values are guarded by `motionLock`; everything but
        // `applyInteractiveSpin`, which already holds the lock, goes through
        // `currentRotationX` / `currentRotationY`.
        private var rotationX: Float = 0
        private var rotationY: Float = GlobeScene.homeRotationY
        private var currentRotationX: Float {
            get { motionLock.lock(); defer { motionLock.unlock() }; return rotationX }
            set { motionLock.lock(); rotationX = newValue; motionLock.unlock() }
        }
        private var currentRotationY: Float {
            get { motionLock.lock(); defer { motionLock.unlock() }; return rotationY }
            set { motionLock.lock(); rotationY = newValue; motionLock.unlock() }
        }
        private var currentScale: Float = 1.0
        private var lastAutoRotatingState: Bool = true
        private var hasAnimatedToCountry: Bool = false
        private var lastAnimatedCountry: String?
        /// One seal per capital of the selected country (Bolivia has two).
        private var capitalStarNodes: [SCNNode] = []
        /// The country `capitalStarNodes` were built for: nil before the first
        /// update, `.some(nil)` when no seal is shown.
        private var capitalStarCountry: String??
        /// The plane every seal draws, built once (see `createStarNode`).
        private lazy var capitalMarkerGeometry: SCNPlane = {
            // A unit plane scaled down by the node, so the seal's radius is
            // `capitalMarkerRadius` in world units.
            let plane = SCNPlane(width: 2, height: 2)

            // `.constant` already ignores scene lighting, so the seal keeps the exact
            // theme colors on the night side without an emission wash over it.
            let material = SCNMaterial()
            material.diffuse.contents = CapitalMarker.image
            material.lightingModel = .constant
            plane.materials = [material]
            return plane
        }()

        /// The style last applied to each country's nodes, so an update touches
        /// only the countries whose style changed (see `updateHighlights`).
        private var appliedStyles: [String: CountryStyle] = [:]

        // The three shapes a microstate's outline node takes, shared by all 25
        // dots: a black ring around an unpainted dot, a black disc behind a
        // painted one, and the thicker ring of the selected dot. Only one dot is
        // selected at a time, so the selected ring's one material carries its color.
        private lazy var dotRingGeometry: SCNGeometry = Coordinator.makeDotOutline(SCNTube(innerRadius: 0.012, outerRadius: 0.014, height: 0.0005))
        private lazy var dotDiscGeometry: SCNGeometry = Coordinator.makeDotOutline(SCNCylinder(radius: 0.014, height: 0.0005))
        private lazy var selectedDotRingGeometry: SCNGeometry = Coordinator.makeDotOutline(SCNTube(innerRadius: 0.012, outerRadius: 0.0155, height: 0.0005))

        private static func makeDotOutline(_ geometry: SCNGeometry) -> SCNGeometry {
            let material = SCNMaterial()
            material.lightingModel = .constant
            material.isDoubleSided = true
            material.diffuse.contents = UIColor.black
            geometry.materials = [material]
            return geometry
        }
        private var doubleTapDragStartY: CGFloat = 0
        private var doubleTapDragStartDistance: Float = 0
        private var lastGlobeStyle: GlobeStyle?
        private var lastShowsTerrain: Bool?
        private var reliefTexture: MTLTexture?

        // Interactive spin state, applied on the render thread (renderer(_:updateAtTime:))
        // and fed from main-thread gesture handlers — guarded by motionLock.
        let motionLock = NSLock()
        let inertia = GlobeInertia()
        var pendingDragX: Float = 0
        var pendingDragY: Float = 0
        var lastRenderTime: TimeInterval = 0

        /// The last `GlobeState.homeRequest` this globe answered. Starts at the
        /// state's current value, so a globe built after a request has nothing
        /// to fly back from.
        private var lastHomeRequest: Int
        /// Bumped by every flight and every touch that takes the globe, so a
        /// flight's completion only runs if nothing has superseded it.
        private var flightGeneration = 0

        init(globeState: GlobeState, onCountryTapped: ((String) -> Void)? = nil) {
            self.globeState = globeState
            self.onCountryTapped = onCountryTapped
            self.lastHomeRequest = globeState.homeRequest
            super.init()
        }

        /// Points the camera at the globe's center with zero roll.
        ///
        /// Always passes an explicit world up: the parameterless `look(at:)` reuses the
        /// camera's *current* worldUp, so one aggressive drag/inertia frame that steps
        /// rotation X past ~90° (the clamp allows a 144° swing in a single frame) leaves
        /// that stale up pointing away from the new view direction — the resulting roll
        /// gets baked into the camera and every later `look(at:)` preserves it, making
        /// the globe's axis appear permanently tilted. A fixed up keeps the orientation
        /// deterministic (rotation X is clamped to ±72°, so the view direction never
        /// degenerates against +Y) and self-corrects any roll picked up earlier.
        func aimCameraAtGlobeCenter(_ cameraNode: SCNNode) {
            cameraNode.look(at: SCNVector3(0, 0, 0), up: SCNVector3(0, 1, 0), localFront: SCNVector3(0, 0, -1))
        }

        /// Scales the world-sized decorations — border outlines and the capital
        /// marker — with camera distance so they keep a constant on-screen size.
        ///
        /// At the default distance (4.0) they render at their base size. The lower
        /// clamp is the scale at the closest zoom, so it never binds inside the
        /// usable range: width stays constant all the way down instead of the
        /// outlines fattening once past the old 1/6 floor. Runs inside the caller's
        /// SCNTransaction, so animated zooms animate the sizing too.
        func updateZoomDependentSizing(cameraDistance: Float) {
            let minScale = Self.screenScale(cameraDistance: GlobeCamera.minCameraDistance)
            let scale = max(minScale, min(1.0, Self.screenScale(cameraDistance: cameraDistance)))

            // Cheap and needed on every call: the marker node is rebuilt whenever the
            // selection changes and has to pick up the current zoom scale.
            currentZoomScale = scale
            let markerScale = Self.capitalMarkerScale(zoomScale: scale)
            for node in capitalStarNodes {
                node.scale = SCNVector3(markerScale, markerScale, markerScale)
            }

            guard abs(scale - currentOutlineScale) > 0.005 else { return }
            currentOutlineScale = scale

            sharedOutlineMaterial?.setValue(Self.baseOutlineThickness * scale, forKey: "outlineThickness")
            if let selectedGeometry = selectedOutlineNode?.geometry {
                for material in selectedGeometry.materials {
                    material.setValue(Self.baseOutlineThickness * scale * Self.selectedOutlineFactor, forKey: "outlineThickness")
                }
            }
        }

        /// Whether the globe is the renderer on screen, rather than behind the map.
        private var isShown = true
        private var pendingPause: DispatchWorkItem?

        /// Pauses the scene while the map is shown. The view stays in the
        /// hierarchy at opacity 0 so the switch is an instant crossfade, and an
        /// `SCNView` there keeps rendering for as long as something animates —
        /// the idle spin, most of the time — at the display's full rate, unseen.
        ///
        /// It pauses only once the crossfade has run, so the globe fades out
        /// still turning, and resumes the moment it is asked back, so it fades in
        /// live.
        func updateVisibility() {
            let shown = globeState.viewMode == .globe
            guard shown != isShown else { return }
            isShown = shown
            pendingPause?.cancel()
            pendingPause = nil

            if shown {
                sceneView?.scene?.isPaused = false
                return
            }

            // A coasting flick would keep the view rendering continuously
            stopInertia()
            let pause = DispatchWorkItem { [weak self] in
                guard let self, !self.isShown else { return }
                self.sceneView?.scene?.isPaused = true
            }
            pendingPause = pause
            DispatchQueue.main.asyncAfter(deadline: .now() + HomeView.modeSwitchDuration + 0.1, execute: pause)
        }

        func updateAutoRotation() {
            guard let globeNode else { return }

            if globeState.isAutoRotating != lastAutoRotatingState {
                lastAutoRotatingState = globeState.isAutoRotating

                if globeState.isAutoRotating {
                    // Resume auto-rotation from current position
                    let rotation = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 60))
                    globeNode.runAction(rotation, forKey: "autoRotation")
                    hasAnimatedToCountry = false
                } else {
                    // Capture the current actual rotation from the presentation node before stopping
                    let currentActualRotationY = globeNode.presentation.eulerAngles.y

                    // Update our tracked rotation to match the actual position
                    currentRotationY = currentActualRotationY

                    // Set the model node to match (freeze at current position)
                    globeNode.eulerAngles.y = currentActualRotationY

                    // Now remove the action
                    globeNode.removeAction(forKey: "autoRotation")
                }
            }
        }

        func centerOnSelectedCountry() {
            guard let center = globeState.targetCountryCenter,
                  let selectedCountry = globeState.selectedCountry else { return }

            // Check if this is a new country selection
            if selectedCountry != lastAnimatedCountry {
                hasAnimatedToCountry = false
            }

            guard !hasAnimatedToCountry else { return }

            hasAnimatedToCountry = true
            lastAnimatedCountry = selectedCountry

            // Framed to the country's size: Luxembourg fills the view, Russia fits in it.
            guard let focus = CountryHitTester.shared.focus(of: selectedCountry) else {
                flyTo(lat: center.lat, lon: center.lon, distance: 2.8)
                return
            }
            let bounds = sceneView?.bounds ?? .zero
            let aspect = bounds.height > 0 ? Double(bounds.width / bounds.height) : 1
            let distance = GlobeCamera.distanceToFit(radiusDegrees: focus.radiusDegrees,
                                                    latitude: focus.lat, aspect: aspect)
            flyTo(lat: focus.lat, lon: focus.lon, distance: distance)
        }

        /// Consumes a one-shot camera flight request (e.g. a challenge game
        /// focusing its region at start).
        func applyPendingCameraTarget() {
            guard let target = globeState.pendingCameraTarget else { return }
            flyTo(lat: target.lat, lon: target.lon, distance: target.distance)
            DispatchQueue.main.async { [weak self] in
                self?.globeState.pendingCameraTarget = nil
            }
        }

        /// Turns the globe to where the user is — their time zone's city
        /// (`Daylight.location`), or the launch pose if the zone has none — at the
        /// launch distance when Home's tab is re-selected (`GlobeState.returnHome`),
        /// then lets the idle spin carry on from there.
        /// Android `GlobeSurfaceHost.returnHome`.
        func returnHomeIfRequested() {
            guard globeState.homeRequest != lastHomeRequest else { return }
            lastHomeRequest = globeState.homeRequest
            // Re-selecting the country just left flies to it again.
            hasAnimatedToCountry = false

            let home = Daylight.location()
            flyTo(lat: home?.latitude ?? 0,
                  lon: home?.longitude ?? GlobeScene.homeLongitude,
                  distance: GlobeScene.homeCameraDistance,
                  duration: GlobeCamera.returnHomeDuration) { [weak self] in
                guard let self, self.globeState.selectedCountry == nil else { return }
                self.globeState.isAutoRotating = true
            }
        }

        /// Animates the globe rotation and camera so the given lat/lon is centered
        /// at the given camera distance (nil keeps the current distance).
        ///
        /// `completion` runs on the main thread once the flight lands, and only if
        /// no later flight or touch has taken the globe in the meantime.
        func flyTo(lat: Double,
                   lon: Double,
                   distance: Float?,
                   duration: TimeInterval = 0.8,
                   completion: (() -> Void)? = nil) {
            guard let globeNode, let cameraNode else { return }

            // A flick still coasting would write its next step over the flight's
            // target from the render thread.
            stopInertia()

            let position = cameraNode.position
            let distance = distance ?? sqrt(position.x * position.x + position.y * position.y + position.z * position.z)

            // Capture the current actual rotation from the presentation node
            let currentActualRotationY = globeNode.presentation.eulerAngles.y

            // Immediately set the model node to match the presentation (freeze current position)
            globeNode.eulerAngles.y = currentActualRotationY

            // Convert longitude to globe Y rotation
            // Camera is at (0,0,z) looking at origin, so country needs to be on +Z side
            // lon=0 is at +X, lon=90 is at -Z, lon=-90 is at +Z
            // To center lon L: rotate by -(L + 90) degrees
            var targetRotationY = Float(-lon - 90) * .pi / 180.0

            // Normalize target rotation to take the shortest path from current rotation
            // Adjust target to be within -π to +π of current rotation
            let twoPi = Float.pi * 2
            while targetRotationY - currentActualRotationY > Float.pi {
                targetRotationY -= twoPi
            }
            while targetRotationY - currentActualRotationY < -Float.pi {
                targetRotationY += twoPi
            }

            flightGeneration += 1
            let generation = flightGeneration

            SCNTransaction.begin()
            // Behind the map nobody watches the flight, and the paused scene would
            // only play it once the globe is back: land at once, so the globe
            // already faces a country selected on the map when it fades in.
            SCNTransaction.animationDuration = isShown ? duration : 0
            SCNTransaction.animationTimingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
            if let completion {
                SCNTransaction.completionBlock = {
                    DispatchQueue.main.async { [weak self] in
                        guard let self, self.flightGeneration == generation else { return }
                        completion()
                    }
                }
            }

            // Rotate globe to center the location horizontally
            currentRotationY = targetRotationY
            globeNode.eulerAngles = SCNVector3(0, targetRotationY, 0)

            // Move camera to appropriate latitude
            // Positive lat (North) = camera moves up to look down at it
            let targetCameraX = max(-.pi / 2.5, min(.pi / 2.5, Float(lat) * .pi / 180.0))
            currentRotationX = targetCameraX

            cameraNode.position = SCNVector3(
                0,
                distance * sin(targetCameraX),
                distance * cos(targetCameraX)
            )
            aimCameraAtGlobeCenter(cameraNode)
            updateZoomDependentSizing(cameraDistance: distance)

            SCNTransaction.commit()
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let globeNode, let cameraNode else { return }

            // Scale rotation speed based on camera distance (zoom level)
            let cameraDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                      cameraNode.position.y * cameraNode.position.y +
                                      cameraNode.position.z * cameraNode.position.z)
            let rotationSpeed = Self.panRotationSpeed(cameraDistance: cameraDistance)

            // Gestures only accumulate rotation deltas; the render delegate applies them
            // once per rendered frame. Setting node transforms from main-thread gesture
            // events (~120Hz) lets the render thread sample one or two steps per frame at
            // random — visible judder while spinning, no matter how fast the GPU is.
            switch gesture.state {
            case .began:
                stopInertia()
                // Sync rotation state with actual visual position before stopping auto-rotation
                let currentActualRotationY = globeNode.presentation.eulerAngles.y
                currentRotationY = currentActualRotationY
                globeNode.eulerAngles.y = currentActualRotationY
                globeNode.removeAction(forKey: "autoRotation")
                globeState.isAutoRotating = false
                flightGeneration += 1  // a flight under the finger no longer resumes the spin
                sceneView?.rendersContinuously = true

            case .changed:
                let translation = gesture.translation(in: sceneView)
                motionLock.lock()
                pendingDragY += Float(translation.x) * rotationSpeed
                pendingDragX += Float(translation.y) * rotationSpeed
                motionLock.unlock()
                gesture.setTranslation(.zero, in: sceneView)

            case .ended, .cancelled:
                let velocity = gesture.velocity(in: sceneView)
                motionLock.lock()
                inertia.velocityY = Float(velocity.x) * rotationSpeed
                inertia.velocityX = Float(velocity.y) * rotationSpeed
                motionLock.unlock()

            default:
                break
            }
        }

        /// Drops a coasting flick and any drag not yet applied. Main thread only.
        private func stopInertia() {
            motionLock.lock()
            let wasCoasting = inertia.isActive
            inertia.reset()
            pendingDragX = 0
            pendingDragY = 0
            motionLock.unlock()

            // The render thread turns continuous rendering off when a coast runs
            // out by itself; one cut short here never gets there.
            if wasCoasting {
                sceneView?.rendersContinuously = false
            }
        }

        @objc func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard let globeNode, let cameraNode else { return }

            if gesture.state == .began {
                // Sync rotation state with actual visual position before stopping auto-rotation
                let currentActualRotationY = globeNode.presentation.eulerAngles.y
                currentRotationY = currentActualRotationY
                globeNode.eulerAngles.y = currentActualRotationY
                globeNode.removeAction(forKey: "autoRotation")
                globeState.isAutoRotating = false
                flightGeneration += 1  // a flight under the finger no longer resumes the spin
            }

            if gesture.state == .changed {
                let currentDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                          cameraNode.position.y * cameraNode.position.y +
                                          cameraNode.position.z * cameraNode.position.z)
                // Scale zoom speed proportionally to distance so it slows down when zoomed in
                let zoomSpeed: Float = currentDistance * 0.4    // After some manual testing, this felt like a good pinch-zoom sensitivity
                var newDistance = currentDistance - Float(gesture.scale - 1) * zoomSpeed

                // Clamp zoom level
                newDistance = max(GlobeCamera.minCameraDistance, min(GlobeCamera.maxCameraDistance, newDistance))

                cameraNode.position = SCNVector3(
                    0,
                    newDistance * sin(currentRotationX),
                    newDistance * cos(currentRotationX)
                )
                aimCameraAtGlobeCenter(cameraNode)
                updateZoomDependentSizing(cameraDistance: newDistance)

                gesture.scale = 1
            }
        }

        @objc func handleDoubleTapDrag(_ gesture: UILongPressGestureRecognizer) {
            guard let globeNode, let cameraNode else { return }

            let location = gesture.location(in: sceneView)

            switch gesture.state {
            case .began:
                doubleTapDragStartY = location.y
                doubleTapDragStartDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                                   cameraNode.position.y * cameraNode.position.y +
                                                   cameraNode.position.z * cameraNode.position.z)
                // Sync rotation state with actual visual position before stopping auto-rotation
                let currentActualRotationY = globeNode.presentation.eulerAngles.y
                currentRotationY = currentActualRotationY
                globeNode.eulerAngles.y = currentActualRotationY
                globeNode.removeAction(forKey: "autoRotation")
                globeState.isAutoRotating = false
                flightGeneration += 1  // a flight under the finger no longer resumes the spin

            case .changed:
                let deltaY = location.y - doubleTapDragStartY
                // Drag up = zoom in, drag down = zoom out: deltaY is positive
                // downwards, and distance grows with it. This is Apple's
                // direction — Maps, Find My and Weather all zoom this way — and
                // it is deliberately the opposite of Android's, where Google
                // Maps sets the convention and `GlobeCamera.zoomDraggedBy`
                // follows it. The sign here is not drift to be fixed.
                let zoomSpeed: Float = 0.01
                var newDistance = doubleTapDragStartDistance + Float(deltaY) * zoomSpeed

                // Clamp zoom level
                newDistance = max(GlobeCamera.minCameraDistance, min(GlobeCamera.maxCameraDistance, newDistance))

                cameraNode.position = SCNVector3(
                    0,
                    newDistance * sin(currentRotationX),
                    newDistance * cos(currentRotationX)
                )
                aimCameraAtGlobeCenter(cameraNode)
                updateZoomDependentSizing(cameraDistance: newDistance)

            default:
                break
            }
        }

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let sceneView = sceneView,
                  let globeNode = globeNode else { return }

            stopInertia()

            let location = gesture.location(in: sceneView)
            let countryName = Self.surfaceLatLon(at: location, in: sceneView, globeNode: globeNode)
                .flatMap { CountryHitTester.shared.findCountry(lat: $0.lat, lon: $0.lon) }

            if let onCountryTapped = onCountryTapped {
                if let countryName { onCountryTapped(countryName) }
            } else if let countryName {
                // The state change brings `updateUIView`, which restyles the globe.
                self.globeState.selectCountry(countryName, center: CountryHitTester.shared.center(of: countryName))
            } else if globeState.selectedCountry != nil {
                // Ocean or space: dismiss the country card, as MapView does
                self.globeState.deselectCountry()
            }
        }

        /// Exact tap → globe-surface lat/lon: unprojects the screen point into a
        /// world-space ray and intersects it with the globe sphere analytically.
        ///
        /// A SceneKit hitTest is deliberately not used here — its nearest hit is a
        /// fill or outline raised above the sphere, and normalizing that hit point
        /// misreads oblique taps, most of all near the screen edge at close zoom.
        static func surfaceLatLon(at point: CGPoint, in sceneView: SCNView,
                                  globeNode: SCNNode) -> (lat: Double, lon: Double)? {
            let nearWorld = sceneView.unprojectPoint(SCNVector3(Float(point.x), Float(point.y), 0))
            let farWorld = sceneView.unprojectPoint(SCNVector3(Float(point.x), Float(point.y), 1))

            // Globe-local space, via the presentation node so in-flight rotation
            // animations (centering flights, auto-rotation) are accounted for
            let globe = globeNode.presentation
            let nearLocal = globe.convertPosition(nearWorld, from: nil)
            let farLocal = globe.convertPosition(farWorld, from: nil)

            let origin = simd_double3(Double(nearLocal.x), Double(nearLocal.y), Double(nearLocal.z))
            let target = simd_double3(Double(farLocal.x), Double(farLocal.y), Double(farLocal.z))
            guard let surface = PolygonTriangulator.raySphereSurfaceDirection(origin: origin,
                                                                             direction: target - origin) else {
                return nil
            }
            return PolygonTriangulator.sphereToLatLon(surface)
        }

        func updateGlobeStyle() {
            guard globeState.globeStyle != lastGlobeStyle else { return }
            lastGlobeStyle = globeState.globeStyle

            guard let oceanMaterial = oceanNode?.geometry?.materials.first else { return }

            // Compressed and at full size if it is ready (see `EarthTexture`);
            // otherwise a small copy now, and the real one once it is made.
            let name = globeState.globeStyle.textureName
            guard let device = sceneView?.device ?? MTLCreateSystemDefaultDevice() else {
                showEarthTexture(EarthTexture.fallbackImage(named: name), on: oceanMaterial)
                return
            }
            if let texture = EarthTexture.cached(named: name, device: device) {
                showEarthTexture(texture, on: oceanMaterial)
                return
            }
            showEarthTexture(EarthTexture.preview(named: name), on: oceanMaterial)
            EarthTexture.load(named: name, device: device) { [weak self] contents in
                // The style may have changed again while this one was compressed
                guard let self, self.globeState.globeStyle.textureName == name else { return }
                self.showEarthTexture(contents, on: oceanMaterial)
            }
        }

        private func showEarthTexture(_ contents: Any?, on material: SCNMaterial) {
            guard let contents else { return }
            material.diffuse.contents = contents
            // Shift texture to align with country polygon coordinate system
            // latLonToSphere maps lon=0 to +X axis, but SCNSphere UV has U=0.5 offset
            material.diffuse.contentsTransform = SCNMatrix4MakeTranslation(-0.25, 0, 0)
            material.diffuse.wrapS = .repeat
            // Blend between mipmap levels, and keep the texture sharp where the
            // sphere turns away from the camera toward its edge
            material.diffuse.mipFilter = .linear
            material.diffuse.maxAnisotropy = 16
        }

        /// Hard-lights the relief over the ocean and the polygon fills, as the map
        /// does (see `TerrainRelief`). Microstate dots are markers and stay unshaded.
        func updateTerrain() {
            guard globeState.showsTerrain != lastShowsTerrain,
                  let sceneView, globeNode != nil else { return }
            lastShowsTerrain = globeState.showsTerrain

            if reliefTexture == nil, globeState.showsTerrain, let device = sceneView.device {
                reliefTexture = TerrainRelief.texture(device: device)
            }

            var materials = oceanNode?.geometry?.materials ?? []
            for (name, node) in countryNodes where dotOutlineNodes[name] == nil {
                materials += node.geometry?.materials ?? []
            }
            for material in materials {
                TerrainRelief.apply(to: material, texture: reliefTexture, enabled: globeState.showsTerrain)
            }
        }

        /// Restyles the countries whose `CountryStyle` differs from the one last
        /// applied. This runs on every state change the globe observes, most of
        /// which change no country at all; a selection changes two.
        func updateHighlights() {
            let visited = globeState.visitedCountries
            let wishlist = globeState.wishlistCountries
            let overrides = globeState.countryHighlightColors
            let selected = globeState.selectedCountry

            var selectedStyleChanged = false
            for (name, node) in countryNodes {
                let style = CountryStyle(isVisited: visited.contains(name),
                                         isWishlist: wishlist.contains(name),
                                         isSelected: selected == name,
                                         override: overrides[name])
                guard appliedStyles[name] != style else { continue }
                appliedStyles[name] = style
                if style.isSelected { selectedStyleChanged = true }

                applyFill(style.fill, to: node)

                // Point countries have their own outline node (dot ring/disc); polygon
                // countries share one merged outline node, with the selected country's
                // outline drawn by a separate overlay node (see updateSelectedOutline)
                if let outlineNode = dotOutlineNodes[name] {
                    applyDotOutline(style, to: outlineNode)
                }
            }

            // The overlay follows the selection, and its color the selected
            // country's style.
            if selectedStyleChanged || selected != selectedOutlineCountry {
                updateSelectedOutline()
            }
            updateCapitalStar()
        }

        /// A country's fill node is an overlay on the Earth texture: shown only
        /// while it has a paint to show.
        private func applyFill(_ paint: CountryStyle.Paint, to node: SCNNode) {
            guard paint != .plain, let geometry = node.geometry else {
                node.isHidden = true
                return
            }
            node.isHidden = false

            let color: UIColor
            switch paint {
            case .plain:
                return
            case .visitedAndWishlist:
                // The gradient is its own light and shade
                for material in geometry.materials {
                    material.transparency = 1.0
                    material.lightingModel = .constant
                    material.diffuse.contents = AppColors.visitedWishlistGradient
                    material.emission.contents = UIColor.black
                }
                return
            case .visited: color = AppColors.visitedUI
            case .wishlist: color = AppColors.wishlistUI
            case .custom(let custom): color = custom
            }
            for material in geometry.materials {
                material.transparency = 1.0
                material.lightingModel = .blinn
                material.diffuse.contents = color
                material.emission.contents = color.withAlphaComponent(0.15)
            }
        }

        /// A dot's outline is a ring while its fill is hidden (selected, or nothing
        /// to paint) and a disc behind the fill otherwise. The shapes are shared
        /// (see `dotRingGeometry`), so this only swaps which one the node draws.
        private func applyDotOutline(_ style: CountryStyle, to outlineNode: SCNNode) {
            outlineNode.isHidden = false
            if style.isSelected {
                selectedDotRingGeometry.firstMaterial?.diffuse.contents = borderContents(style.border)
                outlineNode.geometry = selectedDotRingGeometry
            } else if style.fill == .plain {
                outlineNode.geometry = dotRingGeometry
            } else {
                outlineNode.geometry = dotDiscGeometry
            }
        }

        /// What a border material's diffuse takes for `paint`.
        private func borderContents(_ paint: CountryStyle.Paint) -> Any {
            switch paint {
            case .plain: return UIColor.black
            case .visited: return AppColors.visitedUI
            case .wishlist: return AppColors.wishlistUI
            case .visitedAndWishlist: return AppColors.visitedWishlistGradient
            case .custom(let color): return color
            }
        }

        /// Shows the selected polygon country's outline as an overlay node: thicker,
        /// status-colored, and raised above the merged black outlines.
        private func updateSelectedOutline() {
            guard let globeNode else { return }
            selectedOutlineCountry = globeState.selectedCountry

            guard let name = globeState.selectedCountry,
                  let country = CountryDataCache.shared.countryNamed(name),
                  !country.isPointCountry else {
                selectedOutlineNode?.isHidden = true
                return
            }

            let geometry: SCNGeometry
            if let cached = selectedOutlineGeometries[name] {
                geometry = cached
            } else {
                guard let built = PolygonTriangulator.createBorderOutlineGeometry(polygons: country.polygons) else {
                    selectedOutlineNode?.isHidden = true
                    return
                }
                built.materials = [GlobeScene.makeOutlineMaterial()]
                selectedOutlineGeometries[name] = built
                geometry = built
            }

            let node: SCNNode
            if let existing = selectedOutlineNode {
                node = existing
            } else {
                node = SCNNode()
                node.name = "selected_outline"
                globeNode.addChildNode(node)
                selectedOutlineNode = node
            }
            node.geometry = geometry
            node.isHidden = false

            let style = CountryStyle(isVisited: globeState.visitedCountries.contains(name),
                                     isWishlist: globeState.wishlistCountries.contains(name),
                                     isSelected: true,
                                     override: globeState.countryHighlightColors[name])
            for material in geometry.materials {
                material.diffuse.contents = borderContents(style.border)
                material.setValue(Self.baseOutlineThickness * currentOutlineScale * Self.selectedOutlineFactor, forKey: "outlineThickness")
                material.setValue(Self.selectedOutlineRaise, forKey: "outlineRaise")
            }
        }

        /// Rebuilds the seals only when the country they mark changes.
        func updateCapitalStar() {
            guard let globeNode else { return }

            let marked = globeState.showsCapitalMarker ? globeState.selectedCountry : nil
            guard capitalStarCountry != .some(marked) else { return }
            capitalStarCountry = .some(marked)

            // Remove existing stars
            capitalStarNodes.forEach { $0.removeFromParentNode() }
            capitalStarNodes = []

            // If a country is selected, show a star at each of its capitals
            guard let marked, let country = CountryDataCache.shared.countryNamed(marked) else { return }

            for capital in country.capitals {
                let holder = createCapitalHolder(at: capital)
                globeNode.addChildNode(holder)
                capitalStarNodes.append(holder)
            }
        }

        /// The star seal at `capital`, laid on the globe and sized for the current zoom.
        private func createCapitalHolder(at capital: Capital) -> SCNNode {
            // Convert lat/lon to 3D position on sphere (radius slightly above surface)
            // Must match PolygonTriangulator.latLonToSphere coordinate system
            let radius = GlobeLayer.capitalStar  // Just above borders and dots
            let latRad = Float(capital.lat) * .pi / 180.0
            let lonRad = Float(-capital.lon) * .pi / 180.0  // Negative lon to match globe

            // Convert spherical to cartesian coordinates (matching globe's coordinate system)
            let x = radius * cos(latRad) * cos(lonRad)
            let y = radius * sin(latRad)
            let z = radius * cos(latRad) * sin(lonRad)

            // The zoom scale rides on a container so it doesn't overwrite the
            // marker's own size scale.
            let holder = SCNNode()
            holder.position = SCNVector3(x, y, z)
            let markerScale = Self.capitalMarkerScale(zoomScale: currentZoomScale)
            holder.scale = SCNVector3(markerScale, markerScale, markerScale)
            holder.addChildNode(createStarNode())

            // Lay the seal flat on the globe's tangent plane with the star's point
            // facing north, as Android does: the plane's +Z is the outward normal,
            // its +Y the local north. A tangent plane only rises off the sphere
            // towards its edges, so it never dips into the surface, and the globe
            // hides it by depth once the capital turns away.
            let normal = simd_normalize(simd_float3(x, y, z))
            let east = simd_normalize(simd_cross(simd_float3(0, 1, 0), normal))
            let north = simd_cross(normal, east)
            holder.simdOrientation = simd_quatf(simd_float3x3(east, north, normal))
            return holder
        }

        func createStarNode() -> SCNNode {
            // The star seal, drawn by `CapitalMarker` into a texture on a flat
            // plane. The holder lays it on the surface facing outward, so only the
            // front face is ever seen.
            let starNode = SCNNode(geometry: capitalMarkerGeometry)
            starNode.name = "capitalMarker"
            let radius = Self.capitalMarkerRadius
            starNode.scale = SCNVector3(radius, radius, radius)

            return starNode
        }
    }
}

extension GlobeView.Coordinator: SCNSceneRendererDelegate {
    /// Runs once per rendered frame, on the render thread. Applies interactive spin
    /// (drag deltas + inertia) and then hides far-side outline sectors.
    ///
    /// Motion is applied here rather than in gesture handlers because main-thread
    /// transform writes are sampled by the render thread at random phase — some frames
    /// see one gesture step, some two — which shows as judder while spinning. Applying
    /// exactly one integration step per rendered frame makes motion even by construction
    /// (the auto-rotation SCNAction is smooth for the same reason).
    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
        applyInteractiveSpin(atTime: time)
        cullFarSideOutlineSectors()
    }

    private func applyInteractiveSpin(atTime time: TimeInterval) {
        let dt = lastRenderTime > 0 ? Float(min(max(time - lastRenderTime, 0), 0.1)) : 0
        lastRenderTime = time

        motionLock.lock()
        var dx = pendingDragX
        var dy = pendingDragY
        pendingDragX = 0
        pendingDragY = 0
        let coasting = inertia.isActive
        if coasting && dt > 0 {
            let step = inertia.step(dt: dt)
            dx += step.dx
            dy += step.dy
        }
        let coastingEnded = coasting && !inertia.isActive
        // Stepped under the lock, so a flight starting on the main thread either
        // lands before this step or replaces it — never half of each.
        let moved = dx != 0 || dy != 0
        if moved {
            rotationY += dy
            rotationX = max(-.pi / 2.5, min(.pi / 2.5, rotationX + dx))
        }
        let turn = rotationY
        let tilt = rotationX
        motionLock.unlock()

        if moved, let globeNode, let cameraNode {
            globeNode.eulerAngles = SCNVector3(0, turn, 0)

            let position = cameraNode.position
            let distance = sqrt(position.x * position.x + position.y * position.y + position.z * position.z)
            cameraNode.position = SCNVector3(0, distance * sin(tilt), distance * cos(tilt))
            aimCameraAtGlobeCenter(cameraNode)
        }

        if coastingEnded {
            // Momentum finished: stop forcing continuous rendering (the auto-rotation
            // action, when enabled, drives rendering by itself)
            DispatchQueue.main.async { [weak self] in
                self?.sceneView?.rendersContinuously = false
            }
        }
    }

    /// Hides outline sectors that are entirely beyond the globe's horizon. The outline
    /// mesh dominates the scene's vertex count, and frustum culling never removes the
    /// far side (the whole globe fits the frustum except at close zoom), so without
    /// this every border vertex is processed every frame.
    private func cullFarSideOutlineSectors() {
        guard !outlineSectors.isEmpty,
              let cameraNode = sceneView?.pointOfView,
              let globeNode = globeNode else { return }

        let cameraWorld = cameraNode.presentation.worldPosition
        let cameraLocal = globeNode.presentation.convertPosition(cameraWorld, from: nil)
        let camera = simd_float3(Float(cameraLocal.x), Float(cameraLocal.y), Float(cameraLocal.z))
        let distance = simd_length(camera)
        guard distance > 1.0 else { return }

        let cameraDirection = camera / distance

        for sector in outlineSectors {
            let hidden = GlobeView.Coordinator.isBeyondHorizon(center: sector.center,
                                                              radius: sector.radius,
                                                              cameraDirection: cameraDirection,
                                                              distance: distance)
            if sector.node.isHidden != hidden {
                sector.node.isHidden = hidden
            }
        }
    }
}
