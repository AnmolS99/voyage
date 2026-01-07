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
        oceanMaterial.diffuse.contents = UIColor(red: 0.2, green: 0.4, blue: 0.65, alpha: 1.0)
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
            }
        }
    }
}
