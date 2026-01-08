import Foundation
import SceneKit

class PolygonTriangulator {

    // Convert lat/lon to 3D point on sphere
    static func latLonToSphere(lat: Double, lon: Double, radius: Float) -> SCNVector3 {
        let latRad = Float(lat) * .pi / 180
        let lonRad = Float(-lon) * .pi / 180

        let x = radius * cos(latRad) * cos(lonRad)
        let y = radius * sin(latRad)
        let z = radius * cos(latRad) * sin(lonRad)

        return SCNVector3(x, y, z)
    }

    // Rasterize polygon into grid cells and render as small quads
    static func createCountryGeometry(polygons: [[[Double]]], radius: Float = 1.003) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

        let cellSize: Double = 0.05 // degrees per cell (higher resolution)

        for polygon in polygons {
            let coords = polygon.filter { $0.count >= 2 }
            guard coords.count >= 3 else { continue }

            // Get bounding box
            var minLon = Double.infinity, maxLon = -Double.infinity
            var minLat = Double.infinity, maxLat = -Double.infinity

            for coord in coords {
                minLon = min(minLon, coord[0])
                maxLon = max(maxLon, coord[0])
                minLat = min(minLat, coord[1])
                maxLat = max(maxLat, coord[1])
            }

            // Rasterize: create grid cells and check if center is inside polygon
            var lat = minLat
            while lat < maxLat {
                var lon = minLon
                while lon < maxLon {
                    let centerLon = lon + cellSize / 2
                    let centerLat = lat + cellSize / 2

                    if isPointInPolygon(lon: centerLon, lat: centerLat, polygon: coords) {
                        // Add a quad for this cell
                        let startIndex = Int32(allVertices.count)

                        // Four corners of the cell
                        allVertices.append(latLonToSphere(lat: lat, lon: lon, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat, lon: lon + cellSize, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon + cellSize, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon, radius: radius))

                        // Two triangles for the quad
                        allIndices.append(startIndex)
                        allIndices.append(startIndex + 1)
                        allIndices.append(startIndex + 2)

                        allIndices.append(startIndex)
                        allIndices.append(startIndex + 2)
                        allIndices.append(startIndex + 3)
                    }

                    lon += cellSize
                }
                lat += cellSize
            }
        }

        guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

        return SCNGeometry(sources: [vertexSource], elements: [element])
    }

    // Create border outline geometry from polygon coordinates
    static func createBorderOutlineGeometry(polygons: [[[Double]]], radius: Float = 1.005) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

        for polygon in polygons {
            let coords = polygon.filter { $0.count >= 2 }
            guard coords.count >= 3 else { continue }

            let startIndex = Int32(allVertices.count)

            // Add vertices for each point in the polygon
            for coord in coords {
                let lat = coord[1]
                let lon = coord[0]
                let point = latLonToSphere(lat: lat, lon: lon, radius: radius)
                allVertices.append(point)
            }

            // Create line indices connecting consecutive points
            for i in 0..<coords.count {
                allIndices.append(startIndex + Int32(i))
                allIndices.append(startIndex + Int32((i + 1) % coords.count))
            }
        }

        guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .line)

        return SCNGeometry(sources: [vertexSource], elements: [element])
    }

    // Ray casting algorithm for point-in-polygon test
    static func isPointInPolygon(lon: Double, lat: Double, polygon: [[Double]]) -> Bool {
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
}
