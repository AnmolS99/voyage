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

    /// Shaped like `country_aliases.json`: other names, former names, parts.
    private let aliases: [String: [CountryAlias]] = [
        "United Kingdom": [
            CountryAlias(text: "UK", kind: .name),
            CountryAlias(text: "Great Britain", kind: .name),
            CountryAlias(text: "England", kind: .part),
        ],
        "Myanmar": [CountryAlias(text: "Burma", kind: .formerName)],
        "Türkiye": [CountryAlias(text: "Turkey", kind: .formerName)],
        "United States": [CountryAlias(text: "USA", kind: .name), CountryAlias(text: "Hawaii", kind: .part)],
    ]

    private func makeViewModel(countries: [String],
                               store: ChallengeStatsStore? = nil) -> NameFlagGameViewModel {
        NameFlagGameViewModel(
            region: .europe,
            countries: countries,
            statsStore: store ?? makeStore(),
            aliasLookup: { [aliases] in aliases[$0] ?? [] }
        )
    }

    private func outcome(_ guess: String, for country: String) -> SweepGuessOutcome {
        makeViewModel(countries: [country]).submitGuess(guess)
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

    // MARK: - Other names

    func testTypedAliasNamesTheCountry() {
        XCTAssertEqual(outcome("UK", for: "United Kingdom"), .correct)
        XCTAssertEqual(outcome(" great britain ", for: "United Kingdom"), .correct)
        XCTAssertEqual(outcome("usa", for: "United States"), .correct)
    }

    func testFormerNameNamesTheCountry() {
        XCTAssertEqual(outcome("Burma", for: "Myanmar"), .correct)
    }

    func testTypedNameIsFoldedTheWaySearchFoldsIt() {
        XCTAssertEqual(outcome("turkiye", for: "Türkiye"), .correct)
    }

    func testAliasAnswersWithTheCountrysOwnName() {
        let viewModel = makeViewModel(countries: ["United Kingdom"])
        XCTAssertEqual(viewModel.acceptedAnswer(for: "uk"), "United Kingdom")
    }

    func testPartOfACountryOrItsCodeIsNotANameForIt() {
        XCTAssertEqual(outcome("England", for: "United Kingdom"), .reveal)
        XCTAssertEqual(outcome("Hawaii", for: "United States"), .reveal)
        XCTAssertEqual(outcome("GB", for: "United Kingdom"), .reveal)
    }

    func testPartialNameDoesNotCountEvenAsTheTopSuggestion() {
        XCTAssertEqual(outcome("Ni", for: "Niger"), .reveal)
    }

    func testAnotherCountrysAliasDoesNotCount() {
        XCTAssertEqual(outcome("UK", for: "United States"), .reveal)
    }

    func testEmptyGuessIsIgnored() {
        let viewModel = makeViewModel(countries: ["France"])

        guard case .ignored = viewModel.submitGuess("   ") else {
            return XCTFail("Expected whitespace-only guess to be ignored")
        }
        XCTAssertEqual(viewModel.currentTarget, "France")
        XCTAssertEqual(viewModel.answeredCount, 0)
    }

    // MARK: - Trophies

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

    private func suggestions(_ items: [String], for query: String) -> [String] {
        CountrySearchIndex<String>.plain(items).suggestions(for: query).map(\.item)
    }

    func testSuggestionsPutPrefixMatchesFirst() {
        let capitals = ["Buenos Aires", "Moscow", "Nicosia", "Oslo"]

        // "Os" is a prefix of "Oslo" but appears mid-word in the others, so
        // Oslo must rank first (see the reported search bug).
        XCTAssertEqual(suggestions(capitals, for: "Os").first, "Oslo")
    }

    func testSuggestionsAreAlphabeticalWithinGroups() {
        let capitals = ["Oslo", "Nicosia", "Moscow", "Buenos Aires"]

        // Prefix match first, then the interior matches alphabetically.
        XCTAssertEqual(suggestions(capitals, for: "os"),
                       ["Oslo", "Buenos Aires", "Moscow", "Nicosia"])
    }

    func testSuggestionsAreCaseInsensitive() {
        let names = ["oslo", "OSAKA", "Buenos Aires"]

        XCTAssertEqual(suggestions(names, for: "OS"), ["OSAKA", "oslo", "Buenos Aires"])
    }

    func testSuggestionsForEmptyQueryAreEmpty() {
        XCTAssertTrue(suggestions(["Oslo", "Paris"], for: "").isEmpty)
        XCTAssertTrue(suggestions(["Oslo", "Paris"], for: "   ").isEmpty)
    }

    func testSuggestionsExcludeNonMatches() {
        let capitals = ["Buenos Aires", "Moscow", "Nicosia", "Oslo"]

        XCTAssertEqual(suggestions(capitals, for: "Osl"), ["Oslo"])
    }

    func testCountryNameSuggestionsMatchAliases() {
        let matches = CountrySearchIndex<String>.countryNames().suggestions(for: "uk")
        XCTAssertEqual(matches.first?.item, "United Kingdom")
        XCTAssertEqual(matches.first?.matchedAlias?.text, "UK")
    }
}
