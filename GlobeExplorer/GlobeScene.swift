import SceneKit
import SwiftUI
import UIKit

class GlobeScene {

    static func createScene(globeState: GlobeState, coordinator: GlobeView.Coordinator) -> SCNScene {
        let scene = SCNScene()
        scene.background.contents = UIColor.clear

        // Create globe container node
        let globeNode = SCNNode()
        globeNode.name = "globe"
        scene.rootNode.addChildNode(globeNode)
        coordinator.globeNode = globeNode

        // Create ocean sphere (base)
        let oceanSphere = SCNSphere(radius: 1.0)
        oceanSphere.segmentCount = 64
        let oceanMaterial = SCNMaterial()
        oceanMaterial.diffuse.contents = UIColor(red: 0.184, green: 0.525, blue: 0.651, alpha: 1.0)
        oceanMaterial.specular.contents = UIColor.white.withAlphaComponent(0.3)
        oceanMaterial.shininess = 0.3
        oceanSphere.materials = [oceanMaterial]

        let oceanNode = SCNNode(geometry: oceanSphere)
        oceanNode.name = "ocean"
        globeNode.addChildNode(oceanNode)

        // Create atmosphere glow
        let atmosphereSphere = SCNSphere(radius: 1.08)
        atmosphereSphere.segmentCount = 48
        let atmosphereMaterial = SCNMaterial()
        atmosphereMaterial.diffuse.contents = UIColor(red: 0.6, green: 0.8, blue: 1.0, alpha: 0.15)
        atmosphereMaterial.isDoubleSided = true
        atmosphereMaterial.transparency = 0.3
        atmosphereSphere.materials = [atmosphereMaterial]

        let atmosphereNode = SCNNode(geometry: atmosphereSphere)
        atmosphereNode.name = "atmosphere"
        globeNode.addChildNode(atmosphereNode)

        // Add countries from GeoJSON
        addCountriesFromGeoJSON(to: globeNode, coordinator: coordinator)

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

        // Add subtle rotation animation
        let rotation = SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 60))
        globeNode.runAction(rotation, forKey: "autoRotation")

        return scene
    }

    static func addCountriesFromGeoJSON(to globeNode: SCNNode, coordinator: GlobeView.Coordinator) {
        let countries = GeoJSONParser.loadCountries()

        for country in countries {
            if let geometry = PolygonTriangulator.createCountryGeometry(polygons: country.polygons) {
                let material = SCNMaterial()
                material.diffuse.contents = country.color
                material.specular.contents = UIColor.white.withAlphaComponent(0.2)
                material.shininess = 0.2
                material.isDoubleSided = true
                geometry.materials = [material]

                let node = SCNNode(geometry: geometry)
                node.name = country.name
                globeNode.addChildNode(node)
                coordinator.countryNodes[country.name] = node
                coordinator.originalColors[country.name] = country.color

                // Add black border outline
                if let outlineGeometry = PolygonTriangulator.createBorderOutlineGeometry(polygons: country.polygons) {
                    let outlineMaterial = SCNMaterial()
                    outlineMaterial.diffuse.contents = UIColor.black
                    outlineMaterial.lightingModel = .constant // Make it always visible
                    outlineMaterial.isDoubleSided = true
                    outlineGeometry.materials = [outlineMaterial]

                    let outlineNode = SCNNode(geometry: outlineGeometry)
                    outlineNode.name = "\(country.name)_outline"
                    globeNode.addChildNode(outlineNode)
                }
            }
        }

        // Add point countries (small island nations and microstates)
        addPointCountries(to: globeNode, coordinator: coordinator)
    }

    static func addPointCountries(to globeNode: SCNNode, coordinator: GlobeView.Coordinator) {
        let landColor = UIColor(red: 0.204, green: 0.745, blue: 0.510, alpha: 1.0) // Green #34BE82

        for country in PointCountriesData.countries {
            // Convert lat/lon to 3D position
            let position = PolygonTriangulator.latLonToSphere(lat: country.lat, lon: country.lon, radius: 1.005)

            // Create a small sphere for the country
            let sphere = SCNSphere(radius: 0.012)
            let material = SCNMaterial()
            material.diffuse.contents = landColor
            material.specular.contents = UIColor.white.withAlphaComponent(0.2)
            material.shininess = 0.2
            sphere.materials = [material]

            let node = SCNNode(geometry: sphere)
            node.name = country.name
            node.position = position
            globeNode.addChildNode(node)

            coordinator.countryNodes[country.name] = node
            coordinator.originalColors[country.name] = landColor
        }
    }
}
