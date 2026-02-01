import SwiftUI
import SceneKit
import UIKit

struct GlobeView: UIViewRepresentable {
    @ObservedObject var globeState: GlobeState

    func makeUIView(context: Context) -> SCNView {
        let sceneView = SCNView()
        sceneView.scene = GlobeScene.createScene(globeState: globeState, coordinator: context.coordinator)
        sceneView.backgroundColor = .clear
        sceneView.allowsCameraControl = false
        sceneView.antialiasingMode = .multisampling4X
        sceneView.autoenablesDefaultLighting = false

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
        context.coordinator.updateHighlights()
        context.coordinator.updateAutoRotation()
        context.coordinator.centerOnSelectedCountry()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(globeState: globeState)
    }

    class Coordinator: NSObject {
        var globeState: GlobeState
        weak var sceneView: SCNView?
        var globeNode: SCNNode?
        var countryNodes: [String: SCNNode] = [:]
        var originalColors: [String: UIColor] = [:]
        var cachedCountries: [GeoJSONCountry] = []

        private var lastPanLocation: CGPoint = .zero
        private var currentRotationX: Float = 0
        private var currentRotationY: Float = 0
        private var currentScale: Float = 1.0
        private var lastAutoRotatingState: Bool = true
        private var hasAnimatedToCountry: Bool = false
        private var lastAnimatedCountry: String?
        private var capitalStarNode: SCNNode?
        private var doubleTapDragStartY: CGFloat = 0
        private var doubleTapDragStartDistance: Float = 0

        init(globeState: GlobeState) {
            self.globeState = globeState
            super.init()
            // Cache countries data once
            self.cachedCountries = GeoJSONParser.loadCountries()
        }

        func zoomIn() {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }
            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.3
            cameraNode.position.z = max(1.2, cameraNode.position.z - 0.5)
            SCNTransaction.commit()
        }

        func zoomOut() {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }
            SCNTransaction.begin()
            SCNTransaction.animationDuration = 0.3
            cameraNode.position.z = min(10.0, cameraNode.position.z + 0.5)
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
            cameraNode.look(at: SCNVector3(0, 0, 0))
            SCNTransaction.commit()
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
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true),
                  let center = globeState.targetCountryCenter,
                  let selectedCountry = globeState.selectedCountry else { return }

            // Check if this is a new country selection
            if selectedCountry != lastAnimatedCountry {
                hasAnimatedToCountry = false
            }

            guard !hasAnimatedToCountry else { return }

            hasAnimatedToCountry = true
            lastAnimatedCountry = selectedCountry

            // Capture the current actual rotation from the presentation node
            let currentActualRotationY = globeNode.presentation.eulerAngles.y

            // Immediately set the model node to match the presentation (freeze current position)
            globeNode.eulerAngles.y = currentActualRotationY

            // Convert longitude to globe Y rotation
            // Camera is at (0,0,z) looking at origin, so country needs to be on +Z side
            // lon=0 is at +X, lon=90 is at -Z, lon=-90 is at +Z
            // To center lon L: rotate by -(L + 90) degrees
            var targetRotationY = Float(-center.lon - 90) * .pi / 180.0

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

            // Rotate globe to center the country horizontally
            currentRotationY = targetRotationY
            globeNode.eulerAngles = SCNVector3(0, currentRotationY, 0)

            // Move camera to appropriate latitude
            // Positive lat (North) = camera moves up to look down at it
            let targetCameraX = Float(center.lat) * .pi / 180.0
            currentRotationX = max(-.pi / 2.5, min(.pi / 2.5, targetCameraX))

            // Zoom in closer
            let zoomDistance: Float = 2.8
            cameraNode.position = SCNVector3(
                0,
                zoomDistance * sin(currentRotationX),
                zoomDistance * cos(currentRotationX)
            )
            cameraNode.look(at: SCNVector3(0, 0, 0))

            SCNTransaction.commit()
        }

        func getCountryCenter(name: String) -> (lat: Double, lon: Double)? {
            guard let country = cachedCountries.first(where: { $0.name == name }) else { return nil }

            // Point countries have their center stored directly
            if country.isPointCountry, let coord = country.pointCoordinate {
                return coord
            }

            // Calculate center for polygon countries
            var totalLat = 0.0
            var totalLon = 0.0
            var count = 0

            for polygon in country.polygons {
                for coord in polygon {
                    if coord.count >= 2 {
                        totalLon += coord[0]
                        totalLat += coord[1]
                        count += 1
                    }
                }
            }

            guard count > 0 else { return nil }
            return (lat: totalLat / Double(count), lon: totalLon / Double(count))
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                  let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

            // Stop auto-rotation when user drags
            if gesture.state == .began {
                // Sync rotation state with actual visual position before stopping auto-rotation
                let currentActualRotationY = globeNode.presentation.eulerAngles.y
                currentRotationY = currentActualRotationY
                globeNode.eulerAngles.y = currentActualRotationY
                globeNode.removeAction(forKey: "autoRotation")
                globeState.isAutoRotating = false
            }

            let translation = gesture.translation(in: sceneView)

            // Scale rotation speed based on camera distance (zoom level)
            // When zoomed in (closer), use slower rotation for finer control
            let cameraDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                      cameraNode.position.y * cameraNode.position.y +
                                      cameraNode.position.z * cameraNode.position.z)
            let baseRotationSpeed: Float = 0.005
            let referenceDistance: Float = 4.0  // Default camera distance
            let distanceRatio = cameraDistance / referenceDistance
            // Use squared ratio for more aggressive scaling when zoomed in
            let rotationSpeed = baseRotationSpeed * distanceRatio * distanceRatio

            // Horizontal drag: rotate globe around Y-axis
            currentRotationY += Float(translation.x) * rotationSpeed
            globeNode.eulerAngles = SCNVector3(0, currentRotationY, 0)

            // Vertical drag: orbit camera up/down while looking at globe
            currentRotationX += Float(translation.y) * rotationSpeed
            currentRotationX = max(-.pi / 2.5, min(.pi / 2.5, currentRotationX))

            // Keep camera at current distance from globe center
            cameraNode.position = SCNVector3(
                0,
                cameraDistance * sin(currentRotationX),
                cameraDistance * cos(currentRotationX)
            )
            cameraNode.look(at: SCNVector3(0, 0, 0))

            gesture.setTranslation(.zero, in: sceneView)
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
                let zoomSpeed: Float = 0.5
                let currentDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                          cameraNode.position.y * cameraNode.position.y +
                                          cameraNode.position.z * cameraNode.position.z)
                var newDistance = currentDistance - Float(gesture.scale - 1) * zoomSpeed

                // Clamp zoom level
                newDistance = max(1.2, min(8.0, newDistance))

                cameraNode.position = SCNVector3(
                    0,
                    newDistance * sin(currentRotationX),
                    newDistance * cos(currentRotationX)
                )
                cameraNode.look(at: SCNVector3(0, 0, 0))

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
                newDistance = max(1.2, min(8.0, newDistance))

                cameraNode.position = SCNVector3(
                    0,
                    newDistance * sin(currentRotationX),
                    newDistance * cos(currentRotationX)
                )
                cameraNode.look(at: SCNVector3(0, 0, 0))

            default:
                break
            }
        }

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let sceneView = sceneView,
                  let globeNode = globeNode else { return }

            let location = gesture.location(in: sceneView)
            let hitResults = sceneView.hitTest(location, options: nil)

            // Accept any hit that's part of the globe (not camera or lights)
            let excludedNames: Set<String> = ["camera", "light", "ambientLight"]

            for hit in hitResults {
                let nodeName = hit.node.name ?? ""
                if excludedNames.contains(nodeName) { continue }

                // Convert world hit point to globe's local coordinate system
                let worldPoint = hit.worldCoordinates
                let localPoint = globeNode.convertPosition(worldPoint, from: nil)

                // Normalize to unit sphere
                let length = sqrt(localPoint.x * localPoint.x + localPoint.y * localPoint.y + localPoint.z * localPoint.z)
                guard length > 0 else { continue }

                let nx = localPoint.x / length
                let ny = localPoint.y / length
                let nz = localPoint.z / length

                // Convert 3D point to lat/lon
                let lat = Double(asin(ny)) * 180.0 / .pi
                let lon = -Double(atan2(nz, nx)) * 180.0 / .pi

                // Find which country contains this point
                if let countryName = findCountryAt(lat: lat, lon: lon) {
                    let center = getCountryCenter(name: countryName)
                    self.globeState.selectCountry(countryName, center: center)
                    self.updateHighlights()
                }
                return
            }
        }

        func findCountryAt(lat: Double, lon: Double) -> String? {
            // First check point countries (small island nations and microstates)
            let pointHitRadius: Double = 0.8
            for country in cachedCountries where country.isPointCountry {
                guard let coord = country.pointCoordinate else { continue }
                let distance = sqrt(pow(lat - coord.lat, 2) + pow(lon - coord.lon, 2))
                if distance < pointHitRadius {
                    return country.name
                }
            }

            // Then try exact location for polygon countries
            if let country = findCountryAtExact(lat: lat, lon: lon) {
                return country
            }

            // If not found, search in expanding radius for small countries
            let searchRadii: [Double] = [0.5, 1.0, 2.0, 3.0]
            let pointsPerRadius = 8

            for radius in searchRadii {
                for i in 0..<pointsPerRadius {
                    let angle = Double(i) * (2.0 * .pi / Double(pointsPerRadius))
                    let searchLat = lat + radius * sin(angle)
                    let searchLon = lon + radius * cos(angle)

                    if let country = findCountryAtExact(lat: searchLat, lon: searchLon) {
                        return country
                    }
                }
            }

            return nil
        }

        func findCountryAtExact(lat: Double, lon: Double) -> String? {
            for country in cachedCountries {
                for polygon in country.polygons {
                    if isPointInPolygon(lon: lon, lat: lat, polygon: polygon) {
                        return country.name
                    }
                }
            }
            return nil
        }

        // Ray casting algorithm for point-in-polygon test
        func isPointInPolygon(lon: Double, lat: Double, polygon: [[Double]]) -> Bool {
            var inside = false
            var j = polygon.count - 1

            for i in 0..<polygon.count {
                guard polygon[i].count >= 2 && polygon[j].count >= 2 else {
                    j = i
                    continue
                }
                let xi = polygon[i][0], yi = polygon[i][1]
                let xj = polygon[j][0], yj = polygon[j][1]

                if ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
                j = i
            }

            return inside
        }

        func updateHighlights() {
            for (name, node) in countryNodes {
                guard let geometry = node.geometry else { continue }

                let isCurrentlySelected = globeState.selectedCountry == name
                let isVisited = globeState.visitedCountries.contains(name)
                let isWishlist = globeState.wishlistCountries.contains(name)

                SCNTransaction.begin()
                SCNTransaction.animationDuration = 0.3

                // Update all materials (needed for cylinders which have multiple materials for sides/caps)
                for material in geometry.materials {
                    if isCurrentlySelected {
                        // Orange #D98C59 (matches button color)
                        material.diffuse.contents = UIColor(red: 0.85, green: 0.55, blue: 0.35, alpha: 1.0)
                        material.emission.contents = UIColor(red: 0.85, green: 0.55, blue: 0.35, alpha: 0.3)
                    } else if isVisited && isWishlist {
                        // Yellow fill for visited + wishlist (outline will be purple)
                        material.diffuse.contents = UIColor(red: 0.949, green: 0.941, blue: 0.075, alpha: 1.0)
                        material.emission.contents = UIColor(red: 0.949, green: 0.941, blue: 0.075, alpha: 0.15)
                    } else if isVisited {
                        // Light yellow #F2F013
                        material.diffuse.contents = UIColor(red: 0.949, green: 0.941, blue: 0.075, alpha: 1.0)
                        material.emission.contents = UIColor(red: 0.949, green: 0.941, blue: 0.075, alpha: 0.15)
                    } else if isWishlist {
                        // Purple for wishlist
                        material.diffuse.contents = UIColor(red: 0.6, green: 0.4, blue: 0.8, alpha: 1.0)
                        material.emission.contents = UIColor(red: 0.6, green: 0.4, blue: 0.8, alpha: 0.15)
                    } else {
                        // Green for unvisited countries
                        if let originalColor = originalColors[name] {
                            material.diffuse.contents = originalColor
                        }
                        material.emission.contents = UIColor.black
                    }
                }

                // Update outline color for visited+wishlist countries
                if let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true),
                   let outlineNode = globeNode.childNode(withName: "\(name)_outline", recursively: false),
                   let outlineGeometry = outlineNode.geometry {
                    for material in outlineGeometry.materials {
                        if isVisited && isWishlist {
                            // Purple outline for visited+wishlist
                            material.diffuse.contents = UIColor(red: 0.6, green: 0.4, blue: 0.8, alpha: 1.0)
                        } else {
                            // Black outline for all other states
                            material.diffuse.contents = UIColor.black
                        }
                    }
                }

                SCNTransaction.commit()
            }

            // Update capital star
            updateCapitalStar()
        }

        func updateCapitalStar() {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true) else { return }

            // Remove existing star
            capitalStarNode?.removeFromParentNode()
            capitalStarNode = nil

            // If a country is selected, show star at its capital
            guard let selectedCountry = globeState.selectedCountry,
                  let capital = CapitalData.getCapital(for: selectedCountry) else { return }

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

            // Create star geometry
            let starNode = createStarNode()
            starNode.position = SCNVector3(x, y, z)

            // Orient star to face outward from globe center using constraints
            let billboardConstraint = SCNBillboardConstraint()
            billboardConstraint.freeAxes = .all
            starNode.constraints = [billboardConstraint]

            globeNode.addChildNode(starNode)
            capitalStarNode = starNode
        }

        func createStarNode() -> SCNNode {
            // Small black dot for capital
            let sphere = SCNSphere(radius: 0.006)

            let material = SCNMaterial()
            material.diffuse.contents = UIColor.black
            material.emission.contents = UIColor(white: 0.2, alpha: 0.8)
            material.lightingModel = .constant
            sphere.materials = [material]

            let node = SCNNode(geometry: sphere)
            node.name = "capitalMarker"

            // Add pulsating animation
            let scaleUp = SCNAction.scale(to: 1.5, duration: 0.6)
            scaleUp.timingMode = .easeInEaseOut
            let scaleDown = SCNAction.scale(to: 1.0, duration: 0.6)
            scaleDown.timingMode = .easeInEaseOut
            let pulse = SCNAction.sequence([scaleUp, scaleDown])
            node.runAction(SCNAction.repeatForever(pulse))

            return node
        }
    }
}
