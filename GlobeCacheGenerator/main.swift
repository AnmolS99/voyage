import Foundation
import SceneKit

// MARK: - GeoJSON Parsing (macOS version - reads from file path)

struct GeoJSONCountry {
    let name: String
    let polygons: [[[Double]]]
    let isPointCountry: Bool
    let pointCoordinate: (lat: Double, lon: Double)?

    static func landColor() -> NSColor {
        return NSColor(red: 0.204, green: 0.745, blue: 0.510, alpha: 1.0)
    }
}

func loadCountries(from geojsonPath: String) -> [GeoJSONCountry] {
    guard let data = FileManager.default.contents(atPath: geojsonPath),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let features = json["features"] as? [[String: Any]] else {
        print("Failed to load GeoJSON from \(geojsonPath)")
        return []
    }

    var countries: [GeoJSONCountry] = []

    for feature in features {
        guard let properties = feature["properties"] as? [String: Any],
              let name = properties["name"] as? String ?? properties["NAME"] as? String,
              let geometry = feature["geometry"] as? [String: Any],
              let type = geometry["type"] as? String,
              let coordinates = geometry["coordinates"] else {
            continue
        }

        let renderAs = properties["renderAs"] as? String
        let isPointCountry = renderAs == "point"

        if type == "Point" {
            if let coords = coordinates as? [Double], coords.count >= 2 {
                let lon = coords[0]
                let lat = coords[1]
                let country = GeoJSONCountry(
                    name: name,
                    polygons: [],
                    isPointCountry: true,
                    pointCoordinate: (lat: lat, lon: lon)
                )
                countries.append(country)
            }
        } else {
            var polygons: [[[Double]]] = []

            if type == "Polygon" {
                if let coords = coordinates as? [[[Double]]] {
                    if let outerRing = coords.first {
                        polygons.append(outerRing)
                    }
                }
            } else if type == "MultiPolygon" {
                if let multiCoords = coordinates as? [[[[Double]]]] {
                    for polygon in multiCoords {
                        if let outerRing = polygon.first {
                            polygons.append(outerRing)
                        }
                    }
                }
            }

            if !polygons.isEmpty {
                let country = GeoJSONCountry(
                    name: name,
                    polygons: polygons,
                    isPointCountry: isPointCountry,
                    pointCoordinate: nil
                )
                countries.append(country)
            }
        }
    }

    return countries
}

// MARK: - Globe Generation

func createGlobeNode(countries: [GeoJSONCountry]) -> SCNNode {
    let globeNode = SCNNode()
    globeNode.name = "globe"

    let landColor = GeoJSONCountry.landColor()

    // Create ocean sphere (base)
    let oceanSphere = SCNSphere(radius: 1.0)
    oceanSphere.segmentCount = 64
    let oceanMaterial = SCNMaterial()
    oceanMaterial.diffuse.contents = NSColor(red: 0.184, green: 0.525, blue: 0.651, alpha: 1.0)
    oceanMaterial.specular.contents = NSColor.clear
    oceanMaterial.shininess = 0.3
    oceanSphere.materials = [oceanMaterial]

    let oceanNode = SCNNode(geometry: oceanSphere)
    oceanNode.name = "ocean"
    globeNode.addChildNode(oceanNode)

    // Create atmosphere glow
    let atmosphereSphere = SCNSphere(radius: 1.08)
    atmosphereSphere.segmentCount = 48
    let atmosphereMaterial = SCNMaterial()
    atmosphereMaterial.diffuse.contents = NSColor(red: 0.6, green: 0.8, blue: 1.0, alpha: 0.15)
    atmosphereMaterial.isDoubleSided = true
    atmosphereMaterial.transparency = 0.3
    atmosphereSphere.materials = [atmosphereMaterial]

    let atmosphereNode = SCNNode(geometry: atmosphereSphere)
    atmosphereNode.name = "atmosphere"
    globeNode.addChildNode(atmosphereNode)

    // Add countries
    for country in countries {
        if country.isPointCountry {
            guard let pointCoord = country.pointCoordinate else { continue }

            let position = PolygonTriangulator.latLonToSphere(lat: pointCoord.lat, lon: pointCoord.lon, radius: 1.005)

            // Create black outline circle (slightly larger, behind)
            let outlineCircle = SCNCylinder(radius: 0.014, height: 0.0005)
            let outlineMaterial = SCNMaterial()
            outlineMaterial.diffuse.contents = NSColor.black
            outlineMaterial.lightingModel = .constant
            outlineMaterial.isDoubleSided = true
            outlineCircle.materials = [outlineMaterial]

            let outlineNode = SCNNode(geometry: outlineCircle)
            outlineNode.name = "\(country.name)_outline"

            // Create a flat circle (thin cylinder) for the country
            let circle = SCNCylinder(radius: 0.012, height: 0.001)
            let material = SCNMaterial()
            material.diffuse.contents = landColor
            material.specular.contents = NSColor.clear
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

            outlineNode.position = position
            outlineNode.look(at: SCNVector3(direction.x * 2, direction.y * 2, direction.z * 2), up: up, localFront: SCNVector3(0, 1, 0))

            globeNode.addChildNode(outlineNode)
            globeNode.addChildNode(node)
        } else {
            if let geometry = PolygonTriangulator.createCountryGeometry(polygons: country.polygons) {
                let material = SCNMaterial()
                material.diffuse.contents = landColor
                material.specular.contents = NSColor.clear
                material.shininess = 0.2
                material.isDoubleSided = true
                geometry.materials = [material]

                let node = SCNNode(geometry: geometry)
                node.name = country.name
                globeNode.addChildNode(node)

                if let outlineGeometry = PolygonTriangulator.createBorderOutlineGeometry(polygons: country.polygons) {
                    let outlineMaterial = SCNMaterial()
                    outlineMaterial.diffuse.contents = NSColor.black
                    outlineMaterial.lightingModel = .constant
                    outlineMaterial.isDoubleSided = true
                    outlineGeometry.materials = [outlineMaterial]

                    let outlineNode = SCNNode(geometry: outlineGeometry)
                    outlineNode.name = "\(country.name)_outline"
                    globeNode.addChildNode(outlineNode)
                }
            }
        }
    }

    return globeNode
}

// MARK: - Main

func main() {
    let args = CommandLine.arguments

    // Determine paths
    let scriptDir = URL(fileURLWithPath: #file).deletingLastPathComponent().path
    let projectDir = URL(fileURLWithPath: scriptDir).deletingLastPathComponent().path

    let geojsonPath: String
    let outputPath: String

    if args.count >= 3 {
        geojsonPath = args[1]
        outputPath = args[2]
    } else {
        geojsonPath = "\(projectDir)/voyage/world.geojson"
        outputPath = "\(projectDir)/voyage/globe.scn"
    }

    print("Globe Cache Generator")
    print("=====================")
    print("GeoJSON: \(geojsonPath)")
    print("Output:  \(outputPath)")
    print("")

    // Check input file exists
    guard FileManager.default.fileExists(atPath: geojsonPath) else {
        print("Error: GeoJSON file not found at \(geojsonPath)")
        exit(1)
    }

    // Load countries
    print("Loading countries from GeoJSON...")
    let countries = loadCountries(from: geojsonPath)
    print("Loaded \(countries.count) countries")

    let polygonCountries = countries.filter { !$0.isPointCountry }
    let pointCountries = countries.filter { $0.isPointCountry }
    print("  - \(polygonCountries.count) polygon countries")
    print("  - \(pointCountries.count) point countries")
    print("")

    // Generate globe
    print("Generating globe geometry...")
    let globeNode = createGlobeNode(countries: countries)

    // Create scene and add globe
    let scene = SCNScene()
    scene.rootNode.addChildNode(globeNode)

    // Count nodes
    var countryNodeCount = 0
    globeNode.enumerateChildNodes { node, _ in
        if let name = node.name, !name.hasSuffix("_outline") && name != "ocean" && name != "atmosphere" {
            countryNodeCount += 1
        }
    }
    print("Created \(countryNodeCount) country nodes")
    print("")

    // Save scene
    print("Saving scene to \(outputPath)...")
    let outputURL = URL(fileURLWithPath: outputPath)

    do {
        let success = scene.write(to: outputURL, options: nil, delegate: nil, progressHandler: { progress, error, stop in
            if let error = error {
                print("Progress error: \(error)")
            }
        })

        if success {
            // Get file size
            if let attrs = try? FileManager.default.attributesOfItem(atPath: outputPath),
               let size = attrs[.size] as? Int64 {
                let sizeInMB = Double(size) / 1_000_000.0
                print("Success! Saved globe.scn (\(String(format: "%.1f", sizeInMB)) MB)")
            } else {
                print("Success! Saved globe.scn")
            }
        } else {
            print("Error: Failed to write scene file")
            exit(1)
        }
    }

    print("")
    print("Done! The globe cache has been regenerated.")
}

main()
