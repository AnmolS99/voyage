import Foundation
import SceneKit
import simd

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

    // Create country geometry using adaptive resolution grid-based fill
    static func createCountryGeometry(polygons: [[[Double]]], radius: Float = 1.003) -> SCNGeometry? {
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

            // Calculate adaptive cell size based on polygon size
            let lonSpan = maxLon - minLon
            let latSpan = maxLat - minLat
            let maxSpan = max(lonSpan, latSpan)

            let cellSize: Double
            if maxSpan < 0.5 {
                // Tiny countries (Vatican, San Marino, Monaco, etc.) - use triangle fan
                let baseIndex = Int32(allVertices.count)

                // Calculate centroid
                var centroidLon = 0.0, centroidLat = 0.0
                for coord in coords {
                    centroidLon += coord[0]
                    centroidLat += coord[1]
                }
                centroidLon /= Double(coords.count)
                centroidLat /= Double(coords.count)

                // Add centroid vertex
                allVertices.append(latLonToSphere(lat: centroidLat, lon: centroidLon, radius: radius))

                // Add boundary vertices
                for coord in coords {
                    allVertices.append(latLonToSphere(lat: coord[1], lon: coord[0], radius: radius))
                }

                // Create triangle fan
                for i in 0..<coords.count {
                    let next = (i + 1) % coords.count
                    allIndices.append(baseIndex) // centroid
                    allIndices.append(baseIndex + Int32(i) + 1)
                    allIndices.append(baseIndex + Int32(next) + 1)
                }
                continue
            } else if maxSpan < 2.0 {
                // Small countries (Luxembourg, Andorra, etc.)
                cellSize = 0.05
            } else if maxSpan < 10.0 {
                // Medium countries
                cellSize = 0.15
            } else {
                // Large countries
                cellSize = 0.3
            }

            // Create grid and fill cells that are inside the polygon
            var cellCount = 0
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

                        cellCount += 1
                    }

                    lon += cellSize
                }
                lat += cellSize
            }

            // Fallback: if no cells were created, use triangle fan
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

    // Create border outline as a continuous quad strip with shared vertices at joins
    static func createBorderOutlineGeometry(polygons: [[[Double]]], radius: Float = 1.005, thickness: Float = 0.002) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

        for polygon in polygons {
            var coords = polygon.filter { $0.count >= 2 }
            guard coords.count >= 3 else { continue }

            // Remove duplicate closing point (GeoJSON repeats first point at end)
            if let first = coords.first, let last = coords.last,
               first[0] == last[0] && first[1] == last[1] {
                coords.removeLast()
            }
            guard coords.count >= 3 else { continue }

            let n = coords.count

            // Convert all coordinates to 3D positions on sphere
            let positions: [simd_float3] = coords.map { coord in
                let p = latLonToSphere(lat: coord[1], lon: coord[0], radius: radius)
                return simd_float3(Float(p.x), Float(p.y), Float(p.z))
            }

            // Build inner/outer vertex pairs using miter offset at each vertex
            let baseIndex = Int32(allVertices.count)
            for i in 0..<n {
                let p = positions[i]
                let prev = positions[(i - 1 + n) % n]
                let next = positions[(i + 1) % n]
                let normal = p / radius

                // Perpendicular to each adjacent edge, projected onto sphere surface
                let perp1 = simd_normalize(simd_cross(p - prev, normal))
                let perp2 = simd_normalize(simd_cross(next - p, normal))

                // Miter direction: average of the two perpendiculars
                var miter = simd_normalize(perp1 + perp2)
                let dot = simd_dot(miter, perp1)
                let scale = dot > 0.3 ? min(thickness / dot, thickness * 2.0) : thickness
                miter *= scale

                allVertices.append(SCNVector3(p - miter))
                allVertices.append(SCNVector3(p + miter))
            }

            // Connect as continuous quad strip wrapping around
            for i in 0..<n {
                let next = (i + 1) % n
                let i0 = baseIndex + Int32(i * 2)
                let i1 = i0 + 1
                let i2 = baseIndex + Int32(next * 2)
                let i3 = i2 + 1

                allIndices.append(contentsOf: [i0, i1, i3, i0, i3, i2])
            }
        }

        guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

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
