import XCTest
import SceneKit
import simd
@testable import voyage

final class voyageTests: XCTestCase {

    // Test that globe.scn contains all countries from source data (map and globe consistency)
    func testGlobeAndMapCountryConsistency() {
        // Get expected countries from source data (used by map)
        // All countries (both polygon and point) are now in world.geojson
        let expectedCountries = Set(GeoJSONParser.loadCountries().map { $0.name })

        // Load globe.scn and extract country names
        guard let url = Bundle.main.url(forResource: "globe", withExtension: "scn"),
              let scene = try? SCNScene(url: url, options: nil),
              let globeNode = scene.rootNode.childNode(withName: "globe", recursively: true) else {
            XCTFail("Could not load globe.scn")
            return
        }

        var globeCountries = Set<String>()
        globeNode.enumerateChildNodes { node, _ in
            if let name = node.name,
               !name.isEmpty,
               !name.hasSuffix("_outline"),
               !name.hasPrefix("outline_sector_"),
               name != "ocean",
               name != "atmosphere" {
                globeCountries.insert(name)
            }
        }

        // Check for countries missing from globe
        let missingFromGlobe = expectedCountries.subtracting(globeCountries)
        XCTAssertTrue(missingFromGlobe.isEmpty,
            "Countries in map but missing from globe: \(missingFromGlobe.sorted()). Regenerate globe.scn.")

        // Check for extra countries in globe
        let extraInGlobe = globeCountries.subtracting(expectedCountries)
        XCTAssertTrue(extraInGlobe.isEmpty,
            "Countries in globe but missing from map data: \(extraInGlobe.sorted())")
    }

    // Test point-in-polygon algorithm
    func testPointInPolygon() {
        // Simple square polygon: corners at (0,0), (10,0), (10,10), (0,10)
        let square: [[Double]] = [
            [0, 0],
            [10, 0],
            [10, 10],
            [0, 10],
            [0, 0]  // Closing point
        ]

        // Point inside
        XCTAssertTrue(isPointInPolygon(lon: 5, lat: 5, polygon: square), "Point (5,5) should be inside square")

        // Point outside
        XCTAssertFalse(isPointInPolygon(lon: 15, lat: 5, polygon: square), "Point (15,5) should be outside square")
        XCTAssertFalse(isPointInPolygon(lon: -5, lat: 5, polygon: square), "Point (-5,5) should be outside square")

        // Point on edge (may vary by implementation)
        // Points exactly on edges can be tricky with ray casting
    }

    // Test with a real country polygon (simplified USA bounding box)
    func testUSABoundingBox() {
        // Rough bounding box for continental USA
        let usaBounds: [[Double]] = [
            [-125, 24],  // SW corner
            [-66, 24],   // SE corner
            [-66, 49],   // NE corner
            [-125, 49],  // NW corner
            [-125, 24]   // Close
        ]

        // Point in middle of USA (roughly Kansas)
        XCTAssertTrue(isPointInPolygon(lon: -98, lat: 38, polygon: usaBounds), "Kansas should be in USA bounds")

        // Point in Europe
        XCTAssertFalse(isPointInPolygon(lon: 2, lat: 48, polygon: usaBounds), "Paris should not be in USA bounds")

        // Point in Pacific Ocean
        XCTAssertFalse(isPointInPolygon(lon: -150, lat: 30, polygon: usaBounds), "Pacific Ocean should not be in USA bounds")
    }

    // Test coordinate conversion (3D to lat/lon)
    func testCoordinateConversion() {
        // Test equator, prime meridian (0, 0)
        let (lat1, lon1) = sphereToLatLon(x: 1, y: 0, z: 0)
        XCTAssertEqual(lat1, 0, accuracy: 0.1, "Lat should be 0 at equator")
        XCTAssertEqual(lon1, 0, accuracy: 0.1, "Lon should be 0 at prime meridian")

        // Test north pole (0, 90)
        let (lat2, lon2) = sphereToLatLon(x: 0, y: 1, z: 0)
        XCTAssertEqual(lat2, 90, accuracy: 0.1, "Lat should be 90 at north pole")

        // Test south pole (0, -90)
        let (lat3, lon3) = sphereToLatLon(x: 0, y: -1, z: 0)
        XCTAssertEqual(lat3, -90, accuracy: 0.1, "Lat should be -90 at south pole")

        // Test point at lon=90 (east)
        let (lat4, lon4) = sphereToLatLon(x: 0, y: 0, z: 1)
        XCTAssertEqual(lat4, 0, accuracy: 0.1, "Lat should be 0")
        XCTAssertEqual(lon4, 90, accuracy: 0.1, "Lon should be 90 at z=1")
    }

    // Test GeoJSON loading
    func testGeoJSONLoading() {
        let countries = GeoJSONParser.loadCountries()
        XCTAssertGreaterThan(countries.count, 0, "Should load at least some countries")

        // Check that countries have valid data
        for country in countries.prefix(10) {
            XCTAssertFalse(country.name.isEmpty, "Country should have a name")
            if country.isPointCountry {
                XCTAssertNotNil(country.pointCoordinate, "Point country should have coordinates")
            } else {
                XCTAssertGreaterThan(country.polygons.count, 0, "Polygon country should have at least one polygon")
            }
        }
    }

    // Dataset uses current official country names; saved user data with the
    // old names is migrated on load by GlobeState
    func testRenamedCountriesUseOfficialNamesAndMigrate() {
        let names = Set(GeoJSONParser.loadCountries().map { $0.name })

        for (old, current) in GlobeState.renamedCountries {
            XCTAssertTrue(names.contains(current), "Dataset should use \(current)")
            XCTAssertFalse(names.contains(old), "Dataset should no longer contain \(old)")
        }

        XCTAssertEqual(
            GlobeState.migrateRenamedCountries(in: ["Turkey", "Cape Verde", "France"]),
            ["Türkiye", "Cabo Verde", "France"]
        )
        XCTAssertEqual(
            GlobeState.migrateRenamedCountries(inKeysOf: [
                "Turkey": ["Istanbul"],
                "Türkiye": ["Ankara"],
                "France": ["Paris"]
            ]),
            ["Türkiye": ["Istanbul", "Ankara"], "France": ["Paris"]]
        )
    }

    // Test finding country at known coordinates
    func testFindCountryAtCoordinates() {
        let countries = GeoJSONParser.loadCountries()

        // Test a few known locations
        let testCases: [(lat: Double, lon: Double, expectedCountry: String?)] = [
            (lat: 48.8566, lon: 2.3522, expectedCountry: "France"),      // Paris
            (lat: 51.5074, lon: -0.1278, expectedCountry: "United Kingdom"), // London (might be "England" depending on data)
            (lat: 35.6762, lon: 139.6503, expectedCountry: "Japan"),     // Tokyo
            (lat: 0, lon: 0, expectedCountry: nil),                       // Gulf of Guinea (ocean)
        ]

        for testCase in testCases {
            let found = findCountryAt(lat: testCase.lat, lon: testCase.lon, countries: countries)
            if let expected = testCase.expectedCountry {
                XCTAssertEqual(found, expected, "At (\(testCase.lat), \(testCase.lon)) expected \(expected) but got \(found ?? "nil")")
            } else {
                // Ocean - might or might not find a country depending on data precision
                print("At (\(testCase.lat), \(testCase.lon)) found: \(found ?? "nil")")
            }
        }
    }

    // MARK: - Tap ray → surface intersection (globe click accuracy)

    // latLonToSphere → sphereToLatLon must round-trip across the globe
    func testSphereToLatLonRoundTrip() {
        for lat in stride(from: -80.0, through: 80.0, by: 20.0) {
            for lon in stride(from: -170.0, through: 170.0, by: 30.0) {
                let v = PolygonTriangulator.latLonToSphere(lat: lat, lon: lon, radius: 1.0)
                let (outLat, outLon) = PolygonTriangulator.sphereToLatLon(
                    simd_double3(Double(v.x), Double(v.y), Double(v.z)))
                XCTAssertEqual(outLat, lat, accuracy: 0.001, "lat round trip at (\(lat), \(lon))")
                XCTAssertEqual(outLon, lon, accuracy: 0.001, "lon round trip at (\(lat), \(lon))")
            }
        }
    }

    // A ray aimed at a known surface point must recover exactly that point,
    // no matter how oblique the ray is (screen-edge taps at close zoom)
    func testTapRayRecoversExactSurfacePoint() {
        // Camera at the closest allowed zoom on the +X axis, which in the
        // latLonToSphere convention looks straight at lat/lon (0°, 0°)
        let cameraDistance = Double(GlobeState.minCameraDistance)
        let origin = simd_double3(cameraDistance, 0, 0)

        // Sample of surface targets from dead center out to very oblique
        let targets: [(lat: Double, lon: Double)] = [
            (0, 0),           // screen center
            (0.5, 0.5),       // near center
            (2.5, 4.0),       // Paris → Luxembourg scale offset
            (8, 10),          // strongly oblique
            (18, 12),         // near the visible limb at this zoom (~21.5° of ~24.6°)
        ]

        for target in targets {
            let surface = unitVector(lat: target.lat, lon: target.lon)
            guard let hit = PolygonTriangulator.raySphereSurfaceDirection(
                origin: origin, direction: surface - origin) else {
                XCTFail("Ray at (\(target.lat), \(target.lon)) should hit the sphere")
                continue
            }
            let (lat, lon) = PolygonTriangulator.sphereToLatLon(hit)
            XCTAssertEqual(lat, target.lat, accuracy: 1e-6, "lat at (\(target.lat), \(target.lon))")
            XCTAssertEqual(lon, target.lon, accuracy: 1e-6, "lon at (\(target.lat), \(target.lon))")
        }
    }

    // Documents the bug the analytic intersection fixes: SceneKit's hitTest
    // struck the atmosphere shell (radius 1.08) first, and normalizing that hit
    // point drags oblique taps toward the screen center by several degrees —
    // enough to turn an edge-of-screen tap on Luxembourg into a tap on France.
    func testAtmosphereShellHitSkewsObliqueTaps() {
        let cameraDistance = Double(GlobeState.minCameraDistance)
        let origin = simd_double3(cameraDistance, 0, 0)
        let target = unitVector(lat: 0, lon: 4)  // ~Luxembourg's offset from screen center

        let exact = PolygonTriangulator.raySphereSurfaceDirection(
            origin: origin, direction: target - origin)!
        let shell = PolygonTriangulator.raySphereSurfaceDirection(
            origin: origin, direction: target - origin, radius: 1.08)!

        XCTAssertLessThan(angleDegrees(exact, target), 1e-6,
                          "Analytic surface intersection should be exact")
        XCTAssertGreaterThan(angleDegrees(shell, target), 2,
                             "Shell hit should skew by degrees — the pre-fix misclick")
    }

    // Near-misses clamp to the limb so taps just off the globe still resolve;
    // wide misses and rays pointing away return nil
    func testRaySphereLimbClampAndMisses() {
        let origin = simd_double3(0, 0, 4)

        // Passes 1.02 from center: within limb slack → clamped to closest approach
        let nearMiss = PolygonTriangulator.raySphereSurfaceDirection(
            origin: origin, direction: simd_double3(1.02, 0, -4))
        XCTAssertNotNil(nearMiss, "Near-miss within limb slack should clamp to the limb")
        if let nearMiss = nearMiss {
            XCTAssertEqual(simd_length(nearMiss), 1.0, accuracy: 1e-9, "Clamped point should be on the unit sphere")
        }

        // Passes 1.2 from center: outside slack → miss
        XCTAssertNil(PolygonTriangulator.raySphereSurfaceDirection(
            origin: origin, direction: simd_double3(1.2, 0, -4)),
            "Wide miss should not resolve to a surface point")

        // Sphere behind the ray → miss
        XCTAssertNil(PolygonTriangulator.raySphereSurfaceDirection(
            origin: origin, direction: simd_double3(0, 0, 1)),
            "Ray pointing away from the globe should not hit")
    }

    /// The zoom-out limit, which is a two-platform constant: Android's
    /// `GlobeCameraTest` asserts the same 6.0 and that a pinch bottoms out there.
    @MainActor
    func testZoomOutStopsWhereAndroidDoes() {
        XCTAssertEqual(GlobeState.maxCameraDistance, 6.0, accuracy: 0,
                       "Zoom-out limit changed; Android clamps to the same value")

        let state = GlobeState()
        for _ in 0..<50 { state.zoomOut() }
        XCTAssertEqual(state.zoomLevel, GlobeState.maxCameraDistance, accuracy: 1e-6,
                       "Repeated zoom-out should settle on the limit, not run past it")

        for _ in 0..<50 { state.zoomIn() }
        XCTAssertEqual(state.zoomLevel, GlobeState.minCameraDistance, accuracy: 1e-6,
                       "Repeated zoom-in should settle on the floor")

        // Far enough out that the whole globe is still comfortably in frame: its
        // silhouette spans about 40% of a 45-degree viewport at the limit.
        let globeDegrees = 2 * asin(1.0 / Double(GlobeState.maxCameraDistance)) * 180 / .pi
        XCTAssertGreaterThan(globeDegrees / 45.0, 0.35,
                             "At the zoom-out limit the globe should still fill much of the screen")
    }

    private func unitVector(lat: Double, lon: Double) -> simd_double3 {
        let latRad = lat * .pi / 180
        let lonRad = -lon * .pi / 180
        return simd_double3(cos(latRad) * cos(lonRad), sin(latRad), cos(latRad) * sin(lonRad))
    }

    private func angleDegrees(_ a: simd_double3, _ b: simd_double3) -> Double {
        let cosine = simd_dot(simd_normalize(a), simd_normalize(b))
        return acos(min(1, max(-1, cosine))) * 180 / .pi
    }

    // Helper functions for tests

    func isPointInPolygon(lon: Double, lat: Double, polygon: [[Double]]) -> Bool {
        var inside = false
        var j = polygon.count - 1

        for i in 0..<polygon.count {
            guard polygon[i].count >= 2 && polygon[j].count >= 2 else {
                j = i
                continue
            }
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

    func sphereToLatLon(x: Float, y: Float, z: Float) -> (lat: Double, lon: Double) {
        let lat = Double(asin(y)) * 180.0 / .pi
        let lon = Double(atan2(z, x)) * 180.0 / .pi
        return (lat, lon)
    }

    func findCountryAt(lat: Double, lon: Double, countries: [GeoJSONCountry]) -> String? {
        for country in countries {
            for polygon in country.polygons {
                if isPointInPolygon(lon: lon, lat: lat, polygon: polygon) {
                    return country.name
                }
            }
        }
        return nil
    }
}
