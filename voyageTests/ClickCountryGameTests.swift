import XCTest
@testable import voyage

final class ClickCountryGameTests: XCTestCase {
    private var suiteName: String!
    private var userDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "ClickCountryGameTests-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    private func makeStore() -> ChallengeStatsStore {
        ChallengeStatsStore(userDefaults: userDefaults)
    }

    /// Performs a full guess: a first tap to mark the country, then a
    /// confirming second tap. Returns the confirmation outcome (or the first
    /// tap's outcome when it didn't mark, e.g. `.ignored`).
    @discardableResult
    private func guess(_ viewModel: ClickCountryGameViewModel, _ country: String) -> ClickCountryGameViewModel.TapOutcome {
        let first = viewModel.handleTap(on: country)
        guard case .marked = first else { return first }
        return viewModel.handleTap(on: country)
    }

    // MARK: - Game flow

    func testFirstTapMarksAndSecondConfirms() {
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France"],
            statsStore: makeStore()
        )

        // First tap only marks — nothing is scored or consumed
        guard case .marked = viewModel.handleTap(on: "Germany") else {
            return XCTFail("Expected marked outcome")
        }
        XCTAssertEqual(viewModel.pendingGuess, "Germany")
        XCTAssertEqual(viewModel.triesLeft, 3)
        XCTAssertEqual(viewModel.score, 0)

        // Tapping a different country moves the mark instead of guessing
        guard case .marked = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected marked outcome")
        }
        XCTAssertEqual(viewModel.pendingGuess, "France")
        XCTAssertEqual(viewModel.triesLeft, 3)

        // The confirming tap submits the guess
        guard case .correct(let points) = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertEqual(points, 3)
        XCTAssertNil(viewModel.pendingGuess)
    }

    func testGradedScoringThroughFullSweep() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany", "Spain"],
            statsStore: store
        )

        // First target: found on first try = 3 points
        XCTAssertEqual(viewModel.currentTarget, "France")
        guard case .correct(let points1) = guess(viewModel, "France") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertEqual(points1, 3)

        // Second target: one miss, then found = 2 points
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        guard case .wrong(let remaining) = guess(viewModel, "Norway") else {
            return XCTFail("Expected wrong outcome")
        }
        XCTAssertEqual(remaining, 2)
        guard case .correct(let points2) = guess(viewModel, "Germany") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertEqual(points2, 2)

        // Third target: three misses = reveal for 0 points
        XCTAssertEqual(viewModel.currentTarget, "Spain")
        guess(viewModel, "Norway")
        guess(viewModel, "Italy")
        guard case .reveal = guess(viewModel, "Portugal") else {
            return XCTFail("Expected reveal outcome")
        }
        XCTAssertEqual(viewModel.phase, .revealing)

        // Taps are ignored while revealing
        guard case .ignored = viewModel.handleTap(on: "Spain") else {
            return XCTFail("Expected taps to be ignored during reveal")
        }

        viewModel.finishReveal()
        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertEqual(viewModel.score, 5)
        XCTAssertEqual(viewModel.maxScore, 9)
        XCTAssertEqual(viewModel.solvedCount, 3)
        XCTAssertEqual(viewModel.perfectCount, 1)
        XCTAssertEqual(viewModel.missedCountries, ["Spain"])
        XCTAssertTrue(viewModel.isNewBest)

        let stats = store.stats(for: .clickCountry, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestScore, 5)
        XCTAssertEqual(stats.bestScoreMax, 9)
    }

    func testTappingAlreadySolvedCountryIsIgnored() {
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: makeStore()
        )

        guess(viewModel, "France")

        // A stray tap on the already-found country costs no tries
        guard case .ignored = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected ignored outcome")
        }
        XCTAssertEqual(viewModel.triesLeft, 3)
    }

    // MARK: - Attempts vs completed games

    func testAttemptCountedOnFirstGuessOnly() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: store
        )

        // No guess yet: opening a game is not an attempt
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 0)

        // Marking a country is not a guess yet
        _ = viewModel.handleTap(on: "Norway")
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 0)

        // The first confirmed guess (right or wrong) counts the attempt, exactly once
        _ = viewModel.handleTap(on: "Norway")
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 1)
        guess(viewModel, "France")
        guess(viewModel, "Germany")
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 1)

        // The completed run counts once in both statistics
        let stats = store.stats(for: .clickCountry, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.attempts, 1)
    }

    func testAbandonedAndRestartedRunsCountAsAttemptsButNotPlayed() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: store
        )

        // Guess once, then restart mid-run: attempt recorded, nothing completed
        guess(viewModel, "France")
        viewModel.restart()
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 1)
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)

        // A restart with no guess afterwards adds nothing
        viewModel.restart()
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 1)

        // Abandoning after a guess (view model dropped, never finished) still counts
        let abandoned = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: store
        )
        guess(abandoned, "Norway")
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).attempts, 2)
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
    }

    // MARK: - Statistics

    func testBestResultComparesScoreThenTime() {
        let store = makeStore()

        // First completed game is always a new best
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, score: 5, maxScore: 9, time: 100))
        // Lower score is not a best, even if faster
        XCTAssertFalse(store.recordGame(mode: .clickCountry, region: .world, score: 4, maxScore: 9, time: 50))
        // Same score but faster is a new best
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, score: 5, maxScore: 9, time: 80))
        // Higher score beats a faster time
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, score: 6, maxScore: 9, time: 200))

        let stats = store.stats(for: .clickCountry, region: .world)
        XCTAssertEqual(stats.gamesPlayed, 4)
        XCTAssertEqual(stats.bestScore, 6)
        XCTAssertEqual(stats.bestScoreTime, 200)
    }

    func testStatsAreKeyedPerRegion() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .europe, score: 5, maxScore: 9, time: 100)

        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 1)
        XCTAssertEqual(store.stats(for: .clickCountry, region: .africa).gamesPlayed, 0)
        XCTAssertEqual(store.totalGamesPlayed(for: .clickCountry), 1)
    }

    func testTrophyTiersPerRegion() {
        XCTAssertEqual(ChallengeRegion.world.trophy, .gold)
        XCTAssertEqual(ChallengeRegion.europe.trophy, .silver)
        XCTAssertEqual(ChallengeRegion.asia.trophy, .silver)
        XCTAssertEqual(ChallengeRegion.africa.trophy, .silver)
        XCTAssertEqual(ChallengeRegion.southAmerica.trophy, .bronze)
        XCTAssertEqual(ChallengeRegion.northAmerica.trophy, .bronze)
        XCTAssertEqual(ChallengeRegion.oceania.trophy, .bronze)
    }

    func testTrophyCountingRequiresPerfectSweeps() {
        let store = makeStore()
        // Two perfect bronze regions, one perfect silver region
        store.recordGame(mode: .clickCountry, region: .southAmerica, score: 36, maxScore: 36, time: 120)
        store.recordGame(mode: .clickCountry, region: .oceania, score: 42, maxScore: 42, time: 200)
        store.recordGame(mode: .clickCountry, region: .europe, score: 132, maxScore: 132, time: 400)
        // Imperfect games award nothing
        store.recordGame(mode: .clickCountry, region: .asia, score: 100, maxScore: 147, time: 300)
        store.recordGame(mode: .clickCountry, region: .world, score: 500, maxScore: 585, time: 900)

        XCTAssertTrue(store.stats(for: .clickCountry, region: .southAmerica).isPerfect)
        XCTAssertFalse(store.stats(for: .clickCountry, region: .asia).isPerfect)
        XCTAssertEqual(store.trophyCount(.bronze), 2)
        XCTAssertEqual(store.trophyCount(.silver), 1)
        XCTAssertEqual(store.trophyCount(.gold), 0)
    }

    func testTrophyEarnedOnlyOnFirstPerfectSweep() {
        let store = makeStore()

        // First flawless sweep earns the trophy
        let first = ClickCountryGameViewModel(region: .oceania, countries: ["Fiji"], statsStore: store)
        guess(first, "Fiji")
        XCTAssertEqual(first.phase, .finished)
        XCTAssertTrue(first.didEarnTrophy)

        // A repeat 100% run doesn't re-earn it
        let second = ClickCountryGameViewModel(region: .oceania, countries: ["Fiji"], statsStore: store)
        guess(second, "Fiji")
        XCTAssertEqual(second.phase, .finished)
        XCTAssertFalse(second.didEarnTrophy)

        // Still only one bronze trophy in the cabinet
        XCTAssertEqual(store.trophyCount(.bronze), 1)
    }

    func testImperfectSweepDoesNotEarnTrophy() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(region: .oceania, countries: ["Fiji"], statsStore: store)

        guess(viewModel, "Samoa")
        guess(viewModel, "Fiji")

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertFalse(viewModel.didEarnTrophy)
        XCTAssertEqual(store.trophyCount(.bronze), 0)
    }

    func testResetAllClearsStatsAndStorage() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .europe, score: 5, maxScore: 9, time: 100)

        store.resetAll()

        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
        // Storage is cleared too, so a fresh instance sees nothing
        let reloaded = ChallengeStatsStore(userDefaults: userDefaults)
        XCTAssertEqual(reloaded.totalGamesPlayed(for: .clickCountry), 0)
    }

    func testStatsPersistAcrossStoreInstances() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .asia, score: 10, maxScore: 30, time: 60)

        let reloaded = ChallengeStatsStore(userDefaults: userDefaults)
        let stats = reloaded.stats(for: .clickCountry, region: .asia)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestScore, 10)
    }

    // MARK: - Region pools

    func testRegionPoolsMatchAppProgressCounting() {
        let world = Set(ChallengeRegion.world.countries)

        XCTAssertFalse(world.isEmpty)
        // Excluded territories never appear as targets
        for territory in GlobeState.nonUNTerritories {
            XCTAssertFalse(world.contains(territory), "\(territory) should not be a game target")
        }

        // Every continent pool is disjoint slicing of the world pool
        for region in ChallengeRegion.allCases where region != .world {
            let pool = Set(region.countries)
            XCTAssertFalse(pool.isEmpty, "\(region.displayName) pool should not be empty")
            XCTAssertTrue(pool.isSubset(of: world), "\(region.displayName) pool should be part of the world pool")
        }
    }
}
