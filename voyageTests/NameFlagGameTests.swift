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

    private func makeViewModel(countries: [String],
                               store: ChallengeStatsStore? = nil) -> NameFlagGameViewModel {
        NameFlagGameViewModel(
            region: .europe,
            countries: countries,
            statsStore: store ?? makeStore()
        )
    }

    // MARK: - Game flow

    func testGradedScoringThroughFullSweep() {
        let store = makeStore()
        let viewModel = makeViewModel(countries: ["France", "Germany", "Spain"], store: store)

        // First target: correct on the first try = 3 points
        XCTAssertEqual(viewModel.currentTarget, "France")
        guard case .correct(let points1) = viewModel.submitGuess("France") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertEqual(points1, 3)

        // Second target: one miss, then correct = 2 points
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        guard case .wrong(let remaining) = viewModel.submitGuess("Austria") else {
            return XCTFail("Expected wrong outcome")
        }
        XCTAssertEqual(remaining, 2)
        guard case .correct(let points2) = viewModel.submitGuess("Germany") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertEqual(points2, 2)

        // Third target: three misses = reveal for 0 points
        XCTAssertEqual(viewModel.currentTarget, "Spain")
        _ = viewModel.submitGuess("Portugal")
        _ = viewModel.submitGuess("Italy")
        guard case .reveal = viewModel.submitGuess("Andorra") else {
            return XCTFail("Expected reveal outcome")
        }
        XCTAssertEqual(viewModel.phase, .revealing)

        // Guesses are ignored while revealing
        guard case .ignored = viewModel.submitGuess("Spain") else {
            return XCTFail("Expected guesses to be ignored during reveal")
        }

        viewModel.finishReveal()
        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertEqual(viewModel.score, 5)
        XCTAssertEqual(viewModel.maxScore, 9)
        XCTAssertEqual(viewModel.solvedCount, 3)
        XCTAssertEqual(viewModel.perfectCount, 1)
        XCTAssertEqual(viewModel.missedCountries, ["Spain"])
        XCTAssertTrue(viewModel.isNewBest)

        let stats = store.stats(for: .nameFlag, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestScore, 5)
        XCTAssertEqual(stats.bestScoreMax, 9)
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
        XCTAssertEqual(viewModel.triesLeft, 3)
    }

    func testRepeatedWrongGuessCostsNothing() {
        let viewModel = makeViewModel(countries: ["France"])

        _ = viewModel.submitGuess("Germany")
        XCTAssertEqual(viewModel.triesLeft, 2)
        XCTAssertEqual(viewModel.wrongGuesses, ["Germany"])

        // The same wrong country again (any casing) doesn't burn a try
        guard case .ignored = viewModel.submitGuess("germany") else {
            return XCTFail("Expected repeated wrong guess to be ignored")
        }
        XCTAssertEqual(viewModel.triesLeft, 2)
        XCTAssertEqual(viewModel.wrongGuesses, ["Germany"])
    }

    func testWrongGuessesClearForNextCountry() {
        let viewModel = makeViewModel(countries: ["France", "Germany"])

        _ = viewModel.submitGuess("Spain")
        _ = viewModel.submitGuess("France")
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        XCTAssertTrue(viewModel.wrongGuesses.isEmpty)

        // Spain is guessable again for the new flag
        guard case .wrong = viewModel.submitGuess("Spain") else {
            return XCTFail("Expected a fresh wrong guess for the new country")
        }
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
        _ = viewModel.submitGuess("France")
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
        _ = viewModel.submitGuess("France")

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertFalse(viewModel.didEarnTrophy)
        XCTAssertEqual(store.trophyCount(.silver), 0)
    }
}
