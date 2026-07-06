import SceneKit
import SwiftUI
import UIKit

class GlobeScene {

    // MARK: - Border outline materials

    /// Material for border outlines: width is applied by the geometry shader modifier
    /// (see PolygonTriangulator.outlineShaderModifier) from the `outlineThickness` uniform,
    /// which zoom code scales so borders keep a constant on-screen width. Each country
    /// gets its own instance because selection recolors and thickens outlines per country.
    static func makeOutlineMaterial() -> SCNMaterial {
        let material = SCNMaterial()
        material.diffuse.contents = UIColor.black
        material.lightingModel = .constant // Make it always visible
        material.isDoubleSided = true
        material.shaderModifiers = [.geometry: PolygonTriangulator.outlineShaderModifier]
        material.setValue(GlobeView.Coordinator.baseOutlineThickness, forKey: "outlineThickness")
        material.setValue(Float(0), forKey: "outlineRaise")
        return material
    }

    static func createScene(globeState: GlobeState, coordinator: GlobeView.Coordinator) -> SCNScene {
        let scene = SCNScene()
        scene.background.contents = UIColor.clear

        // Load pre-built globe from bundle
        if let bundledGlobe = GlobeCache.shared.loadBundledGlobe() {
            scene.rootNode.addChildNode(bundledGlobe)
            coordinator.globeNode = bundledGlobe

            // Rebuild the countryNodes and originalColors dictionaries from cached nodes
            rebuildCoordinatorData(from: bundledGlobe, coordinator: coordinator)

            // Start facing Europe/Africa (~15°E longitude)
            bundledGlobe.eulerAngles.y = -.pi / 2 - .pi / 12

            // Add rotation animation (not saved in bundle)
            let rotation = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 60))
            bundledGlobe.runAction(rotation, forKey: "autoRotation")
        } else {
            // Fallback: Generate globe from scratch (should not happen in production)
            let globeNode = createGlobeNode(coordinator: coordinator)
            scene.rootNode.addChildNode(globeNode)
            coordinator.globeNode = globeNode
        }

        // Camera
        let cameraNode = SCNNode()
        cameraNode.name = "camera"
        cameraNode.camera = SCNCamera()
        cameraNode.camera?.fieldOfView = 45
        cameraNode.camera?.zNear = 0.1
        cameraNode.camera?.zFar = 100
        cameraNode.position = SCNVector3(0, 0, 4)
        scene.rootNode.addChildNode(cameraNode)

        // Main light (sun-like)
        let lightNode = SCNNode()
        lightNode.name = "light"
        lightNode.light = SCNLight()
        lightNode.light?.type = .directional
        lightNode.light?.color = UIColor(white: 1.0, alpha: 1.0)
        lightNode.light?.intensity = 800
        lightNode.position = SCNVector3(5, 5, 5)
        lightNode.look(at: SCNVector3(0, 0, 0))
        scene.rootNode.addChildNode(lightNode)

        // Ambient light for fill
        let ambientLightNode = SCNNode()
        ambientLightNode.name = "ambientLight"
        ambientLightNode.light = SCNLight()
        ambientLightNode.light?.type = .ambient
        ambientLightNode.light?.color = UIColor(white: 0.4, alpha: 1.0)
        ambientLightNode.light?.intensity = 400
        scene.rootNode.addChildNode(ambientLightNode)

        return scene
    }

    private static func createGlobeNode(coordinator: GlobeView.Coordinator) -> SCNNode {
        let globeNode = SCNNode()
        globeNode.name = "globe"

        // Create ocean sphere (base)
        let oceanSphere = SCNSphere(radius: 1.0)
        oceanSphere.segmentCount = 64
        let oceanMaterial = SCNMaterial()
        oceanMaterial.diffuse.contents = AppColors.oceanUI
        oceanMaterial.specular.contents = UIColor.clear
        oceanMaterial.shininess = 0.3
        oceanSphere.materials = [oceanMaterial]

        let oceanNode = SCNNode(geometry: oceanSphere)
        oceanNode.name = "ocean"
        globeNode.addChildNode(oceanNode)

        // Create atmosphere glow
        let atmosphereSphere = SCNSphere(radius: 1.08)
        atmosphereSphere.segmentCount = 48
        let atmosphereMaterial = SCNMaterial()
        atmosphereMaterial.diffuse.contents = AppColors.atmosphere
        atmosphereMaterial.isDoubleSided = true
        atmosphereMaterial.transparency = 0.3
        atmosphereSphere.materials = [atmosphereMaterial]

        let atmosphereNode = SCNNode(geometry: atmosphereSphere)
        atmosphereNode.name = "atmosphere"
        globeNode.addChildNode(atmosphereNode)

        // Add countries from GeoJSON
        addCountriesFromGeoJSON(to: globeNode, coordinator: coordinator)

        // Start facing Europe/Africa (~15°E longitude)
        globeNode.eulerAngles.y = -.pi / 2 - .pi / 12

        // Add subtle rotation animation
        let rotation = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 60))
        globeNode.runAction(rotation, forKey: "autoRotation")

        return globeNode
    }

    private static func rebuildCoordinatorData(from globeNode: SCNNode, coordinator: GlobeView.Coordinator) {
        let landColor = AppColors.landUI

        for country in CountryDataCache.shared.countries {
            if let node = globeNode.childNode(withName: country.name, recursively: true) {
                coordinator.countryNodes[country.name] = node
                coordinator.originalColors[country.name] = landColor
            }

            // Give each polygon-country outline a fresh shader-driven material (the
            // cached .scn's materials lack the width shader modifier)
            if !country.isPointCountry,
               let outlineNode = globeNode.childNode(withName: "\(country.name)_outline", recursively: true) {
                let material = makeOutlineMaterial()
                outlineNode.geometry?.materials = [material]
                coordinator.outlineMaterials[country.name] = material
            }
        }
    }

    static func addCountriesFromGeoJSON(to globeNode: SCNNode, coordinator: GlobeView.Coordinator) {
        let countries = CountryDataCache.shared.countries
        let landColor = AppColors.landUI

        for country in countries {
            if country.isPointCountry {
                // Render as point marker (small island nations and microstates)
                guard let pointCoord = country.pointCoordinate else { continue }

                // Convert lat/lon to 3D position
                let position = PolygonTriangulator.latLonToSphere(lat: pointCoord.lat, lon: pointCoord.lon, radius: 1.005)

                // Create black outline circle (slightly larger, behind)
                let outlineCircle = SCNCylinder(radius: 0.014, height: 0.0005)
                let outlineMaterial = SCNMaterial()
                outlineMaterial.diffuse.contents = UIColor.black
                outlineMaterial.lightingModel = .constant
                outlineMaterial.isDoubleSided = true
                outlineCircle.materials = [outlineMaterial]

                let outlineNode = SCNNode(geometry: outlineCircle)
                outlineNode.name = "\(country.name)_outline"

                // Create a flat circle (thin cylinder) for the country
                let circle = SCNCylinder(radius: 0.012, height: 0.001)
                let material = SCNMaterial()
                material.diffuse.contents = landColor
                material.specular.contents = UIColor.clear
                material.shininess = 0.2
                material.isDoubleSided = true
                circle.materials = [material]

                let node = SCNNode(geometry: circle)
                node.name = country.name
                node.position = position

                // Orient the circles to face outward from globe center
                let direction = SCNVector3(position.x, position.y, position.z)
                let up = SCNVector3(0, 1, 0)
                node.look(at: SCNVector3(direction.x * 2, direction.y * 2, direction.z * 2), up: up, localFront: SCNVector3(0, 1, 0))

                // Position outline at same location
                outlineNode.position = position
                outlineNode.look(at: SCNVector3(direction.x * 2, direction.y * 2, direction.z * 2), up: up, localFront: SCNVector3(0, 1, 0))

                globeNode.addChildNode(outlineNode)
                globeNode.addChildNode(node)

                coordinator.countryNodes[country.name] = node
                coordinator.originalColors[country.name] = landColor
            } else {
                // Render as polygon (regular countries)
                if let geometry = PolygonTriangulator.createCountryGeometry(polygons: country.polygons, holes: country.holes) {
                    let material = SCNMaterial()
                    material.diffuse.contents = country.color
                    material.specular.contents = UIColor.clear
                    material.shininess = 0.2
                    material.isDoubleSided = true
                    geometry.materials = [material]

                    let node = SCNNode(geometry: geometry)
                    node.name = country.name
                    globeNode.addChildNode(node)
                    coordinator.countryNodes[country.name] = node
                    coordinator.originalColors[country.name] = country.color

                    // Add black border outline (shader-driven, zoom-dependent width)
                    if let outlineGeometry = PolygonTriangulator.createBorderOutlineGeometry(polygons: country.polygons) {
                        let outlineMaterial = makeOutlineMaterial()
                        outlineGeometry.materials = [outlineMaterial]
                        coordinator.outlineMaterials[country.name] = outlineMaterial

                        let outlineNode = SCNNode(geometry: outlineGeometry)
                        outlineNode.name = "\(country.name)_outline"
                        globeNode.addChildNode(outlineNode)
                    }
                }
            }
        }
    }

}
