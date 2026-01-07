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
            cameraNode.position.z = max(2.0, cameraNode.position.z - 0.5)
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
            cameraNode.position.z = Float(globeState.zoomLevel)
            SCNTransaction.commit()
        }

        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let globeNode = sceneView?.scene?.rootNode.childNode(withName: "globe", recursively: true) else { return }

            let translation = gesture.translation(in: sceneView)

            let rotationSpeed: Float = 0.005

            currentRotationY += Float(translation.x) * rotationSpeed
            currentRotationX += Float(translation.y) * rotationSpeed

            // Clamp vertical rotation
            currentRotationX = max(-.pi / 2, min(.pi / 2, currentRotationX))

            globeNode.eulerAngles = SCNVector3(currentRotationX, currentRotationY, 0)

            gesture.setTranslation(.zero, in: sceneView)
        }

        @objc func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard let cameraNode = sceneView?.scene?.rootNode.childNode(withName: "camera", recursively: true) else { return }

            if gesture.state == .changed {
                let zoomSpeed: Float = 0.5
                var newZ = cameraNode.position.z - Float(gesture.scale - 1) * zoomSpeed

                // Clamp zoom level
                newZ = max(2.5, min(8.0, newZ))
                cameraNode.position.z = newZ

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
                    self.globeState.selectCountry(countryName)
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

                let isSelected = globeState.selectedCountries.contains(name)

                SCNTransaction.begin()
                SCNTransaction.animationDuration = 0.3

                if isSelected {
                    material.diffuse.contents = UIColor(red: 1.0, green: 0.85, blue: 0.2, alpha: 1.0)
                    material.emission.contents = UIColor(red: 1.0, green: 0.85, blue: 0.2, alpha: 0.3)
                } else {
                    // Reset to original color
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
