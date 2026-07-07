import Foundation
import SceneKit
import simd

class PolygonTriangulator {

    /// Max triangle edge / border segment length in true angular degrees before subdivision.
    /// Keeps fill and outline geometry hugging the sphere instead of cutting chords through it.
    private static let maxEdgeDegrees = 2.5

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

    // MARK: - Ring helpers

    /// Drops malformed coordinates, consecutive duplicates (zero-length segments break the
    /// miter math), and the duplicate closing point; nil if fewer than 3 points remain.
    private static func cleanRing(_ ring: [[Double]]) -> [[Double]]? {
        var coords: [[Double]] = []
        coords.reserveCapacity(ring.count)
        for coord in ring where coord.count >= 2 {
            if let last = coords.last, last[0] == coord[0] && last[1] == coord[1] { continue }
            coords.append(coord)
        }
        if coords.count > 1, let first = coords.first, let last = coords.last,
           first[0] == last[0] && first[1] == last[1] {
            coords.removeLast()
        }
        return coords.count >= 3 ? coords : nil
    }

    /// Absolute shoelace area of a ring in lon/lat space.
    private static func ringArea(_ coords: [[Double]]) -> Double {
        var sum = 0.0
        var j = coords.count - 1
        for i in 0..<coords.count {
            sum += (coords[j][0] - coords[i][0]) * (coords[j][1] + coords[i][1])
            j = i
        }
        return abs(sum / 2)
    }

    /// Length of a lon/lat segment in true angular degrees (longitude scaled by latitude).
    private static func angularLength(_ a: (lon: Double, lat: Double), _ b: (lon: Double, lat: Double)) -> Double {
        let midLat = (a.lat + b.lat) / 2 * .pi / 180
        let dLon = (b.lon - a.lon) * cos(midLat)
        let dLat = b.lat - a.lat
        return (dLon * dLon + dLat * dLat).squareRoot()
    }

    // MARK: - Country fill

    // Create country fill geometry by ear-clipping triangulation (with holes) in lon/lat space,
    // subdividing large triangles to follow sphere curvature, then projecting onto the sphere.
    // holes: inner ring coordinates to exclude from the fill (e.g., the Lesotho enclave in South Africa).
    static func createCountryGeometry(polygons: [[[Double]]], holes: [[[Double]]] = [], radius: Float = 1.003) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allIndices: [Int32] = []

        // Assign each hole ring to the outer ring that contains it
        var holesForPolygon: [Int: [[[Double]]]] = [:]
        for hole in holes {
            guard let first = hole.first(where: { $0.count >= 2 }) else { continue }
            if let index = polygons.firstIndex(where: { isPointInPolygon(lon: first[0], lat: first[1], polygon: $0) }) {
                holesForPolygon[index, default: []].append(hole)
            }
        }

        for (index, polygon) in polygons.enumerated() {
            let ringHoles = holesForPolygon[index] ?? []
            if !appendEarcutFill(polygon: polygon, holes: ringHoles, radius: radius,
                                 vertices: &allVertices, indices: &allIndices) {
                // Ring earcut couldn't handle (e.g. self-intersecting) — fall back to grid fill
                print("PolygonTriangulator: earcut failed for ring with \(polygon.count) points, using grid fill")
                appendGridFill(polygon: polygon, holes: ringHoles, radius: radius,
                               vertices: &allVertices, indices: &allIndices)
            }
        }

        guard !allVertices.isEmpty && !allIndices.isEmpty else { return nil }

        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let texCoordSource = SCNGeometrySource(textureCoordinates: computeTexCoords(vertices: allVertices, polygons: polygons))
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

        return SCNGeometry(sources: [vertexSource, texCoordSource], elements: [element])
    }

    /// Triangulates one outer ring (+ its holes) with earcut. Returns false if the
    /// triangulation fails or covers a wrong area, so the caller can fall back.
    private static func appendEarcutFill(polygon: [[Double]], holes: [[[Double]]], radius: Float,
                                         vertices: inout [SCNVector3], indices: inout [Int32]) -> Bool {
        guard let outer = cleanRing(polygon) else { return false }

        var flat: [Double] = []
        flat.reserveCapacity((outer.count + holes.reduce(0) { $0 + $1.count }) * 2)
        var holeIndices: [Int] = []
        var expectedArea = ringArea(outer)

        for coord in outer {
            flat.append(coord[0])
            flat.append(coord[1])
        }
        for hole in holes {
            guard let cleaned = cleanRing(hole) else { continue }
            holeIndices.append(flat.count / 2)
            for coord in cleaned {
                flat.append(coord[0])
                flat.append(coord[1])
            }
            expectedArea -= ringArea(cleaned)
        }
        guard expectedArea > 0 else { return false }

        let triangulated = Earcut.triangulate(data: flat, holeIndices: holeIndices)
        guard !triangulated.isEmpty else { return false }

        var points: [(lon: Double, lat: Double)] = []
        points.reserveCapacity(flat.count / 2)
        for i in stride(from: 0, to: flat.count, by: 2) {
            points.append((lon: flat[i], lat: flat[i + 1]))
        }

        // Validate coverage: a degenerate input (e.g. self-intersecting ring) produces a
        // triangulation whose total area deviates from the polygon's true area.
        var triangulatedArea = 0.0
        for t in stride(from: 0, to: triangulated.count, by: 3) {
            let a = points[triangulated[t]]
            let b = points[triangulated[t + 1]]
            let c = points[triangulated[t + 2]]
            let cross1 = (b.lon - a.lon) * (c.lat - a.lat)
            let cross2 = (c.lon - a.lon) * (b.lat - a.lat)
            triangulatedArea += abs(cross1 - cross2) / 2
        }
        guard abs(triangulatedArea - expectedArea) <= expectedArea * 0.05 else { return false }

        let triangles = subdivideForCurvature(points: &points, triangles: triangulated)

        let base = Int32(vertices.count)
        for point in points {
            vertices.append(latLonToSphere(lat: point.lat, lon: point.lon, radius: radius))
        }
        for index in triangles {
            indices.append(base + Int32(index))
        }
        return true
    }

    /// Splits triangles until no edge exceeds maxEdgeDegrees. Midpoints are shared between
    /// neighboring triangles (edge cache), keeping the mesh watertight.
    private static func subdivideForCurvature(points: inout [(lon: Double, lat: Double)], triangles: [Int]) -> [Int] {
        var midpointCache: [Int64: Int] = [:]

        func midpoint(_ i: Int, _ j: Int) -> Int {
            let key = (Int64(min(i, j)) << 32) | Int64(max(i, j))
            if let cached = midpointCache[key] { return cached }
            let index = points.count
            points.append((lon: (points[i].lon + points[j].lon) / 2,
                           lat: (points[i].lat + points[j].lat) / 2))
            midpointCache[key] = index
            return index
        }

        var output: [Int] = []
        output.reserveCapacity(triangles.count)
        var stack: [(Int, Int, Int)] = []
        for t in stride(from: 0, to: triangles.count, by: 3) {
            stack.append((triangles[t], triangles[t + 1], triangles[t + 2]))
        }

        while let (a, b, c) = stack.popLast() {
            let ab = angularLength(points[a], points[b])
            let bc = angularLength(points[b], points[c])
            let ca = angularLength(points[c], points[a])
            let longest = max(ab, bc, ca)

            if longest <= maxEdgeDegrees {
                output.append(contentsOf: [a, b, c])
            } else if longest == ab {
                let m = midpoint(a, b)
                stack.append((a, m, c))
                stack.append((m, b, c))
            } else if longest == bc {
                let m = midpoint(b, c)
                stack.append((a, b, m))
                stack.append((a, m, c))
            } else {
                let m = midpoint(c, a)
                stack.append((a, b, m))
                stack.append((m, b, c))
            }
        }
        return output
    }

    // MARK: - Grid fill fallback

    // Legacy adaptive grid-based point-in-polygon fill. Large cells cover the interior;
    // cells near borders subdivide to finer resolution. Kept as a fallback for rings
    // that earcut cannot triangulate.
    private static func appendGridFill(polygon: [[Double]], holes: [[[Double]]], radius: Float,
                                       vertices allVertices: inout [SCNVector3], indices allIndices: inout [Int32]) {
        guard let coords = cleanRing(polygon) else { return }

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
            let centerLon = lon + size / 2
            let centerLat = lat + size / 2
            let centerIn = isPointInPolygon(lon: centerLon, lat: centerLat, polygon: coords)

            if size <= minSize {
                if centerIn {
                    // Exclude cells whose center falls inside a hole (e.g., Lesotho within South Africa)
                    let inHole = holes.contains { isPointInPolygon(lon: centerLon, lat: centerLat, polygon: $0) }
                    if !inHole { emitCell(lat: lat, lon: lon, size: size) }
                }
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
                    // Before emitting, check whether a hole overlaps this cell.
                    // A hole overlaps if its center lands inside, or any hole vertex is within the cell bounds.
                    let holeOverlaps = !holes.isEmpty && holes.contains { hole in
                        isPointInPolygon(lon: centerLon, lat: centerLat, polygon: hole) ||
                        hole.contains { coord in
                            coord[0] >= lon && coord[0] <= lon + size &&
                            coord[1] >= lat && coord[1] <= lat + size
                        }
                    }
                    if holeOverlaps {
                        // Subdivide so the hole boundary is respected at finer resolution
                        addCell(lat: lat, lon: lon, size: half)
                        addCell(lat: lat, lon: lon + half, size: half)
                        addCell(lat: lat + half, lon: lon, size: half)
                        addCell(lat: lat + half, lon: lon + half, size: half)
                    } else {
                        emitCell(lat: lat, lon: lon, size: size)
                    }
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

    // MARK: - Border outline

    /// Inserts interpolated points into segments longer than maxEdgeDegrees so the outline
    /// follows the sphere surface (a long straight chord would dip below it and disappear).
    private static func densifyRing(_ coords: [[Double]]) -> [[Double]] {
        var result: [[Double]] = []
        result.reserveCapacity(coords.count)
        for i in 0..<coords.count {
            let current = coords[i]
            let next = coords[(i + 1) % coords.count]
            result.append(current)
            let length = angularLength((lon: current[0], lat: current[1]), (lon: next[0], lat: next[1]))
            if length > maxEdgeDegrees {
                let segments = Int(ceil(length / maxEdgeDegrees))
                for s in 1..<segments {
                    let t = Double(s) / Double(segments)
                    result.append([current[0] + (next[0] - current[0]) * t,
                                   current[1] + (next[1] - current[1]) * t])
                }
            }
        }
        return result
    }

    /// Geometry shader modifier for border outlines. Outline vertices sit ON the border
    /// centerline; each vertex's miter displacement direction is stored in the normal
    /// attribute (outlines use constant lighting, so normals are free). The shader pushes
    /// vertices apart by `outlineThickness` world units at render time, letting zoom code
    /// keep outlines at a constant on-screen width by updating one uniform instead of
    /// rebuilding ~335k vertices. `outlineRaise` lifts an outline off the sphere surface
    /// (used to draw the selected country's outline above its neighbours').
    static let outlineShaderModifier = """
    uniform float outlineThickness = 0.0015;
    uniform float outlineRaise = 0.0;
    _geometry.position.xyz += _geometry.normal * outlineThickness
                            + normalize(_geometry.position.xyz) * outlineRaise;
    """

    // Create border outline as a continuous quad strip with shared vertices at joins.
    // Width is applied by outlineShaderModifier at render time (see above); without the
    // modifier this geometry is degenerate (zero width).
    static func createBorderOutlineGeometry(polygons: [[[Double]]], radius: Float = 1.005) -> SCNGeometry? {
        var allVertices: [SCNVector3] = []
        var allMiters: [SCNVector3] = []
        var allIndices: [Int32] = []

        for polygon in polygons {
            guard let cleaned = cleanRing(polygon) else { continue }
            let coords = densifyRing(cleaned)

            let n = coords.count

            // Convert all coordinates to 3D positions on sphere
            let positions: [simd_float3] = coords.map { coord in
                let p = latLonToSphere(lat: coord[1], lon: coord[0], radius: radius)
                return simd_float3(Float(p.x), Float(p.y), Float(p.z))
            }

            // Build inner/outer vertex pairs; the miter offset direction is stored per
            // vertex and applied by the shader
            let baseIndex = Int32(allVertices.count)
            for i in 0..<n {
                let p = positions[i]
                let prev = positions[(i - 1 + n) % n]
                let next = positions[(i + 1) % n]
                let normal = p / radius

                // Perpendicular to each adjacent edge, projected onto sphere surface
                let perp1 = simd_normalize(simd_cross(p - prev, normal))
                let perp2 = simd_normalize(simd_cross(next - p, normal))

                // Miter direction: average of the two perpendiculars, lengthened on sharp
                // corners (capped at 2x) so the strip keeps its visual width
                var miter = simd_normalize(perp1 + perp2)
                let dot = simd_dot(miter, perp1)
                let scale: Float = dot > 0.3 ? min(1.0 / dot, 2.0) : 1.0
                miter *= scale

                allVertices.append(SCNVector3(p))
                allMiters.append(SCNVector3(-miter))
                allVertices.append(SCNVector3(p))
                allMiters.append(SCNVector3(miter))
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

        // No texcoord source: outlines render as flat color, never textured
        let vertexSource = SCNGeometrySource(vertices: allVertices)
        let miterSource = SCNGeometrySource(normals: allMiters)
        let element = SCNGeometryElement(indices: allIndices, primitiveType: .triangles)

        return SCNGeometry(sources: [vertexSource, miterSource], elements: [element])
    }
}
