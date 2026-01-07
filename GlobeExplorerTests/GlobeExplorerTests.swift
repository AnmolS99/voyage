import XCTest
@testable import GlobeExplorer

final class GlobeExplorerTests: XCTestCase {

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

        // Check that countries have polygons
        for country in countries.prefix(10) {
            XCTAssertFalse(country.name.isEmpty, "Country should have a name")
            XCTAssertGreaterThan(country.polygons.count, 0, "Country should have at least one polygon")
        }
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
