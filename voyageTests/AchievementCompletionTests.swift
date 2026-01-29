import XCTest
@testable import voyage

final class AchievementCompletionTests: XCTestCase {

    // MARK: - Europe Achievement Tests

    func testEuropeAchievementCompletedWhenAllCountriesVisited() {
        let europeCountries = Continent.europe.countries
        let visited = europeCountries
        let visitedInEurope = ContinentData.visitedCountries(in: .europe, from: visited)

        let achievement = Achievement(
            name: "Explorer of Europe",
            medal: Continent.europe.medal,
            current: visitedInEurope.count,
            total: europeCountries.count
        )

        XCTAssertTrue(achievement.isCompleted, "Visiting all European countries should complete the achievement")
        XCTAssertEqual(achievement.percentage, 100)
    }

    func testEuropeAchievementPartialProgress() {
        let visited: Set<String> = ["France", "Germany", "Italy", "Spain", "Portugal"]
        let visitedInEurope = ContinentData.visitedCountries(in: .europe, from: visited)

        let achievement = Achievement(
            name: "Explorer of Europe",
            medal: Continent.europe.medal,
            current: visitedInEurope.count,
            total: Continent.europe.countries.count
        )

        XCTAssertFalse(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 5)
    }

    // MARK: - Africa Achievement Tests

    func testAfricaAchievementCompletedWhenAllCountriesVisited() {
        let africaCountries = Continent.africa.countries
        let visited = africaCountries
        let visitedInAfrica = ContinentData.visitedCountries(in: .africa, from: visited)

        let achievement = Achievement(
            name: "Explorer of Africa",
            medal: Continent.africa.medal,
            current: visitedInAfrica.count,
            total: africaCountries.count
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.percentage, 100)
    }

    func testAfricaAchievementPartialProgress() {
        let africaCountries = Continent.africa.countries
        let halfOfAfrica = Set(Array(africaCountries).prefix(africaCountries.count / 2))
        let visitedInAfrica = ContinentData.visitedCountries(in: .africa, from: halfOfAfrica)

        let achievement = Achievement(
            name: "Explorer of Africa",
            medal: Continent.africa.medal,
            current: visitedInAfrica.count,
            total: africaCountries.count
        )

        XCTAssertFalse(achievement.isCompleted)
        XCTAssertGreaterThan(achievement.percentage, 40)
        XCTAssertLessThan(achievement.percentage, 60)
    }

    // MARK: - Asia Achievement Tests

    func testAsiaAchievementCompletedWhenAllCountriesVisited() {
        let asiaCountries = Continent.asia.countries
        let visited = asiaCountries
        let visitedInAsia = ContinentData.visitedCountries(in: .asia, from: visited)

        let achievement = Achievement(
            name: "Explorer of Asia",
            medal: Continent.asia.medal,
            current: visitedInAsia.count,
            total: asiaCountries.count
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.percentage, 100)
    }

    func testAsiaAchievementFromGlobeAndMapVisits() {
        // Simulate visiting countries via both globe and map interactions
        let visitedViaGlobe: Set<String> = ["Japan", "China", "India"]
        let visitedViaMap: Set<String> = ["South Korea", "Thailand", "Vietnam"]
        let allVisited = visitedViaGlobe.union(visitedViaMap)

        let visitedInAsia = ContinentData.visitedCountries(in: .asia, from: allVisited)

        XCTAssertEqual(visitedInAsia.count, 6, "Should have 6 Asian countries visited")

        let achievement = Achievement(
            name: "Explorer of Asia",
            medal: Continent.asia.medal,
            current: visitedInAsia.count,
            total: Continent.asia.countries.count
        )

        XCTAssertFalse(achievement.isCompleted, "6 countries should not complete Asia achievement")
        XCTAssertGreaterThan(achievement.current, 0)
    }

    // MARK: - Oceania Achievement Tests

    func testOceaniaAchievementCompletedWhenAllCountriesVisited() {
        let oceaniaCountries = Continent.oceania.countries
        let visited = oceaniaCountries
        let visitedInOceania = ContinentData.visitedCountries(in: .oceania, from: visited)

        let achievement = Achievement(
            name: "Explorer of Oceania",
            medal: Continent.oceania.medal,
            current: visitedInOceania.count,
            total: oceaniaCountries.count
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.percentage, 100)
        XCTAssertEqual(visitedInOceania.count, oceaniaCountries.count)
    }

    // MARK: - South America Achievement Tests

    func testSouthAmericaAchievementCompletedWhenAllCountriesVisited() {
        let southAmericaCountries = Continent.southAmerica.countries
        let visited = southAmericaCountries
        let visitedInSouthAmerica = ContinentData.visitedCountries(in: .southAmerica, from: visited)

        let achievement = Achievement(
            name: "Explorer of South America",
            medal: Continent.southAmerica.medal,
            current: visitedInSouthAmerica.count,
            total: southAmericaCountries.count
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 12, "South America should have 12 countries")
    }

    // MARK: - North America Achievement Tests

    func testNorthAmericaAchievementCompletedWhenAllCountriesVisited() {
        let northAmericaCountries = Continent.northAmerica.countries
        let visited = northAmericaCountries
        let visitedInNorthAmerica = ContinentData.visitedCountries(in: .northAmerica, from: visited)

        let achievement = Achievement(
            name: "Explorer of North America",
            medal: Continent.northAmerica.medal,
            current: visitedInNorthAmerica.count,
            total: northAmericaCountries.count
        )

        XCTAssertTrue(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 23, "North America should have 23 countries")
    }

    // MARK: - World Traveler Achievement Tests

    func testWorldTravelerAchievementNotCompletedWithFewCountries() {
        let visited: Set<String> = ["France", "Germany", "Japan"]
        let totalCountries = 195

        let achievement = Achievement(
            name: "World Traveler",
            medal: "🌍",
            current: visited.count,
            total: totalCountries
        )

        XCTAssertFalse(achievement.isCompleted)
        XCTAssertEqual(achievement.current, 3)
        XCTAssertEqual(achievement.percentage, 1) // 3/195 ≈ 1.5% -> 1%
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

        // Test progressive visiting
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

    func testWorldTravelerWithAllContinentCountries() {
        // Collect all countries from all continents
        var allCountries = Set<String>()
        for continent in Continent.allCases {
            allCountries = allCountries.union(continent.countries)
        }

        let totalCountries = 195

        let achievement = Achievement(
            name: "World Traveler",
            medal: "🌍",
            current: allCountries.count,
            total: totalCountries
        )

        // Note: The actual count may differ from 195 if ContinentData doesn't have all countries
        XCTAssertGreaterThan(achievement.current, 150, "Should have most countries defined in ContinentData")
    }

    // MARK: - Cross-view Consistency Tests (Globe and Map share same state)

    func testAchievementConsistencyBetweenViews() {
        // The globe and map both use GlobeState.visitedCountries
        // This test verifies that achievements calculated from the same visited set
        // produce consistent results regardless of which view triggered the visit

        let visited: Set<String> = [
            "France", "Germany", "Italy",  // Europe
            "Japan", "China",               // Asia
            "Brazil", "Argentina"           // South America
        ]

        // Calculate Europe achievement
        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        let europeAchievement = Achievement(
            name: "Explorer of Europe",
            medal: Continent.europe.medal,
            current: europeVisited.count,
            total: Continent.europe.countries.count
        )

        XCTAssertEqual(europeVisited.count, 3, "Should have 3 European countries")
        XCTAssertEqual(europeAchievement.current, 3)

        // Calculate Asia achievement
        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertEqual(asiaVisited.count, 2, "Should have 2 Asian countries")

        // Calculate South America achievement
        let southAmericaVisited = ContinentData.visitedCountries(in: .southAmerica, from: visited)
        XCTAssertEqual(southAmericaVisited.count, 2, "Should have 2 South American countries")

        // Total should match
        let totalVisited = europeVisited.count + asiaVisited.count + southAmericaVisited.count
        XCTAssertEqual(totalVisited, 7, "Total visited across continents should be 7")
    }

    func testVisitingCountryOnGlobeThenCheckingOnMap() {
        // Simulate: user visits France on globe, then switches to map
        // The achievement should reflect the visit regardless of view
        var visited: Set<String> = []

        // Visit on "globe"
        visited.insert("France")

        // Check achievement (as if on "map")
        let europeVisited = ContinentData.visitedCountries(in: .europe, from: visited)
        XCTAssertTrue(europeVisited.contains("France"))

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
        // Simulate: user visits Japan on map, then switches to globe
        var visited: Set<String> = []

        // Visit on "map"
        visited.insert("Japan")

        // Check achievement (as if on "globe")
        let asiaVisited = ContinentData.visitedCountries(in: .asia, from: visited)
        XCTAssertTrue(asiaVisited.contains("Japan"))

        let achievement = Achievement(
            name: "Explorer of Asia",
            medal: Continent.asia.medal,
            current: asiaVisited.count,
            total: Continent.asia.countries.count
        )

        XCTAssertEqual(achievement.current, 1)
        XCTAssertFalse(achievement.isCompleted)
    }
}
