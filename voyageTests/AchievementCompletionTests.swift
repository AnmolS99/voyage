import XCTest
@testable import voyage

final class AchievementCompletionTests: XCTestCase {

    // MARK: - Continent Achievement Completion Tests

    func testContinentAchievementCompletedWhenAllCountriesVisited() {
        // Test with a continent that has countries
        for continent in Continent.allCases where continent != .antarctica {
            let countries = continent.countries
            guard !countries.isEmpty else { continue }

            let visited = countries
            let visitedInContinent = ContinentData.visitedCountries(in: continent, from: visited)

            let achievement = Achievement(
                name: "Explorer of \(continent.rawValue)",
                medal: continent.medal,
                current: visitedInContinent.count,
                total: countries.count
            )

            XCTAssertTrue(achievement.isCompleted,
                "Visiting all countries in \(continent.rawValue) should complete the achievement")
            XCTAssertEqual(achievement.percentage, 100)
        }
    }

    func testContinentAchievementPartialProgress() {
        // Test partial progress for each continent
        for continent in Continent.allCases where continent != .antarctica {
            let countries = Array(continent.countries)
            guard countries.count >= 4 else { continue }

            // Visit only first 2 countries
            let partialVisited = Set(countries.prefix(2))
            let visitedInContinent = ContinentData.visitedCountries(in: continent, from: partialVisited)

            let achievement = Achievement(
                name: "Explorer of \(continent.rawValue)",
                medal: continent.medal,
                current: visitedInContinent.count,
                total: countries.count
            )

            XCTAssertFalse(achievement.isCompleted,
                "Visiting only 2 countries in \(continent.rawValue) should not complete the achievement")
            XCTAssertEqual(achievement.current, 2)
        }
    }

    func testAchievementFromGlobeAndMapVisits() {
        // Simulate visiting countries via both globe and map interactions
        // Both views use the same visited set, so this tests that the data is consistent

        let europeCountries = Array(Continent.europe.countries.prefix(3))
        let asiaCountries = Array(Continent.asia.countries.prefix(3))

        guard europeCountries.count >= 3, asiaCountries.count >= 3 else {
            XCTFail("Not enough countries loaded")
            return
        }

        // Simulate: some visited on "globe", some on "map"
        let visitedViaGlobe: Set<String> = Set(europeCountries.prefix(2))
        let visitedViaMap: Set<String> = Set(asiaCountries.prefix(2))
        let allVisited = visitedViaGlobe.union(visitedViaMap)

        let europeVisited = ContinentData.visitedCountries(in: .europe, from: allVisited)
        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: allVisited)

        XCTAssertEqual(europeVisited.count, 2, "Should have 2 European countries")
        XCTAssertEqual(asiaVisited.count, 2, "Should have 2 Asian countries")
    }

    // MARK: - World Traveler Achievement Tests

    func testWorldTravelerAchievementNotCompletedWithFewCountries() {
        let visited: Set<String> = Set(Array(Continent.europe.countries.prefix(3)))
        let totalCountries = 195

        let achievement = Achievement(
            name: "World Traveler",
            medal: "🌍",
            current: visited.count,
            total: totalCountries
        )

        XCTAssertFalse(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 3)
    }

    func testWorldTravelerAchievementCompletedWithAllCountries() {
        let totalCountries = 195

        let achievement = Achievement(
            name: "World Traveler",
            medal: "🌍",
            current: 195,
            total: totalCountries
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.percentage, 100)
    }

    func testWorldTravelerProgressiveAchievement() {
        let totalCountries = 195

        let stages = [10, 50, 100, 150, 195]
        for count in stages {
            let achievement = Achievement(
                name: "World Traveler",
                medal: "🌍",
                current: count,
                total: totalCountries
            )

            if count == 195 {
                XCTAssertTrue(achievement.isCompleted, "Should be completed at \(count) countries")
                XCTAssertEqual(achievement.percentage, 100)
            } else {
                XCTAssertFalse(achievement.isCompleted, "Should not be completed at \(count) countries")
            }
        }
    }

    func testWorldTravelerWithAllLoadedCountries() {
        // Collect all countries from all continents (from actual data)
        var allCountries = Set<String>()
        for continent in Continent.allCases {
            allCountries = allCountries.union(continent.countries)
        }

        // Should have a reasonable number of countries
        XCTAssertGreaterThan(allCountries.count, 150,
            "Should have most countries defined across all continents")
    }

    // MARK: - Cross-view Consistency Tests

    func testAchievementConsistencyBetweenViews() {
        // The globe and map both use GlobeState.visitedCountries
        // This test verifies that achievements calculated from the same visited set
        // produce consistent results

        let europeCountries = Array(Continent.europe.countries.prefix(3))
        let asiaCountries = Array(Continent.asia.countries.prefix(2))
        let southAmericaCountries = Array(Continent.southAmerica.countries.prefix(2))

        guard europeCountries.count >= 3, asiaCountries.count >= 2, southAmericaCountries.count >= 2 else {
            XCTFail("Not enough countries loaded")
            return
        }

        let visited: Set<String> = Set(europeCountries + asiaCountries + southAmericaCountries)

        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        let europeAchievement = Achievement(
            name: "Explorer of Europe",
            medal: Continent.europe.medal,
            current: europeVisited.count,
            total: Continent.europe.countries.count
        )

        XCTAssertEqual(europeVisited.count, 3, "Should have 3 European countries")
        XCTAssertEqual(europeAchievement.current, 3)

        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertEqual(asiaVisited.count, 2, "Should have 2 Asian countries")

        let southAmericaVisited = ContinentData.visitedCountries(in: .southAmerica, from: visited)
        XCTAssertEqual(southAmericaVisited.count, 2, "Should have 2 South American countries")

        let totalVisited = europeVisited.count + asiaVisited.count + southAmericaVisited.count
        XCTAssertEqual(totalVisited, 7, "Total visited across continents should be 7")
    }

    func testVisitingCountryOnGlobeThenCheckingOnMap() {
        // Simulate: user visits a country on globe, then switches to map
        guard let firstEuropeCountry = Continent.europe.countries.first else {
            XCTFail("No European countries loaded")
            return
        }

        var visited: Set<String> = []
        visited.insert(firstEuropeCountry)

        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertTrue(europeVisited.contains(firstEuropeCountry))

        let achievement = Achievement(
            name: "Explorer of Europe",
            medal: Continent.europe.medal,
            current: europeVisited.count,
            total: Continent.europe.countries.count
        )

        XCTAssertEqual(achievement.current, 1)
        XCTAssertFalse(achievement.isCompleted)
    }

    func testVisitingCountryOnMapThenCheckingOnGlobe() {
        // Simulate: user visits a country on map, then switches to globe
        guard let firstAsiaCountry = Continent.asia.countries.first else {
            XCTFail("No Asian countries loaded")
            return
        }

        var visited: Set<String> = []
        visited.insert(firstAsiaCountry)

        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertTrue(asiaVisited.contains(firstAsiaCountry))

        let achievement = Achievement(
            name: "Explorer of Asia",
            medal: Continent.asia.medal,
            current: asiaVisited.count,
            total: Continent.asia.countries.count
        )

        XCTAssertEqual(achievement.current, 1)
        XCTAssertFalse(achievement.isCompleted)
    }

    // MARK: - Data Source Consistency Tests

    func testCountriesFromGeoJSONMatchContinentData() {
        // Verify that countries loaded from GeoJSON are properly mapped to continents
        let geoJSONCountries = GeoJSONParser.loadCountries()
        var mismatches: [String] = []

        for country in geoJSONCountries {
            guard let geoJSONContinent = country.continent,
                  let continent = Continent(rawContinent: geoJSONContinent) else {
                continue
            }

            // Verify the country appears in the correct continent in ContinentData
            let continentCountries = continent.countries
            if !continentCountries.contains(country.name) {
                mismatches.append("\(country.name) has continent \(geoJSONContinent) in GeoJSON but not in ContinentData")
            }
        }

        XCTAssertTrue(mismatches.isEmpty,
            "GeoJSON countries should match ContinentData: \(mismatches)")
    }
}
