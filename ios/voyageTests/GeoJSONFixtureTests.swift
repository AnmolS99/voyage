import XCTest
@testable import voyage

/// Locks `GeoJSONParser` to `shared/fixtures/expected_countries.json`.
///
/// The Android suite (`GeoJsonParserTest`) asserts the same fixture, so the two
/// hand-written parsers cannot drift apart: a change to `world.geojson` or to
/// either parser that isn't reflected on both platforms fails here or there.
///
/// Regenerate the fixture with `python3 scripts/generate_country_fixture.py`
/// after running `scripts/update_geometry.sh`, and review its diff.
final class GeoJSONFixtureTests: XCTestCase {

    private struct ExpectedCapital: Decodable {
        let name: String
        let lat: Double
        let lon: Double
    }

    private struct ExpectedPoint: Decodable {
        let lat: Double
        let lon: Double
    }

    private struct ExpectedCountry: Decodable {
        let iso: String?
        let name: String
        let continent: String?
        let capital: ExpectedCapital?
        let isPointCountry: Bool
        let point: ExpectedPoint?
        let polygonPointCounts: [Int]
        let holePointCounts: [Int]
        let bbox: [Double]?
    }

    private struct ExpectedCountries: Decodable {
        let countryCount: Int
        let totalCoordinateCount: Int
        let countries: [ExpectedCountry]
    }

    private static let tolerance = 1e-9

    private static let expected: ExpectedCountries = {
        guard let url = Bundle(for: GeoJSONFixtureTests.self)
            .url(forResource: "expected_countries", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let fixture = try? JSONDecoder().decode(ExpectedCountries.self, from: data) else {
            fatalError("expected_countries.json is missing from the test bundle")
        }
        return fixture
    }()

    private static let countries = CountryDataCache.shared.countries

    private var pairs: [(ExpectedCountry, GeoJSONCountry)] {
        Array(zip(Self.expected.countries, Self.countries))
    }

    func testParsesEveryCountryInFixtureOrder() {
        XCTAssertEqual(Self.countries.count, Self.expected.countryCount)
        XCTAssertEqual(Self.countries.map(\.name), Self.expected.countries.map(\.name))
    }

    func testCountryIdentityMatchesFixture() {
        for (want, got) in pairs {
            XCTAssertEqual(got.flagCode, want.iso, want.name)
            XCTAssertEqual(got.continent, want.continent, want.name)
            XCTAssertEqual(got.isPointCountry, want.isPointCountry, want.name)
        }
    }

    func testCapitalsMatchFixture() {
        for (want, got) in pairs {
            guard let capital = want.capital else {
                XCTAssertNil(got.capital, want.name)
                continue
            }
            XCTAssertEqual(got.capital?.name, capital.name, want.name)
            XCTAssertEqual(got.capital?.lat ?? .nan, capital.lat, accuracy: Self.tolerance, want.name)
            XCTAssertEqual(got.capital?.lon ?? .nan, capital.lon, accuracy: Self.tolerance, want.name)
        }
    }

    func testPointCountriesCarryTheirCoordinate() {
        for (want, got) in pairs {
            guard let point = want.point else {
                XCTAssertNil(got.pointCoordinate, want.name)
                continue
            }
            XCTAssertEqual(got.pointCoordinate?.lat ?? .nan, point.lat, accuracy: Self.tolerance, want.name)
            XCTAssertEqual(got.pointCoordinate?.lon ?? .nan, point.lon, accuracy: Self.tolerance, want.name)
            XCTAssertTrue(got.polygons.isEmpty && got.holes.isEmpty, want.name)
        }
    }

    func testRingAndPointCountsMatchFixture() {
        for (want, got) in pairs {
            XCTAssertEqual(got.polygons.map(\.count), want.polygonPointCounts, want.name)
            XCTAssertEqual(got.holes.map(\.count), want.holePointCounts, want.name)
        }

        let total = Self.countries.reduce(0) { running, country in
            running + (country.polygons + country.holes).reduce(0) { $0 + $1.count }
        }
        XCTAssertEqual(total, Self.expected.totalCoordinateCount)
    }

    func testCoordinatesMatchFixtureBoundingBoxes() {
        for (want, got) in pairs {
            guard let bbox = want.bbox else { continue }
            let rings = got.polygons + got.holes
            let lons = rings.flatMap { $0.map { $0[0] } }
            let lats = rings.flatMap { $0.map { $0[1] } }
            XCTAssertEqual(lons.min() ?? .nan, bbox[0], accuracy: Self.tolerance, "\(want.name) minLon")
            XCTAssertEqual(lats.min() ?? .nan, bbox[1], accuracy: Self.tolerance, "\(want.name) minLat")
            XCTAssertEqual(lons.max() ?? .nan, bbox[2], accuracy: Self.tolerance, "\(want.name) maxLon")
            XCTAssertEqual(lats.max() ?? .nan, bbox[3], accuracy: Self.tolerance, "\(want.name) maxLat")
        }
    }
}
