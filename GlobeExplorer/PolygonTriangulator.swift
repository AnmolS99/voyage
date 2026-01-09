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

    // Create country geometry using simple grid-based fill
    static func createCountryGeometry(polygons: [[[Double]]], radius: Float = 1.003, cellSize: Double = 0.3) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

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

            // Create grid and fill cells that are inside the polygon
            var lat = minLat
            while lat <= maxLat {
                var lon = minLon
                while lon <= maxLon {
                    // Check if cell center is inside polygon
                    let centerLon = lon + cellSize / 2
                    let centerLat = lat + cellSize / 2

                    if isPointInPolygon(lon: centerLon, lat: centerLat, polygon: coords) {
                        let baseIndex = Int32(allVertices.count)

                        // Add 4 corners of the cell
                        allVertices.append(latLonToSphere(lat: lat, lon: lon, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat, lon: lon + cellSize, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon + cellSize, radius: radius))
                        allVertices.append(latLonToSphere(lat: lat + cellSize, lon: lon, radius: radius))

                        // Two triangles for the quad
                        allIndices.append(baseIndex)
                        allIndices.append(baseIndex + 1)
                        allIndices.append(baseIndex + 2)

                        allIndices.append(baseIndex)
                        allIndices.append(baseIndex + 2)
                        allIndices.append(baseIndex + 3)
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

    // Ear-clipping triangulation algorithm
    private static func earClipTriangulate(_ polygon: [[Double]]) -> [(Int, Int, Int)] {
        var triangles: [(Int, Int, Int)] = []
        var indices = Array(0..<polygon.count)

        // Ensure polygon is counter-clockwise
        let area = signedArea(polygon)
        if area > 0 {
            indices.reverse()
        }

        var iterations = 0
        let maxIterations = polygon.count * polygon.count

        while indices.count > 3 && iterations < maxIterations {
            iterations += 1
            var earFound = false

            for i in 0..<indices.count {
                let prev = indices[(i + indices.count - 1) % indices.count]
                let curr = indices[i]
                let next = indices[(i + 1) % indices.count]

                if isEar(polygon, indices: indices, prev: prev, curr: curr, next: next) {
                    triangles.append((prev, curr, next))
                    indices.remove(at: i)
                    earFound = true
                    break
                }
            }

            // If no ear found, force remove a vertex to prevent infinite loop
            if !earFound && indices.count > 3 {
                let i = 0
                let prev = indices[(i + indices.count - 1) % indices.count]
                let curr = indices[i]
                let next = indices[(i + 1) % indices.count]
                triangles.append((prev, curr, next))
                indices.remove(at: i)
            }
        }

        // Add final triangle
        if indices.count == 3 {
            triangles.append((indices[0], indices[1], indices[2]))
        }

        return triangles
    }

    private static func signedArea(_ polygon: [[Double]]) -> Double {
        var area = 0.0
        let n = polygon.count
        for i in 0..<n {
            let j = (i + 1) % n
            area += polygon[i][0] * polygon[j][1]
            area -= polygon[j][0] * polygon[i][1]
        }
        return area / 2.0
    }

    private static func isEar(_ polygon: [[Double]], indices: [Int], prev: Int, curr: Int, next: Int) -> Bool {
        let a = polygon[prev]
        let b = polygon[curr]
        let c = polygon[next]

        // Check if vertex is convex (cross product > 0 for CCW)
        let cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if cross >= 0 {
            return false // Reflex vertex, not an ear
        }

        // Check if any other vertex is inside this triangle
        for idx in indices {
            if idx == prev || idx == curr || idx == next { continue }
            if pointInTriangle(polygon[idx], a: a, b: b, c: c) {
                return false
            }
        }

        return true
    }

    private static func pointInTriangle(_ p: [Double], a: [Double], b: [Double], c: [Double]) -> Bool {
        let d1 = sign(p, a, b)
        let d2 = sign(p, b, c)
        let d3 = sign(p, c, a)

        let hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
        let hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)

        return !(hasNeg && hasPos)
    }

    private static func sign(_ p1: [Double], _ p2: [Double], _ p3: [Double]) -> Double {
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
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
