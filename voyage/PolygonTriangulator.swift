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

    // Compute UV texture coordinates from 3D vertices by reverse-mapping to lat/lon
    private static func computeTexCoords(vertices: [SCNVector3], polygons: [[[Double]]]) -> [CGPoint] {
        // Compute overall bounding box across all polygons
        var minLon = Double.infinity, maxLon = -Double.infinity
        var minLat = Double.infinity, maxLat = -Double.infinity
        for polygon in polygons {
            for coord in polygon where coord.count >= 2 {
                minLon = min(minLon, coord[0])
                maxLon = max(maxLon, coord[0])
                minLat = min(minLat, coord[1])
                maxLat = max(maxLat, coord[1])
            }
        }
        let lonSpan = maxLon - minLon
        let latSpan = maxLat - minLat

        return vertices.map { v in
            let len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
            let latDeg = Double(asin(v.y / len)) * 180.0 / .pi
            let lonDeg = Double(-atan2(v.z, v.x)) * 180.0 / .pi
            let u = lonSpan > 0 ? (lonDeg - minLon) / lonSpan : 0.5
            let vv = latSpan > 0 ? (latDeg - minLat) / latSpan : 0.5
            return CGPoint(x: u, y: vv)
        }
    }

    // Create country fill geometry using adaptive grid-based point-in-polygon fill.
    // Large cells cover the interior; cells near borders subdivide to finer resolution.
    static func createCountryGeometry(polygons: [[[Double]]], radius: Float = 1.003) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

        for polygon in polygons {
            var coords = polygon.filter { $0.count >= 2 }
            guard coords.count >= 3 else { continue }

            // Remove duplicate closing point
            if let first = coords.first, let last = coords.last,
               first[0] == last[0] && first[1] == last[1] {
                coords.removeLast()
            }
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

            let maxSpan = max(maxLon - minLon, maxLat - minLat)
            let startSize: Double
            let minSize: Double
            if maxSpan < 0.01 {
                startSize = 0.0005; minSize = 0.0005
            } else if maxSpan < 0.05 {
                startSize = 0.002; minSize = 0.002
            } else if maxSpan < 0.2 {
                startSize = 0.01; minSize = 0.01
            } else if maxSpan < 0.5 {
                startSize = 0.04; minSize = 0.02
            } else if maxSpan < 2.0 {
                startSize = 0.16; minSize = 0.04
            } else if maxSpan < 10.0 {
                startSize = 0.5; minSize = 0.125
            } else if maxSpan < 30.0 {
                startSize = 2.0; minSize = 0.125
            } else {
                startSize = 4.0; minSize = 0.125
            }

            var cellCount = 0

            func emitCell(lat: Double, lon: Double, size: Double) {
                // Expand interior cells slightly to eliminate T-junction gaps; border cells stay tight
                let overlap = size > minSize ? size * 0.02 : 0.0
                let baseIndex = Int32(allVertices.count)
                allVertices.append(latLonToSphere(lat: lat - overlap, lon: lon - overlap, radius: radius))
                allVertices.append(latLonToSphere(lat: lat - overlap, lon: lon + size + overlap, radius: radius))
                allVertices.append(latLonToSphere(lat: lat + size + overlap, lon: lon + size + overlap, radius: radius))
                allVertices.append(latLonToSphere(lat: lat + size + overlap, lon: lon - overlap, radius: radius))
                allIndices.append(contentsOf: [baseIndex, baseIndex + 1, baseIndex + 2,
                                               baseIndex, baseIndex + 2, baseIndex + 3])
                cellCount += 1
            }

            func addCell(lat: Double, lon: Double, size: Double) {
                let centerIn = isPointInPolygon(lon: lon + size / 2, lat: lat + size / 2, polygon: coords)

                if size <= minSize {
                    if centerIn { emitCell(lat: lat, lon: lon, size: size) }
                    return
                }

                let c00 = isPointInPolygon(lon: lon, lat: lat, polygon: coords)
                let c10 = isPointInPolygon(lon: lon + size, lat: lat, polygon: coords)
                let c01 = isPointInPolygon(lon: lon, lat: lat + size, polygon: coords)
                let c11 = isPointInPolygon(lon: lon + size, lat: lat + size, polygon: coords)

                if c00 && c10 && c01 && c11 && centerIn {
                    // Corners + center inside — verify edge midpoints to catch concavities
                    let half = size / 2
                    let edgesIn = isPointInPolygon(lon: lon + half, lat: lat, polygon: coords) &&
                                  isPointInPolygon(lon: lon + size, lat: lat + half, polygon: coords) &&
                                  isPointInPolygon(lon: lon + half, lat: lat + size, polygon: coords) &&
                                  isPointInPolygon(lon: lon, lat: lat + half, polygon: coords)
                    if edgesIn {
                        emitCell(lat: lat, lon: lon, size: size)
                    } else {
                        // Edge crosses concavity — subdivide
                        addCell(lat: lat, lon: lon, size: half)
                        addCell(lat: lat, lon: lon + half, size: half)
                        addCell(lat: lat + half, lon: lon, size: half)
                        addCell(lat: lat + half, lon: lon + half, size: half)
                    }
                } else if !c00 && !c10 && !c01 && !c11 && !centerIn {
                    // All test points outside — but a narrow feature (peninsula, isthmus)
                    // might still pass through. Subdivide if any polygon vertex is in the cell.
                    let hasVertex = coords.contains { coord in
                        coord[0] >= lon && coord[0] <= lon + size &&
                        coord[1] >= lat && coord[1] <= lat + size
                    }
                    if !hasVertex { return }
                    let half = size / 2
                    addCell(lat: lat, lon: lon, size: half)
                    addCell(lat: lat, lon: lon + half, size: half)
                    addCell(lat: lat + half, lon: lon, size: half)
                    addCell(lat: lat + half, lon: lon + half, size: half)
                } else {
                    // Near border — subdivide
                    let half = size / 2
                    addCell(lat: lat, lon: lon, size: half)
                    addCell(lat: lat, lon: lon + half, size: half)
                    addCell(lat: lat + half, lon: lon, size: half)
                    addCell(lat: lat + half, lon: lon + half, size: half)
                }
            }

            var lat = minLat
            while lat < maxLat {
                var lon = minLon
                while lon < maxLon {
                    addCell(lat: lat, lon: lon, size: startSize)
                    lon += startSize
                }
                lat += startSize
            }

            // Fallback for tiny polygons where no grid cell center falls inside
            if cellCount == 0 {
                let baseIndex = Int32(allVertices.count)
                let centroidLon = coords.reduce(0.0) { $0 + $1[0] } / Double(coords.count)
                let centroidLat = coords.reduce(0.0) { $0 + $1[1] } / Double(coords.count)
                allVertices.append(latLonToSphere(lat: centroidLat, lon: centroidLon, radius: radius))
                for coord in coords {
                    allVertices.append(latLonToSphere(lat: coord[1], lon: coord[0], radius: radius))
                }
                for i in 0..<coords.count {
                    let next = (i + 1) % coords.count
                    allIndices.append(contentsOf: [baseIndex, baseIndex + Int32(i) + 1, baseIndex + Int32(next) + 1])
                }
            }
        }

        guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let texCoordSource = SCNGeometrySource(textureCoordinates: computeTexCoords(vertices: allVertices, polygons: polygons))
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

        return SCNGeometry(sources: [vertexSource, texCoordSource], elements: [element])
    }

    // Ray casting point-in-polygon test
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

    // Create border outline as a continuous quad strip with shared vertices at joins
    static func createBorderOutlineGeometry(polygons: [[[Double]]], radius: Float = 1.005, thickness: Float = 0.0015) -> SCNGeometry? {
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
        let texCoordSource = SCNGeometrySource(textureCoordinates: computeTexCoords(vertices: allVertices, polygons: polygons))
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

        return SCNGeometry(sources: [vertexSource, texCoordSource], elements: [element])
    }
}
