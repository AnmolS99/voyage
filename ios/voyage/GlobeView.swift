import SwiftUI
import SceneKit
import UIKit
import simd

struct GlobeView: UIViewRepresentable {
    @ObservedObject var globeState: GlobeState
    /// When set, tapping a country calls this instead of selecting it —
    /// used by challenge games to treat taps as guesses.
    var onCountryTapped: ((String) -> Void)? = nil

    func makeUIView(context: Context) -> SCNView {
        let sceneView = SCNView()
        sceneView.scene = GlobeScene.createScene(globeState: globeState, coordinator: context.coordinator)
        sceneView.backgroundColor = .clear
        sceneView.allowsCameraControl = false
        sceneView.antialiasingMode = .multisampling4X
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

    func updateUIView(_ uiView: SCNView, context: Context) {
        context.coordinator.onCountryTapped = onCountryTapped
        context.coordinator.updateGlobeStyle()
        context.coordinator.updateHighlights()
        context.coordinator.updateAutoRotation()
        context.coordinator.centerOnSelectedCountry()
        context.coordinator.applyPendingCameraTarget()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(globeState: globeState, onCountryTapped: onCountryTapped)
    }

    class Coordinator: NSObject {
        var globeState: GlobeState
        var onCountryTapped: ((String) -> Void)?
        weak var sceneView: SCNView?
        var globeNode: SCNNode?
        var countryNodes: [String: SCNNode] = [:]
        var originalColors: [String: UIColor] = [:]
        var cachedCountries: [GeoJSONCountry] = []
        /// Material shared by all outline sector nodes (one uniform update per zoom change)
        var sharedOutlineMaterial: SCNMaterial?
        /// Overlay node drawing the selected country's outline (thicker, colored, raised)
        private var selectedOutlineNode: SCNNode?
        private var selectedOutlineGeometries: [String: SCNGeometry] = [:]

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

        // Outline thickness (world units) at the default camera distance. Zoomed in,
        // the shader uniform is scaled down so outlines keep a constant on-screen width
        // instead of drowning small countries in black.
        static let baseOutlineThickness: Float = 0.0015
        static let selectedOutlineFactor: Float = 5.0 / 3.0   // matches the old 0.0025 selected width
        static let selectedOutlineRaise: Float = 0.001        // lift above neighbours' outlines
        private static let referenceCameraDistance: Float = 4.0
        private static let globeRadius: Float = 1.0
        private static let basePanRotationSpeed: Float = 0.005
        /// Fraction of finger speed the globe tracks at when fully zoomed in. Below
        /// 1.0 the surface deliberately lags the finger, trading 1:1 feel for finer
        /// control when a single country fills the screen.
        private static let fullZoomTrackingFactor: Float = 0.6

        /// Outer radius of the capital star, in world units at the default distance.
        /// Slightly larger than the 0.006 dot it replaces — a star of equal radius
        /// carries less visual weight than a filled circle.
        private static let capitalMarkerRadius: Float = 0.008

        /// How much of the perspective shrink the capital marker compensates for.
        ///
        /// Scaling by the raw zoom scale (fixed on-screen size) leaves a ~4pt speck
        /// once a single country fills the screen; not scaling at all (fixed world
        /// size) balloons it to a third of the screen. The square root sits between,
        /// so at the closest zoom the marker reads about 5x larger than at the
        /// default distance without covering the capital it marks.
        private static func capitalMarkerScale(zoomScale: Float) -> Float {
            sqrt(zoomScale)
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

            let zoomOutProgress = (cameraDistance - GlobeState.minCameraDistance) / (referenceCameraDistance - GlobeState.minCameraDistance)
            let tracking = fullZoomTrackingFactor
                + (1 - fullZoomTrackingFactor) * max(0, min(1, zoomOutProgress))
            return min(quadratic, basePanRotationSpeed * tracking * screenScale(cameraDistance: cameraDistance))
        }

        /// Last scale pushed to the outline materials (throttles uniform updates)
        private var currentOutlineScale: Float = 1.0
        /// Latest zoom scale, re-applied to the capital marker each time it is rebuilt
        private var currentZoomScale: Float = 1.0

        private var lastPanLocation: CGPoint = .zero
        private var currentRotationX: Float = 0
        private var currentRotationY: Float = -.pi / 2 - .pi / 12
        private var currentScale: Float = 1.0
        private var lastAutoRotatingState: Bool = true
        private var hasAnimatedToCountry: Bool = false
        private var lastAnimatedCountry: String?
        private var capitalStarNode: SCNNode?
        private var doubleTapDragStartY: CGFloat = 0
        private var doubleTapDragStartDistance: Float = 0
        private var lastGlobeStyle: GlobeStyle?

        // Interactive spin state, applied on the render thread (renderer(_:updateAtTime:))
        // and fed from main-thread gesture handlers — guarded by motionLock.
        let motionLock = NSLock()
        let inertia = GlobeInertia()
        var pendingDragX: Float = 0
        var pendingDragY: Float = 0
        var lastRenderTime: TimeInterval = 0

        init(globeState: GlobeState, onCountryTapped: ((String) -> Void)? = nil) {
            self.globeState = globeState
            self.onCountryTapped = onCountryTapped
            super.init()
            // Cache countries data once
            self.cachedCountries = CountryDataCache.shared.countries
        }

        deinit {
            stopInertia()
        }

        func zoomIn() {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }
            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.3
            cameraNode.position.z = max(GlobeState.minCameraDistance, cameraNode.position.z - 0.5)
            updateZoomDependentSizing(cameraDistance: Float(cameraNode.position.z))
            SCNTransaction.commit()
        }

        func zoomOut() {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }
            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.3
            cameraNode.position.z = min(10.0, cameraNode.position.z + 0.5)
            updateZoomDependentSizing(cameraDistance: Float(cameraNode.position.z))
            SCNTransaction.commit()
        }

        func updateZoom() {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }
            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.3
            let cameraDistance = Float(globeState.zoomLevel)
            cameraNode.position = SCNVector3(
                0,
                cameraDistance * sin(currentRotationX),
                cameraDistance * cos(currentRotationX)
            )
            aimCameraAtGlobeCenter(cameraNode)
            updateZoomDependentSizing(cameraDistance: cameraDistance)
            SCNTransaction.commit()
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
            let minScale = Self.screenScale(cameraDistance: GlobeState.minCameraDistance)
            let scale = max(minScale, min(1.0, Self.screenScale(cameraDistance: cameraDistance)))

            // Cheap and needed on every call: the marker node is rebuilt whenever the
            // selection changes and has to pick up the current zoom scale.
            currentZoomScale = scale
            let markerScale = Self.capitalMarkerScale(zoomScale: scale)
            capitalStarNode?.scale = SCNVector3(markerScale, markerScale, markerScale)

            guard abs(scale - currentOutlineScale) > 0.005 else { return }
            currentOutlineScale = scale

            sharedOutlineMaterial?.setValue(Self.baseOutlineThickness * scale, forKey: "outlineThickness")
            if let selectedGeometry = selectedOutlineNode?.geometry {
                for material in selectedGeometry.materials {
                    material.setValue(Self.baseOutlineThickness * scale * Self.selectedOutlineFactor, forKey: "outlineThickness")
                }
            }
        }

        func updateAutoRotation() {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true) else { return }

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

            flyTo(lat: center.lat, lon: center.lon, distance: 2.8)
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

        /// Animates the globe rotation and camera so the given lat/lon is centered
        /// at the given camera distance (nil keeps the current distance).
        func flyTo(lat: Double, lon: Double, distance: Float?) {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

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

            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.8
            SCNTransaction.animationTimingFunction = CAMediaTimingFunction(name: .easeInEaseOut)

            // Rotate globe to center the location horizontally
            currentRotationY = targetRotationY
            globeNode.eulerAngles = SCNVector3(0, currentRotationY, 0)

            // Move camera to appropriate latitude
            // Positive lat (North) = camera moves up to look down at it
            let targetCameraX = Float(lat) * .pi / 180.0
            currentRotationX = max(-.pi / 2.5, min(.pi / 2.5, targetCameraX))

            cameraNode.position = SCNVector3(
                0,
                distance * sin(currentRotationX),
                distance * cos(currentRotationX)
            )
            aimCameraAtGlobeCenter(cameraNode)
            updateZoomDependentSizing(cameraDistance: distance)

            SCNTransaction.commit()
        }

        func getCountryCenter(name: String) -> (lat: Double, lon: Double)? {
            CountryHitTester.shared.center(of: name)
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

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

        private func stopInertia() {
            motionLock.lock()
            inertia.reset()
            pendingDragX = 0
            pendingDragY = 0
            motionLock.unlock()
        }

        @objc func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

            if gesture.state == .began {
                // Sync rotation state with actual visual position before stopping auto-rotation
                let currentActualRotationY = globeNode.presentation.eulerAngles.y
                currentRotationY = currentActualRotationY
                globeNode.eulerAngles.y = currentActualRotationY
                globeNode.removeAction(forKey: "autoRotation")
                globeState.isAutoRotating = false
            }

            if gesture.state == .changed {
                let currentDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                          cameraNode.position.y * cameraNode.position.y +
                                          cameraNode.position.z * cameraNode.position.z)
                // Scale zoom speed proportionally to distance so it slows down when zoomed in
                let zoomSpeed: Float = currentDistance * 0.4    // After some manual testing, this felt like a good pinch-zoom sensitivity
                var newDistance = currentDistance - Float(gesture.scale - 1) * zoomSpeed

                // Clamp zoom level
                newDistance = max(GlobeState.minCameraDistance, min(8.0, newDistance))

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
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

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

            case .changed:
                let deltaY = location.y - doubleTapDragStartY
                // Drag down = zoom in (negative distance), drag up = zoom out
                let zoomSpeed: Float = 0.01
                var newDistance = doubleTapDragStartDistance + Float(deltaY) * zoomSpeed

                // Clamp zoom level
                newDistance = max(GlobeState.minCameraDistance, min(8.0, newDistance))

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
            guard let (lat, lon) = Self.surfaceLatLon(at: location, in: sceneView, globeNode: globeNode) else { return }

            // Find which country contains this point
            if let countryName = findCountryAt(lat: lat, lon: lon) {
                if let onCountryTapped = onCountryTapped {
                    onCountryTapped(countryName)
                } else {
                    let center = getCountryCenter(name: countryName)
                    self.globeState.selectCountry(countryName, center: center)
                    self.updateHighlights()
                }
            }
        }

        /// Exact tap → globe-surface lat/lon: unprojects the screen point into a
        /// world-space ray and intersects it with the globe sphere analytically.
        ///
        /// A SceneKit hitTest is deliberately not used here — its nearest hit is
        /// almost always the atmosphere shell (radius 1.08), and normalizing that
        /// hit point misreads oblique taps: near the screen edge at close zoom the
        /// error reaches several degrees, enough to land in a neighboring country.
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

        func findCountryAt(lat: Double, lon: Double) -> String? {
            CountryHitTester.shared.findCountry(lat: lat, lon: lon)
        }

        func updateGlobeStyle() {
            guard globeState.globeStyle != lastGlobeStyle else { return }
            lastGlobeStyle = globeState.globeStyle

            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let oceanNode = globeNode.childNode(withName: "ocean", recursively: false),
                  let oceanGeometry = oceanNode.geometry,
                  let oceanMaterial = oceanGeometry.materials.first else { return }

            if let texture = UIImage(named: globeState.globeStyle.textureName) {
                oceanMaterial.diffuse.contents = texture
                // Shift texture to align with country polygon coordinate system
                // latLonToSphere maps lon=0 to +X axis, but SCNSphere UV has U=0.5 offset
                oceanMaterial.diffuse.contentsTransform = SCNMatrix4MakeTranslation(-0.25, 0, 0)
                oceanMaterial.diffuse.wrapS = .repeat
            }
        }

        func updateHighlights() {
            for (name, node) in countryNodes {
                guard let geometry = node.geometry else { continue }

                let isVisited = globeState.visitedCountries.contains(name)
                let isWishlist = globeState.wishlistCountries.contains(name)
                let highlightOverride = globeState.countryHighlightColors[name]
                let hasStatus = isVisited || isWishlist || highlightOverride != nil
                let isSelected = globeState.selectedCountry == name
                let isPointCountry = geometry is SCNCylinder

                // Overlay: visible only when has status AND not selected
                if hasStatus && !isSelected {
                    node.isHidden = false
                    for material in geometry.materials {
                        material.transparency = 1.0
                        if let highlightOverride = highlightOverride {
                            material.lightingModel = .blinn
                            material.diffuse.contents = highlightOverride
                            material.emission.contents = highlightOverride.withAlphaComponent(0.15)
                        } else if isVisited && isWishlist {
                            material.lightingModel = .constant
                            material.diffuse.contents = AppColors.visitedWishlistGradient
                            material.emission.contents = UIColor.black
                        } else if isVisited {
                            material.lightingModel = .blinn
                            material.diffuse.contents = AppColors.visitedUI
                            material.emission.contents = AppColors.visitedUI.withAlphaComponent(0.15)
                        } else {
                            material.lightingModel = .blinn
                            material.diffuse.contents = AppColors.wishlistUI
                            material.emission.contents = AppColors.wishlistUI.withAlphaComponent(0.15)
                        }
                    }
                } else {
                    node.isHidden = true
                }

                // Point countries have their own outline node (dot ring/disc); polygon
                // countries share one merged outline node, with the selected country's
                // outline drawn by a separate overlay node (see updateSelectedOutline)
                if isPointCountry,
                   let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                   let outlineNode = globeNode.childNode(withName: "\(name)_outline", recursively: false) {
                    outlineNode.isHidden = false

                    // Swap outline geometry based on status and selection
                    if isSelected || !hasStatus {
                        // Ring (border only) — fill is hidden
                        let outerRadius: CGFloat = isSelected ? 0.0155 : 0.014
                        let ring = SCNTube(innerRadius: 0.012, outerRadius: outerRadius, height: 0.0005)
                        let material = SCNMaterial()
                        material.lightingModel = .constant
                        material.isDoubleSided = true
                        ring.materials = [material]
                        outlineNode.geometry = ring
                    } else {
                        // Disc (behind visible fill overlay)
                        let disc = SCNCylinder(radius: 0.014, height: 0.0005)
                        let material = SCNMaterial()
                        material.lightingModel = .constant
                        material.isDoubleSided = true
                        disc.materials = [material]
                        outlineNode.geometry = disc
                    }

                    // Border color: status color when selected, black otherwise
                    if let outlineGeometry = outlineNode.geometry {
                        for material in outlineGeometry.materials {
                            material.diffuse.contents = borderColor(isSelected: isSelected, isVisited: isVisited, isWishlist: isWishlist, highlightOverride: highlightOverride)
                        }
                    }
                }
            }

            updateSelectedOutline()

            // Update capital star
            updateCapitalStar()
        }

        private func borderColor(isSelected: Bool, isVisited: Bool, isWishlist: Bool, highlightOverride: UIColor? = nil) -> Any {
            if isSelected {
                if let highlightOverride = highlightOverride {
                    return highlightOverride
                }
                if isVisited && isWishlist {
                    return AppColors.visitedWishlistGradient
                } else if isVisited {
                    return AppColors.visitedUI
                } else if isWishlist {
                    return AppColors.wishlistUI
                }
            }
            return UIColor.black
        }

        /// Shows the selected polygon country's outline as an overlay node: thicker,
        /// status-colored, and raised above the merged black outlines.
        private func updateSelectedOutline() {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true) else { return }

            guard let name = globeState.selectedCountry,
                  let country = cachedCountries.first(where: { $0.name == name }),
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

            let isVisited = globeState.visitedCountries.contains(name)
            let isWishlist = globeState.wishlistCountries.contains(name)
            for material in geometry.materials {
                material.diffuse.contents = borderColor(isSelected: true, isVisited: isVisited, isWishlist: isWishlist, highlightOverride: globeState.countryHighlightColors[name])
                material.setValue(Self.baseOutlineThickness * currentOutlineScale * Self.selectedOutlineFactor, forKey: "outlineThickness")
                material.setValue(Self.selectedOutlineRaise, forKey: "outlineRaise")
            }
        }

        func updateCapitalStar() {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true) else { return }

            // Remove existing star
            capitalStarNode?.removeFromParentNode()
            capitalStarNode = nil

            guard globeState.showsCapitalMarker else { return }

            // If a country is selected, show star at its capital
            guard let selectedCountry = globeState.selectedCountry,
                  let country = cachedCountries.first(where: { $0.name == selectedCountry }),
                  let capital = country.capital else { return }

            // Convert lat/lon to 3D position on sphere (radius slightly above surface)
            // Must match PolygonTriangulator.latLonToSphere coordinate system
            // Country polygons are at 1.003, borders at 1.005, so marker at 1.007
            let radius: Float = 1.007  // Just above borders
            let latRad = Float(capital.lat) * .pi / 180.0
            let lonRad = Float(-capital.lon) * .pi / 180.0  // Negative lon to match globe

            // Convert spherical to cartesian coordinates (matching globe's coordinate system)
            let x = radius * cos(latRad) * cos(lonRad)
            let y = radius * sin(latRad)
            let z = radius * cos(latRad) * sin(lonRad)

            // The marker pulses via an SCNAction on its own scale, so the zoom scale
            // rides on a container instead — otherwise the running action overwrites
            // it every frame and the marker balloons at close zoom.
            let holder = SCNNode()
            holder.position = SCNVector3(x, y, z)
            let markerScale = Self.capitalMarkerScale(zoomScale: currentZoomScale)
            holder.scale = SCNVector3(markerScale, markerScale, markerScale)
            holder.addChildNode(createStarNode())

            // Orient star to face outward from globe center using constraints
            let billboardConstraint = SCNBillboardConstraint()
            billboardConstraint.freeAxes = .all
            holder.constraints = [billboardConstraint]

            globeNode.addChildNode(holder)
            capitalStarNode = holder
        }

        func createStarNode() -> SCNNode {
            // Five-pointed star, the printed-map convention for a capital. The
            // holder billboards it, so only the front face is ever seen; the
            // extrusion just gives the shape a body to shade.
            //
            // Built at unit radius and scaled down by the node rather than drawn at
            // its final 0.008: SCNShape tessellates against the path's flatness, and
            // a path smaller than the flatness value yields empty geometry.
            let starPath = UIBezierPath(cgPath: CapitalMarker.starPath(outerRadius: 1, yUp: true))
            starPath.flatness = 0.01
            let star = SCNShape(path: starPath, extrusionDepth: 0.05)

            // `.constant` already ignores scene lighting, so the star keeps the exact
            // theme orange on the night side without an emission wash over it.
            let material = SCNMaterial()
            material.diffuse.contents = AppColors.capitalMarkerUI
            material.lightingModel = .constant
            star.materials = [material]

            let starNode = SCNNode(geometry: star)
            starNode.name = "capitalMarker"
            let radius = Self.capitalMarkerRadius
            starNode.scale = SCNVector3(radius, radius, radius)

            // The pulse rides on a parent so it doesn't overwrite the size scale.
            let pulseNode = SCNNode()
            pulseNode.addChildNode(starNode)

            let scaleUp = SCNAction.scale(to: 1.5, duration: 0.6)
            scaleUp.timingMode = .easeInEaseOut
            let scaleDown = SCNAction.scale(to: 1.0, duration: 0.6)
            scaleDown.timingMode = .easeInEaseOut
            let pulse = SCNAction.sequence([scaleUp, scaleDown])
            pulseNode.runAction(SCNAction.repeatForever(pulse))

            return pulseNode
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
        motionLock.unlock()

        if dx != 0 || dy != 0,
           let globeNode = globeNode,
           let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: false) {
            currentRotationY += dy
            currentRotationX = max(-.pi / 2.5, min(.pi / 2.5, currentRotationX + dx))

            globeNode.eulerAngles = SCNVector3(0, currentRotationY, 0)

            let position = cameraNode.position
            let distance = sqrt(position.x * position.x + position.y * position.y + position.z * position.z)
            cameraNode.position = SCNVector3(0, distance * sin(currentRotationX), distance * cos(currentRotationX))
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
    ///
    /// A point p on the unit sphere is visible from a camera at distance d iff
    /// dot(p, camDir) >= 1/d (the horizon). For a sector with bounding sphere
    /// (center c, radius r), dot(p, camDir) <= dot(c, camDir) + r for all its points,
    /// so the sector is safely hidden when dot(c, camDir) + r < 1/d - margin.
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
        let horizon = 1.0 / distance - 0.02 // small margin against popping at the limb

        for sector in outlineSectors {
            let hidden = simd_dot(sector.center, cameraDirection) + sector.radius < horizon
            if sector.node.isHidden != hidden {
                sector.node.isHidden = hidden
            }
        }
    }
}
