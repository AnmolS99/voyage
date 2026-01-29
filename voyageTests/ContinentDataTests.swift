import XCTest
@testable import voyage

final class ContinentDataTests: XCTestCase {

    // MARK: - visitedCountries Tests

    func testVisitedCountriesInEurope() {
        let visited: Set<String> = ["France", "Germany", "Japan", "Brazil"]
        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertEqual(europeVisited, ["France", "Germany"], "Should find France and Germany in Europe")
    }

    func testVisitedCountriesInAsia() {
        let visited: Set<String> = ["France", "Germany", "Japan", "Brazil"]
        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertEqual(asiaVisited, ["Japan"], "Should find Japan in Asia")
    }

    func testVisitedCountriesInSouthAmerica() {
        let visited: Set<String> = ["France", "Germany", "Japan", "Brazil"]
        let southAmericaVisited = ContinentData.visitedCountries(in: .southAmerica, from: visited)
        XCTAssertEqual(southAmericaVisited, ["Brazil"], "Should find Brazil in South America")
    }

    func testVisitedCountriesInAfricaWhenNoneVisited() {
        let visited: Set<String> = ["France", "Germany", "Japan", "Brazil"]
        let africaVisited = ContinentData.visitedCountries(in: .africa, from: visited)
        XCTAssertTrue(africaVisited.isEmpty, "Should find no countries in Africa")
    }

    func testVisitedCountriesWithEmptySet() {
        let visited: Set<String> = []
        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertTrue(europeVisited.isEmpty, "Should find no countries when none visited")
    }

    // MARK: - continent(for:) Tests

    func testContinentForFrance() {
        XCTAssertEqual(ContinentData.continent(for: "France"), .europe)
    }

    func testContinentForJapan() {
        XCTAssertEqual(ContinentData.continent(for: "Japan"), .asia)
    }

    func testContinentForBrazil() {
        XCTAssertEqual(ContinentData.continent(for: "Brazil"), .southAmerica)
    }

    func testContinentForNigeria() {
        XCTAssertEqual(ContinentData.continent(for: "Nigeria"), .africa)
    }

    func testContinentForAustralia() {
        XCTAssertEqual(ContinentData.continent(for: "Australia"), .oceania)
    }

    func testContinentForCanada() {
        XCTAssertEqual(ContinentData.continent(for: "Canada"), .northAmerica)
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
        XCTAssertTrue(Continent.antarctica.countries.isEmpty, "Antarctica should have no countries")
    }

    func testEuropeCountryCount() {
        XCTAssertEqual(Continent.europe.countries.count, 44, "Europe should have 44 countries")
    }

    func testAsiaCountryCount() {
        XCTAssertEqual(Continent.asia.countries.count, 50, "Asia should have 50 countries")
    }

    func testAfricaCountryCount() {
        XCTAssertEqual(Continent.africa.countries.count, 54, "Africa should have 54 countries")
    }

    func testNorthAmericaCountryCount() {
        XCTAssertEqual(Continent.northAmerica.countries.count, 23, "North America should have 23 countries")
    }

    func testSouthAmericaCountryCount() {
        XCTAssertEqual(Continent.southAmerica.countries.count, 12, "South America should have 12 countries")
    }

    func testOceaniaCountryCount() {
        XCTAssertEqual(Continent.oceania.countries.count, 14, "Oceania should have 14 countries")
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
}
