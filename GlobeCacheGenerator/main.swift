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

// MARK: - Polygon Triangulation (same as iOS version)

func latLonToSphere(lat: Double, lon: Double, radius: Float) -> SCNVector3 {
    let latRad = Float(lat) * .pi / 180
    let lonRad = Float(-lon) * .pi / 180

    let x = radius * cos(latRad) * cos(lonRad)
    let y = radius * sin(latRad)
    let z = radius * cos(latRad) * sin(lonRad)

    return SCNVector3(x, y, z)
}

func isPointInPolygon(lon: Double, lat: Double, polygon: [[Double]]) -> Bool {
    var inside = false
    var j = polygon.count - 1

    for i in 0..<polygon.count {
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

func createCountryGeometry(polygons: [[[Double]]], radius: Float = 1.003) -> SCNGeometry? {
    var allVertices: [SCNVector3] = []
    var allIndices: [Int32] = []

    for polygon in polygons {
        let coords = polygon.filter { $0.count >= 2 }
        guard coords.count >= 3 else { continue }

        var minLon = Double.infinity, maxLon = -Double.infinity
        var minLat = Double.infinity, maxLat = -Double.infinity
        for coord in coords {
            minLon = min(minLon, coord[0])
            maxLon = max(maxLon, coord[0])
            minLat = min(minLat, coord[1])
            maxLat = max(maxLat, coord[1])
        }

        let lonSpan = maxLon - minLon
        let latSpan = maxLat - minLat
        let maxSpan = max(lonSpan, latSpan)

        let cellSize: Double
        if maxSpan < 0.5 {
            let baseIndex = Int32(allVertices.count)

            var centroidLon = 0.0, centroidLat = 0.0
            for coord in coords {
                centroidLon += coord[0]
                centroidLat += coord[1]
            }
            centroidLon /= Double(coords.count)
            centroidLat /= Double(coords.count)

            allVertices.append(latLonToSphere(lat: centroidLat, lon: centroidLon, radius: radius))

            for coord in coords {
                allVertices.append(latLonToSphere(lat: coord[1], lon: coord[0], radius: radius))
            }

            for i in 0..<coords.count {
                let next = (i + 1) % coords.count
                allIndices.append(baseIndex)
                allIndices.append(baseIndex + Int32(i) + 1)
                allIndices.append(baseIndex + Int32(next) + 1)
            }
            continue
        } else if maxSpan < 2.0 {
            cellSize = 0.05
        } else if maxSpan < 10.0 {
            cellSize = 0.15
        } else {
            cellSize = 0.3
        }

        var cellCount = 0
        var lat = minLat
        while lat <= maxLat {
            var lon = minLon
            while lon <= maxLon {
                let centerLon = lon + cellSize / 2
                let centerLat = lat + cellSize / 2

                if isPointInPolygon(lon: centerLon, lat: centerLat, polygon: coords) {
                    let baseIndex = Int32(allVertices.count)

                    allVertices.append(latLonToSphere(lat: lat, lon: lon, radius: radius))
                    allVertices.append(latLonToSphere(lat: lat, lon: lon + cellSize, radius: radius))
                    allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon + cellSize, radius: radius))
                    allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon, radius: radius))

                    allIndices.append(baseIndex)
                    allIndices.append(baseIndex + 1)
                    allIndices.append(baseIndex + 2)

                    allIndices.append(baseIndex)
                    allIndices.append(baseIndex + 2)
                    allIndices.append(baseIndex + 3)

                    cellCount += 1
                }

                lon += cellSize
            }
            lat += cellSize
        }

        if cellCount == 0 {
            let baseIndex = Int32(allVertices.count)

            var centroidLon = 0.0, centroidLat = 0.0
            for coord in coords {
                centroidLon += coord[0]
                centroidLat += coord[1]
            }
            centroidLon /= Double(coords.count)
            centroidLat /= Double(coords.count)

            allVertices.append(latLonToSphere(lat: centroidLat, lon: centroidLon, radius: radius))

            for coord in coords {
                allVertices.append(latLonToSphere(lat: coord[1], lon: coord[0], radius: radius))
            }

            for i in 0..<coords.count {
                let next = (i + 1) % coords.count
                allIndices.append(baseIndex)
                allIndices.append(baseIndex + Int32(i) + 1)
                allIndices.append(baseIndex + Int32(next) + 1)
            }
        }
    }

    guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

    let vertexSource = SCNGeometrySource(vertices: allVertices)
    let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

    return SCNGeometry(sources: [vertexSource], elements: [element])
}

func createBorderOutlineGeometry(polygons: [[[Double]]], radius: Float = 1.005, thickness: Float = 0.002) -> SCNGeometry? {
    var allVertices: [SCNVector3] = []
    var allIndices: [Int32] = []

    for polygon in polygons {
        let coords = polygon.filter { $0.count >= 2 }
        guard coords.count >= 3 else { continue }

        for i in 0..<coords.count {
            let next = (i + 1) % coords.count

            let lat1 = coords[i][1]
            let lon1 = coords[i][0]
            let lat2 = coords[next][1]
            let lon2 = coords[next][0]

            let p1 = latLonToSphere(lat: lat1, lon: lon1, radius: radius)
            let p2 = latLonToSphere(lat: lat2, lon: lon2, radius: radius)

            let dx = Float(p2.x - p1.x)
            let dy = Float(p2.y - p1.y)
            let dz = Float(p2.z - p1.z)

            let nx = Float(p1.x) / radius
            let ny = Float(p1.y) / radius
            let nz = Float(p1.z) / radius

            let perpX = dy * nz - dz * ny
            let perpY = dz * nx - dx * nz
            let perpZ = dx * ny - dy * nx

            let perpLen = sqrtf(perpX * perpX + perpY * perpY + perpZ * perpZ)
            guard perpLen > 0.0001 else { continue }

            let px = perpX / perpLen * thickness
            let py = perpY / perpLen * thickness
            let pz = perpZ / perpLen * thickness

            let baseIndex = Int32(allVertices.count)
            let p1x = Float(p1.x), p1y = Float(p1.y), p1z = Float(p1.z)
            let p2x = Float(p2.x), p2y = Float(p2.y), p2z = Float(p2.z)

            allVertices.append(SCNVector3(p1x - px, p1y - py, p1z - pz))
            allVertices.append(SCNVector3(p1x + px, p1y + py, p1z + pz))
            allVertices.append(SCNVector3(p2x + px, p2y + py, p2z + pz))
            allVertices.append(SCNVector3(p2x - px, p2y - py, p2z - pz))

            allIndices.append(baseIndex)
            allIndices.append(baseIndex + 1)
            allIndices.append(baseIndex + 2)

            allIndices.append(baseIndex)
            allIndices.append(baseIndex + 2)
            allIndices.append(baseIndex + 3)
        }
    }

    guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

    let vertexSource = SCNGeometrySource(vertices: allVertices)
    let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

    return SCNGeometry(sources: [vertexSource], elements: [element])
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

            let position = latLonToSphere(lat: pointCoord.lat, lon: pointCoord.lon, radius: 1.005)

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
            if let geometry = createCountryGeometry(polygons: country.polygons) {
                let material = SCNMaterial()
                material.diffuse.contents = landColor
                material.specular.contents = NSColor.clear
                material.shininess = 0.2
                material.isDoubleSided = true
                geometry.materials = [material]

                let node = SCNNode(geometry: geometry)
                node.name = country.name
                globeNode.addChildNode(node)

                if let outlineGeometry = createBorderOutlineGeometry(polygons: country.polygons) {
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
