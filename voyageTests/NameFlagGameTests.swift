import XCTest
@testable import voyage

final class NameFlagGameTests: XCTestCase {
    private var suiteName: String!
    private var userDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "NameFlagGameTests-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    private func makeStore() -> ChallengeStatsStore {
        ChallengeStatsStore(userDefaults: userDefaults)
    }

    /// Name the Flag needs no view model of its own — the shared sweep engine
    /// checks the country's own name.
    private func makeViewModel(countries: [String],
                               store: ChallengeStatsStore? = nil) -> RegionSweepGameViewModel {
        RegionSweepGameViewModel(
            mode: .nameFlag,
            region: .europe,
            countries: countries,
            statsStore: store ?? makeStore()
        )
    }

    // MARK: - Game flow

    func testScoringThroughFullSweep() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France", "Germany", "Spain"], store: store)

        // First target: correct
        XCTAssertEqual(viewModel.currentTarget, "France")
        guard case .correct = viewModel.submitGuess("France") else {
            return XCTFail("Expected correct outcome")
        }

        // Second target: one miss is all it takes — the answer is revealed
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        guard case .reveal = viewModel.submitGuess("Austria") else {
            return XCTFail("Expected reveal outcome")
        }
        XCTAssertEqual(viewModel.phase, .revealing)

        // Guesses are ignored while revealing
        guard case .ignored = viewModel.submitGuess("Germany") else {
            return XCTFail("Expected guesses to be ignored during reveal")
        }
        viewModel.finishReveal()

        // Third target: correct
        XCTAssertEqual(viewModel.currentTarget, "Spain")
        guard case .correct = viewModel.submitGuess("Spain") else {
            return XCTFail("Expected correct outcome")
        }

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertEqual(viewModel.correctCount, 2)
        XCTAssertEqual(viewModel.totalCountries, 3)
        XCTAssertEqual(viewModel.answeredCount, 3)
        XCTAssertEqual(viewModel.missedCountries, ["Germany"])
        XCTAssertTrue(viewModel.isNewBest)

        let stats = store.stats(for: .nameFlag, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestCorrect, 2)
        XCTAssertEqual(stats.bestTotal, 3)
        // The sweep is recorded under its own mode, not the other games
        XCTAssertEqual(store.stats(for: .nameCapital, region: .europe).gamesPlayed, 0)
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
    }

    func testCountryComparisonIgnoresCaseAndWhitespace() {
        let viewModel = makeViewModel(countries: ["France"])

        guard case .correct = viewModel.submitGuess("  fRaNcE ") else {
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

    // MARK: - Attempts and trophies

    func testAttemptCountedOnFirstGuessOnly() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France", "Germany"], store: store)

        // Opening a game is not an attempt
        XCTAssertEqual(store.stats(for: .nameFlag, region: .europe).attempts, 0)

        // The first guess (right or wrong) counts the attempt, exactly once
        _ = viewModel.submitGuess("Spain")
        XCTAssertEqual(store.stats(for: .nameFlag, region: .europe).attempts, 1)
        viewModel.finishReveal()
        _ = viewModel.submitGuess("Germany")
        XCTAssertEqual(store.stats(for: .nameFlag, region: .europe).attempts, 1)
        XCTAssertEqual(store.stats(for: .nameFlag, region: .europe).gamesPlayed, 1)
    }

    func testTrophyEarnedOnlyOnFirstPerfectSweep() {
        let store = makeStore()

        let first = makeViewModel(countries: ["France"], store: store)
        _ = first.submitGuess("France")
        XCTAssertEqual(first.phase, .finished)
        XCTAssertTrue(first.didEarnTrophy)

        // A repeat 100% run doesn't re-earn it
        let second = makeViewModel(countries: ["France"], store: store)
        _ = second.submitGuess("France")
        XCTAssertFalse(second.didEarnTrophy)

        XCTAssertEqual(store.trophyCount(.silver), 1)
    }

    func testImperfectSweepDoesNotEarnTrophy() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France"], store: store)

        _ = viewModel.submitGuess("Germany")
        viewModel.finishReveal()

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertFalse(viewModel.didEarnTrophy)
        XCTAssertEqual(store.trophyCount(.silver), 0)
    }

    // MARK: - Suggestion ranking

    func testRankedMatchesPutsPrefixMatchesFirst() {
        let capitals = ["Buenos Aires", "Moscow", "Nicosia", "Oslo"]

        // "Os" is a prefix of "Oslo" but appears mid-word in the others, so
        // Oslo must rank first (see the reported search bug).
        XCTAssertEqual(capitals.rankedMatches(for: "Os").first, "Oslo")
    }

    func testRankedMatchesPreservesOrderWithinGroups() {
        let capitals = ["Buenos Aires", "Moscow", "Nicosia", "Oslo"]

        // Prefix match first, then the interior matches in their original
        // (alphabetical) order.
        XCTAssertEqual(capitals.rankedMatches(for: "os"),
                       ["Oslo", "Buenos Aires", "Moscow", "Nicosia"])
    }

    func testRankedMatchesIsCaseInsensitive() {
        let names = ["oslo", "OSAKA", "Buenos Aires"]

        XCTAssertEqual(names.rankedMatches(for: "OS"), ["oslo", "OSAKA", "Buenos Aires"])
    }

    func testRankedMatchesEmptyQueryReturnsNothing() {
        XCTAssertTrue(["Oslo", "Paris"].rankedMatches(for: "").isEmpty)
        XCTAssertTrue(["Oslo", "Paris"].rankedMatches(for: "   ").isEmpty)
    }

    func testRankedMatchesExcludesNonMatches() {
        let capitals = ["Buenos Aires", "Moscow", "Nicosia", "Oslo"]

        XCTAssertEqual(capitals.rankedMatches(for: "Osl"), ["Oslo"])
    }
}
