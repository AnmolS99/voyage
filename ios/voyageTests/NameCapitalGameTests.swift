import XCTest
@testable import voyage

final class NameCapitalGameTests: XCTestCase {
    private var suiteName: String!
    private var userDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "NameCapitalGameTests-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    private func makeStore() -> ChallengeStatsStore {
        ChallengeStatsStore(userDefaults: userDefaults)
    }

    private let capitals = [
        "France": "Paris",
        "Germany": "Berlin",
        "Spain": "Madrid"
    ]

    private func makeViewModel(countries: [String],
                               store: ChallengeStatsStore? = nil) -> NameCapitalGameViewModel {
        NameCapitalGameViewModel(
            region: .europe,
            countries: countries,
            statsStore: store ?? makeStore(),
            capitalLookup: { self.capitals[$0] }
        )
    }

    // MARK: - Game flow

    func testScoringThroughFullSweep() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France", "Germany", "Spain"], store: store)

        // First target: correct
        XCTAssertEqual(viewModel.currentTarget, "France")
        XCTAssertEqual(viewModel.currentAnswer, "Paris")
        guard case .correct = viewModel.submitGuess("Paris") else {
            return XCTFail("Expected correct outcome")
        }

        // Second target: one miss is all it takes — the answer is revealed
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        guard case .reveal = viewModel.submitGuess("Vienna") else {
            return XCTFail("Expected reveal outcome")
        }
        XCTAssertEqual(viewModel.phase, .revealing)

        // Guesses are ignored while revealing
        guard case .ignored = viewModel.submitGuess("Berlin") else {
            return XCTFail("Expected guesses to be ignored during reveal")
        }
        viewModel.finishReveal()

        // Third target: correct
        XCTAssertEqual(viewModel.currentTarget, "Spain")
        guard case .correct = viewModel.submitGuess("Madrid") else {
            return XCTFail("Expected correct outcome")
        }

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertEqual(viewModel.correctCount, 2)
        XCTAssertEqual(viewModel.totalCountries, 3)
        XCTAssertEqual(viewModel.answeredCount, 3)
        XCTAssertEqual(viewModel.missedCountries, ["Germany"])
        XCTAssertTrue(viewModel.isNewBest)

        let stats = store.stats(for: .nameCapital, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestCorrect, 2)
        XCTAssertEqual(stats.bestTotal, 3)
        // The sweep is recorded under its own mode, not Click the Country
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
    }

    func testCapitalComparisonIgnoresCaseAndWhitespace() {
        let viewModel = makeViewModel(countries: ["France"])

        guard case .correct = viewModel.submitGuess("  pArIs ") else {
            return XCTFail("Expected case- and whitespace-insensitive match")
        }
    }

    func testEmptyGuessIsIgnored() {
        let viewModel = makeViewModel(countries: ["France"])

        guard case .ignored = viewModel.submitGuess("   ") else {
            return XCTFail("Expected whitespace-only guess to be ignored")
        }
        XCTAssertEqual(viewModel.currentTarget, "France")
        XCTAssertEqual(viewModel.answeredCount, 0)
    }

    func testCountriesWithoutCapitalDataAreExcluded() {
        let viewModel = makeViewModel(countries: ["France", "Atlantis"])

        XCTAssertEqual(viewModel.totalCountries, 1)
        XCTAssertEqual(viewModel.currentTarget, "France")
    }

    // MARK: - Trophies

    func testTrophyEarnedOnlyOnFirstPerfectSweep() {
        let store = makeStore()

        let first = makeViewModel(countries: ["France"], store: store)
        _ = first.submitGuess("Paris")
        XCTAssertEqual(first.phase, .finished)
        XCTAssertTrue(first.didEarnTrophy)

        // A repeat 100% run doesn't re-earn it
        let second = makeViewModel(countries: ["France"], store: store)
        _ = second.submitGuess("Paris")
        XCTAssertFalse(second.didEarnTrophy)

        XCTAssertEqual(store.trophyCount(.silver), 1)
    }

    func testImperfectSweepDoesNotEarnTrophy() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France"], store: store)

        _ = viewModel.submitGuess("Berlin")
        viewModel.finishReveal()

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertFalse(viewModel.didEarnTrophy)
        XCTAssertEqual(store.trophyCount(.silver), 0)
    }

    // MARK: - Data integrity

    /// Every playable country must have capital data, otherwise it would be
    /// silently dropped from Name the Capital sweeps.
    func testEveryRegionCountryHasACapital() {
        let countriesByName = Dictionary(
            uniqueKeysWithValues: CountryDataCache.shared.countries.map { ($0.name, $0) }
        )
        for country in ChallengeRegion.world.countries {
            XCTAssertNotNil(
                countriesByName[country]?.capital,
                "\(country) has no capital data in world.geojson"
            )
        }
    }
}
