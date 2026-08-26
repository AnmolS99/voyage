import XCTest
import SceneKit
import simd
@testable import voyage

/// Guards the horizon culling of the globe's border outlines: that it never hides a
/// border the camera can see, and that it still hides a worthwhile share of them.
///
/// Mirrors the Android pair in `GlobeGeometryWorldTest` ("horizon culling never hides a
/// border the camera can see" / "…drops a real share of the world's border vertices"),
/// against the same `shared/data/world.geojson`.
final class OutlineSectorCullingTests: XCTestCase {

    /// The camera distance the sector grid was tuned at — iOS `GlobeState.zoomLevel`'s default.
    private let cameraDistance: Float = 4.0

    /// A sector as the coordinator sees it: `SCNNode.boundingSphere` plus its vertices.
    private struct Sector {
        let center: simd_float3
        let radius: Float
        let vertexCount: Int
        let positions: [simd_float3]
    }

    private lazy var borderRings: [[[Double]]] = CountryDataCache.shared.countries
        .filter { !$0.isPointCountry }
        .flatMap { $0.polygons }

    private func sectors(longitudeBands: Int = 12, latitudeBands: Int = 4) -> [Sector] {
        PolygonTriangulator.createSectoredOutlineGeometries(polygons: borderRings,
                                                            longitudeBands: longitudeBands,
                                                            latitudeBands: latitudeBands)
            .map { geometry in
                // The bounding sphere has to come from a node, not the geometry: it is
                // what registerOutlineSectors reads at load, and SceneKit derives it.
                let sphere = SCNNode(geometry: geometry).boundingSphere
                let source = geometry.sources(for: .vertex).first!
                var positions: [simd_float3] = []
                positions.reserveCapacity(source.vectorCount)
                source.data.withUnsafeBytes { raw in
                    let base = raw.baseAddress!.advanced(by: source.dataOffset)
                    for i in 0..<source.vectorCount {
                        let vertex = base.advanced(by: i * source.dataStride)
                            .assumingMemoryBound(to: Float.self)
                        positions.append(simd_float3(vertex[0], vertex[1], vertex[2]))
                    }
                }
                return Sector(center: simd_float3(Float(sphere.center.x),
                                                  Float(sphere.center.y),
                                                  Float(sphere.center.z)),
                              radius: Float(sphere.radius),
                              vertexCount: source.vectorCount,
                              positions: positions)
            }
    }

    /// Direction from the globe's center toward a camera looking straight down at lat/lon.
    private func cameraDirection(lat: Double, lon: Double) -> simd_float3 {
        let position = PolygonTriangulator.latLonToSphere(lat: lat, lon: lon, radius: 1.0)
        return simd_normalize(simd_float3(Float(position.x), Float(position.y), Float(position.z)))
    }

    private func isCulled(_ sector: Sector, from direction: simd_float3) -> Bool {
        GlobeView.Coordinator.isBeyondHorizon(center: sector.center,
                                              radius: sector.radius,
                                              cameraDirection: direction,
                                              distance: cameraDistance)
    }

    func testHorizonCullingNeverHidesABorderTheCameraCanSee() {
        let sectors = self.sectors()

        // The safety property of cullFarSideOutlineSectors: a hidden sector must hold no
        // vertex on the visible cap, or borders vanish mid-drag. Swept over the whole
        // globe, not one viewpoint.
        for lat in stride(from: -80, through: 80, by: 20) {
            for lon in stride(from: -180, to: 180, by: 30) {
                let direction = cameraDirection(lat: Double(lat), lon: Double(lon))
                for sector in sectors where isCulled(sector, from: direction) {
                    for position in sector.positions {
                        XCTAssertLessThan(simd_dot(position, direction), 1.0 / cameraDistance,
                                          "a vertex visible from (\(lat), \(lon)) was culled")
                    }
                }
            }
        }
    }

    func testHorizonCullingDropsARealShareOfTheWorldsBorderVertices() {
        let sectors = self.sectors()
        let total = sectors.reduce(0) { $0 + $1.vertexCount }

        // The measurement that justifies the lon x lat grid over longitude-only
        // bucketing, which sheds 0.0% here — see createSectoredOutlineGeometries. Guards
        // the grid against being quietly simplified back into slabs that never cull.
        var samples = 0
        var culledFraction = 0.0
        for lat in stride(from: -60, through: 60, by: 30) {
            for lon in stride(from: -180, to: 180, by: 30) {
                let direction = cameraDirection(lat: Double(lat), lon: Double(lon))
                let culled = sectors
                    .filter { isCulled($0, from: direction) }
                    .reduce(0) { $0 + $1.vertexCount }
                culledFraction += Double(culled) / Double(total)
                samples += 1
            }
        }
        let average = culledFraction / Double(samples)
        XCTAssertGreaterThan(average, 0.30,
                             "horizon culling only sheds \(Int(average * 100))% of border vertices")

        let longitudeOnly = self.sectors(longitudeBands: 12, latitudeBands: 1)
        let direction = cameraDirection(lat: 20, lon: 0)
        XCTAssertEqual(longitudeOnly.filter { isCulled($0, from: direction) }.count, 0,
                       "a pole-to-pole slab should never fall entirely behind the horizon")
    }

    func testSectorsSplitByLatitudeAsWellAsLongitude() {
        // Two rings sharing a longitude band, one far north and one far south.
        // Bucketing by longitude alone would merge them into a slab spanning the globe,
        // which the horizon test can never cull. Android's PolygonTriangulatorTest has
        // the same case.
        let rings = [square(lat: 60, lon: 0, size: 4), square(lat: -70, lon: 0, size: 4)]
        let sectors = PolygonTriangulator.createSectoredOutlineGeometries(polygons: rings)

        XCTAssertEqual(sectors.count, 2)
        for geometry in sectors {
            let radius = Float(SCNNode(geometry: geometry).boundingSphere.radius)
            XCTAssertLessThan(radius, 0.5, "sector radius \(radius) spans the globe")
        }
    }

    /// A closed square ring from (lat, lon) spanning `size` degrees, counter-clockwise.
    private func square(lat: Double, lon: Double, size: Double) -> [[Double]] {
        [[lon, lat], [lon + size, lat], [lon + size, lat + size], [lon, lat + size], [lon, lat]]
    }

    func testWorldOutlineVertexCountStaysWithinTheBudgetTheCullingAssumes() {
        let geometry = PolygonTriangulator.createBorderOutlineGeometry(polygons: borderRings)
        let vertexCount = geometry?.sources(for: .vertex).first?.vectorCount ?? 0

        // Recorded so a geometry-detail change shows up as a number rather than a
        // frame-rate report: the outline mesh is the scene's dominant cost, which is why
        // the sectors exist at all.
        XCTAssertTrue((300_000...500_000).contains(vertexCount),
                      "outline vertex count \(vertexCount) outside the expected range")
    }
}
