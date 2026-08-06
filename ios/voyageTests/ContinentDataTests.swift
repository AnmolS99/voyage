import XCTest
@testable import voyage

final class ContinentDataTests: XCTestCase {

    // MARK: - visitedCountries Tests

    func testVisitedCountriesFiltersCorrectly() {
        // Get actual countries from each continent
        let europeCountries = Array(Continent.europe.countries.prefix(2))
        let asiaCountries = Array(Continent.asia.countries.prefix(1))

        guard europeCountries.count >= 2, asiaCountries.count >= 1 else {
            XCTFail("Not enough countries loaded")
            return
        }

        let visited: Set<String> = Set(europeCountries + asiaCountries)

        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertEqual(europeVisited.count, 2, "Should find 2 European countries")

        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertEqual(asiaVisited.count, 1, "Should find 1 Asian country")

        let africaVisited = ContinentData.visitedCountries(in: .africa, from: visited)
        XCTAssertTrue(africaVisited.isEmpty, "Should find no African countries")
    }

    func testVisitedCountriesWithEmptySet() {
        let visited: Set<String> = []
        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertTrue(europeVisited.isEmpty, "Should find no countries when none visited")
    }

    // MARK: - continent(for:) Tests

    func testContinentForCountryReturnsCorrectContinent() {
        // Test that continent lookup works for countries in each continent
        for continent in Continent.allCases where continent != .antarctica {
            let countries = continent.countries
            guard let firstCountry = countries.first else {
                continue
            }
            XCTAssertEqual(ContinentData.continent(for: firstCountry), continent,
                "\(firstCountry) should be in \(continent.rawValue)")
        }
    }

    func testContinentForNonexistentCountry() {
        XCTAssertNil(ContinentData.continent(for: "NonexistentCountry"))
    }

    // MARK: - Continent Countries Tests

    func testAllContinentsHaveCountriesExceptAntarctica() {
        for continent in Continent.allCases where continent != .antarctica {
            XCTAssertGreaterThan(continent.countries.count, 0,
                "\(continent.rawValue) should have at least one country")
        }
    }

    func testAntarcticaHasNoCountries() {
        // Antarctica may have 0 or few entries depending on GeoJSON
        XCTAssertLessThanOrEqual(Continent.antarctica.countries.count, 2,
            "Antarctica should have very few or no countries")
    }

    func testContinentsHaveReasonableCountryCounts() {
        // These are approximate counts - the actual values come from GeoJSON
        XCTAssertGreaterThan(Continent.europe.countries.count, 30, "Europe should have 30+ countries")
        XCTAssertGreaterThan(Continent.asia.countries.count, 35, "Asia should have 35+ countries")
        XCTAssertGreaterThan(Continent.africa.countries.count, 40, "Africa should have 40+ countries")
        XCTAssertGreaterThan(Continent.northAmerica.countries.count, 15, "North America should have 15+ countries")
        XCTAssertGreaterThan(Continent.southAmerica.countries.count, 10, "South America should have 10+ countries")
        XCTAssertGreaterThan(Continent.oceania.countries.count, 10, "Oceania should have 10+ countries")
    }

    // MARK: - Continent Medal Tests

    func testAfricaMedal() {
        XCTAssertEqual(Continent.africa.medal, "🦁")
    }

    func testAsiaMedal() {
        XCTAssertEqual(Continent.asia.medal, "🐉")
    }

    func testEuropeMedal() {
        XCTAssertEqual(Continent.europe.medal, "🏰")
    }

    func testNorthAmericaMedal() {
        XCTAssertEqual(Continent.northAmerica.medal, "🦅")
    }

    func testSouthAmericaMedal() {
        XCTAssertEqual(Continent.southAmerica.medal, "🦜")
    }

    func testOceaniaMedal() {
        XCTAssertEqual(Continent.oceania.medal, "🐨")
    }

    func testAntarcticaMedal() {
        XCTAssertEqual(Continent.antarctica.medal, "🐧")
    }

    // MARK: - Continent rawValue Tests

    func testContinentRawValues() {
        XCTAssertEqual(Continent.africa.rawValue, "Africa")
        XCTAssertEqual(Continent.asia.rawValue, "Asia")
        XCTAssertEqual(Continent.europe.rawValue, "Europe")
        XCTAssertEqual(Continent.northAmerica.rawValue, "North America")
        XCTAssertEqual(Continent.southAmerica.rawValue, "South America")
        XCTAssertEqual(Continent.oceania.rawValue, "Oceania")
        XCTAssertEqual(Continent.antarctica.rawValue, "Antarctica")
    }

    // MARK: - Data Consistency Tests

    func testAllGeoJSONCountriesHaveContinents() {
        let countries = GeoJSONParser.loadCountries()
        var countriesWithoutContinent: [String] = []

        for country in countries {
            if country.continent == nil {
                countriesWithoutContinent.append(country.name)
            }
        }

        XCTAssertTrue(countriesWithoutContinent.isEmpty,
            "All GeoJSON countries should have a continent. Missing: \(countriesWithoutContinent)")
    }

    func testNoCountryInMultipleContinents() {
        var countryToContinent: [String: Continent] = [:]
        var duplicates: [String] = []

        for continent in Continent.allCases {
            for country in continent.countries {
                if let existingContinent = countryToContinent[country] {
                    if existingContinent != continent {
                        duplicates.append("\(country) in both \(existingContinent.rawValue) and \(continent.rawValue)")
                    }
                } else {
                    countryToContinent[country] = continent
                }
            }
        }

        XCTAssertTrue(duplicates.isEmpty,
            "Countries should not appear in multiple continents: \(duplicates)")
    }
}
