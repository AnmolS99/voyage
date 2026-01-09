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

        context.coordinator.sceneView = sceneView

        return sceneView
    }

    func updateUIView(_ uiView: SCNView, context: Context) {
        context.coordinator.updateHighlights()
        context.coordinator.updateZoom()
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
            cameraNode.position.z = max(1.5, cameraNode.position.z - 0.5)
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
                    // Resume auto-rotation
                    let rotation = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 60))
                    globeNode.runAction(rotation, forKey: "autoRotation")
                    hasAnimatedToCountry = false
                } else {
                    // Stop auto-rotation
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

            // Convert longitude to globe Y rotation
            // Camera is at (0,0,z) looking at origin, so country needs to be on +Z side
            // lon=0 is at +X, lon=90 is at -Z, lon=-90 is at +Z
            // To center lon L: rotate by -(L + 90) degrees
            let targetRotationY = Float(-center.lon - 90) * .pi / 180.0

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

            let translation = gesture.translation(in: sceneView)

            let rotationSpeed: Float = 0.005

            // Horizontal drag: rotate globe around Y-axis
            currentRotationY += Float(translation.x) * rotationSpeed
            globeNode.eulerAngles = SCNVector3(0, currentRotationY, 0)

            // Vertical drag: orbit camera up/down while looking at globe
            currentRotationX += Float(translation.y) * rotationSpeed
            currentRotationX = max(-.pi / 2.5, min(.pi / 2.5, currentRotationX))

            // Keep camera at fixed distance from globe center
            let cameraDistance = Float(globeState.zoomLevel)
            cameraNode.position = SCNVector3(
                0,
                cameraDistance * sin(currentRotationX),
                cameraDistance * cos(currentRotationX)
            )
            cameraNode.look(at: SCNVector3(0, 0, 0))

            gesture.setTranslation(.zero, in: sceneView)
        }

        @objc func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

            if gesture.state == .changed {
                let zoomSpeed: Float = 0.5
                let currentDistance = sqrt(cameraNode.position.x * cameraNode.position.x +
                                          cameraNode.position.y * cameraNode.position.y +
                                          cameraNode.position.z * cameraNode.position.z)
                var newDistance = currentDistance - Float(gesture.scale - 1) * zoomSpeed

                // Clamp zoom level
                newDistance = max(1.5, min(8.0, newDistance))

                cameraNode.position = SCNVector3(
                    0,
                    newDistance * sin(currentRotationX),
                    newDistance * cos(currentRotationX)
                )
                cameraNode.look(at: SCNVector3(0, 0, 0))

                gesture.scale = 1
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
            // First try exact location
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
                guard let geometry = node.geometry,
                      let material = geometry.firstMaterial else { continue }

                let isCurrentlySelected = globeState.selectedCountry == name
                let isVisited = globeState.visitedCountries.contains(name)

                SCNTransaction.begin()
                SCNTransaction.animationDuration = 0.3

                if isCurrentlySelected {
                    // Lime/light yellow-green for currently focused country
                    material.diffuse.contents = UIColor(red: 0.7, green: 0.9, blue: 0.4, alpha: 1.0)
                    material.emission.contents = UIColor(red: 0.7, green: 0.9, blue: 0.4, alpha: 0.3)
                } else if isVisited {
                    // Yellow for visited countries
                    material.diffuse.contents = UIColor(red: 1.0, green: 0.85, blue: 0.2, alpha: 1.0)
                    material.emission.contents = UIColor(red: 1.0, green: 0.85, blue: 0.2, alpha: 0.15)
                } else {
                    // Green for unvisited countries
                    if let originalColor = originalColors[name] {
                        material.diffuse.contents = originalColor
                    }
                    material.emission.contents = UIColor.black
                }

                SCNTransaction.commit()
            }
        }
    }
}
