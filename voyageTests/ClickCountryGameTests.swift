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
        XCTAssertEqual(viewModel.correctCount, 0)

        // Tapping a different country moves the mark instead of guessing
        guard case .marked = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected marked outcome")
        }
        XCTAssertEqual(viewModel.pendingGuess, "France")
        XCTAssertEqual(viewModel.correctCount, 0)

        // The confirming tap submits the guess
        guard case .correct = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected correct outcome")
        }
        XCTAssertNil(viewModel.pendingGuess)
    }

    func testScoringThroughFullSweep() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany", "Spain"],
            statsStore: store
        )

        // First target: found
        XCTAssertEqual(viewModel.currentTarget, "France")
        guard case .correct = guess(viewModel, "France") else {
            return XCTFail("Expected correct outcome")
        }

        // Second target: one miss is all it takes — the answer is revealed
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        guard case .reveal = guess(viewModel, "Norway") else {
            return XCTFail("Expected reveal outcome")
        }
        XCTAssertEqual(viewModel.phase, .revealing)

        // Taps are ignored while revealing
        guard case .ignored = viewModel.handleTap(on: "Germany") else {
            return XCTFail("Expected taps to be ignored during reveal")
        }
        viewModel.finishReveal()

        // Third target: found
        XCTAssertEqual(viewModel.currentTarget, "Spain")
        guard case .correct = guess(viewModel, "Spain") else {
            return XCTFail("Expected correct outcome")
        }

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertEqual(viewModel.correctCount, 2)
        XCTAssertEqual(viewModel.totalCountries, 3)
        XCTAssertEqual(viewModel.answeredCount, 3)
        XCTAssertEqual(viewModel.missedCountries, ["Germany"])
        XCTAssertTrue(viewModel.isNewBest)

        let stats = store.stats(for: .clickCountry, region: .europe)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestCorrect, 2)
        XCTAssertEqual(stats.bestTotal, 3)
    }

    func testTappingAlreadyAnsweredCountryIsIgnored() {
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: makeStore()
        )

        guess(viewModel, "France")

        // A stray tap on the already-found country doesn't count as a guess
        guard case .ignored = viewModel.handleTap(on: "France") else {
            return XCTFail("Expected ignored outcome")
        }
        XCTAssertEqual(viewModel.currentTarget, "Germany")
        XCTAssertEqual(viewModel.correctCount, 1)
    }

    /// Only sweeps that run to the end are recorded — restarting or walking
    /// away mid-run leaves the statistics untouched.
    func testUnfinishedRunsAreNotRecorded() {
        let store = makeStore()
        let viewModel = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: store
        )

        // Guess once, then restart mid-run
        guess(viewModel, "France")
        viewModel.restart()
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)

        // Abandoned after a guess: the view model is dropped, never finished
        let abandoned = ClickCountryGameViewModel(
            region: .europe,
            countries: ["France", "Germany"],
            statsStore: store
        )
        guess(abandoned, "France")
        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
    }

    // MARK: - Statistics

    func testBestResultComparesCorrectCountThenTime() {
        let store = makeStore()

        // First completed game is always a new best
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, correct: 5, total: 9, time: 100))
        // Fewer correct is not a best, even if faster
        XCTAssertFalse(store.recordGame(mode: .clickCountry, region: .world, correct: 4, total: 9, time: 50))
        // The same number correct but faster is a new best
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, correct: 5, total: 9, time: 80))
        // More correct beats a faster time
        XCTAssertTrue(store.recordGame(mode: .clickCountry, region: .world, correct: 6, total: 9, time: 200))

        let stats = store.stats(for: .clickCountry, region: .world)
        XCTAssertEqual(stats.gamesPlayed, 4)
        XCTAssertEqual(stats.bestCorrect, 6)
        XCTAssertEqual(stats.bestTime, 200)
    }

    func testStatsAreKeyedPerRegion() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .europe, correct: 5, total: 9, time: 100)

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
        store.recordGame(mode: .clickCountry, region: .southAmerica, correct: 12, total: 12, time: 120)
        store.recordGame(mode: .clickCountry, region: .oceania, correct: 14, total: 14, time: 200)
        store.recordGame(mode: .clickCountry, region: .europe, correct: 44, total: 44, time: 400)
        // Imperfect games award nothing
        store.recordGame(mode: .clickCountry, region: .asia, correct: 33, total: 49, time: 300)
        store.recordGame(mode: .clickCountry, region: .world, correct: 166, total: 195, time: 900)

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
        viewModel.finishReveal()

        XCTAssertEqual(viewModel.phase, .finished)
        XCTAssertFalse(viewModel.didEarnTrophy)
        XCTAssertEqual(store.trophyCount(.bronze), 0)
    }

    func testResetAllClearsStatsAndStorage() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .europe, correct: 5, total: 9, time: 100)

        store.resetAll()

        XCTAssertEqual(store.stats(for: .clickCountry, region: .europe).gamesPlayed, 0)
        // Storage is cleared too, so a fresh instance sees nothing
        let reloaded = ChallengeStatsStore(userDefaults: userDefaults)
        XCTAssertEqual(reloaded.totalGamesPlayed(for: .clickCountry), 0)
    }

    func testStatsPersistAcrossStoreInstances() {
        let store = makeStore()
        store.recordGame(mode: .clickCountry, region: .asia, correct: 10, total: 30, time: 60)

        let reloaded = ChallengeStatsStore(userDefaults: userDefaults)
        let stats = reloaded.stats(for: .clickCountry, region: .asia)
        XCTAssertEqual(stats.gamesPlayed, 1)
        XCTAssertEqual(stats.bestCorrect, 10)
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
