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
                visitedCountries: Array(visitedInContinent),
                remainingCountries: countries.filter { !visitedInContinent.contains($0) }
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
                visitedCountries: Array(visitedInContinent),
                remainingCountries: countries.filter { !visitedInContinent.contains($0) }
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

    // MARK: - Globetrotter Achievement Tests

    func testWorldTravelerAchievementNotCompletedWithFewCountries() {
        let visited: Set<String> = Set(Array(Continent.europe.countries.prefix(3)))
        let totalCountries = 195

        let achievement = Achievement.forTesting(
            name: "Globetrotter",
            medal: "🌍",
            current: visited.count,
            total: totalCountries
        )

        XCTAssertFalse(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 3)
    }

    func testWorldTravelerAchievementCompletedWithAllCountries() {
        let totalCountries = 195

        let achievement = Achievement.forTesting(
            name: "Globetrotter",
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
            let achievement = Achievement.forTesting(
                name: "Globetrotter",
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

    // MARK: - Wonders of the World Achievement Tests

    private func makeWondersAchievement(checked: [String: Set<String>]) -> Achievement {
        Achievement(
            name: "Wonders of the World",
            medal: "⭐️",
            visitedCountries: WondersOfTheWorld.visited(from: checked),
            remainingCountries: WondersOfTheWorld.remaining(from: checked),
            itemLabel: "wonders"
        )
    }

    func testWondersAchievementCompletedWhenAllChecked() {
        var checked: [String: Set<String>] = [:]
        for wonder in WondersOfTheWorld.wonders {
            checked[wonder.country, default: []].insert(wonder.attraction)
        }

        let achievement = makeWondersAchievement(checked: checked)
        XCTAssertEqual(achievement.total, 8)
        XCTAssertTrue(achievement.isCompleted,
            "Checking all eight wonders should complete the achievement")
        XCTAssertEqual(achievement.percentage, 100)
    }

    func testWondersAchievementPartialProgress() {
        let checked: [String: Set<String>] = [
            "Peru": ["Machu Picchu"],
            "Italy": ["Colosseum"],
        ]

        let achievement = makeWondersAchievement(checked: checked)
        XCTAssertEqual(achievement.current, 2)
        XCTAssertEqual(achievement.total, 8)
        XCTAssertFalse(achievement.isCompleted)
    }

    func testWondersIncludePyramidsOfGiza() {
        let achievement = makeWondersAchievement(checked: ["Egypt": ["Pyramids of Giza"]])
        XCTAssertEqual(achievement.visitedCountries, ["Pyramids of Giza"])
        XCTAssertEqual(achievement.current, 1)
    }

    func testWondersIgnoresOtherCheckedAttractions() {
        // A non-wonder attraction in a wonder country must not count
        let checked: [String: Set<String>] = [
            "Italy": ["Pompeii"],
            "China": ["Great Wall of China"],
        ]

        let achievement = makeWondersAchievement(checked: checked)
        XCTAssertEqual(achievement.current, 1)
        XCTAssertEqual(achievement.visitedCountries, ["Great Wall of China"])
    }

    func testWondersMatchCountryHighlightsData() {
        // Each wonder must reference a real country and an attraction that exists
        // in that country's checklist, otherwise it can never be checked off.
        let countries = GeoJSONParser.loadCountries()
        let highlights = CountryHighlightsParser.loadHighlights()

        for wonder in WondersOfTheWorld.wonders {
            guard let country = countries.first(where: { $0.name == wonder.country }),
                  let code = country.flagCode else {
                XCTFail("Wonder country \(wonder.country) not found in world.geojson")
                continue
            }

            let attractions = highlights[code]?.attractions ?? []
            XCTAssertTrue(attractions.contains(wonder.attraction),
                "\(wonder.attraction) missing from \(wonder.country)'s attractions in country_highlights.json")
        }
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
            visitedCountries: Array(europeVisited),
            remainingCountries: Continent.europe.countries.filter { !europeVisited.contains($0) }
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
            visitedCountries: Array(europeVisited),
            remainingCountries: Continent.europe.countries.filter { !europeVisited.contains($0) }
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
            visitedCountries: Array(asiaVisited),
            remainingCountries: Continent.asia.countries.filter { !asiaVisited.contains($0) }
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
