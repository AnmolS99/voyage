import Foundation

/// Drives one "Name the Capital" sweep on top of the shared region-sweep
/// engine (`RegionSweepGameViewModel`): each country in the region is shown
/// (flag + name) and the player types its capital.
///
/// Capitals compare case-insensitively and ignore surrounding whitespace.
/// Re-submitting a capital already guessed wrong for the current country
/// costs nothing.
final class NameCapitalGameViewModel: RegionSweepGameViewModel {
    /// Wrong capitals guessed for the current country (cleared on advance),
    /// so the search field can grey them out.
    @Published private(set) var wrongGuesses: [String] = []

    private let capitalLookup: (String) -> String?

    /// `countries` overrides the region's pool and `capitalLookup` the
    /// GeoJSON capital source (both used by tests).
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared,
         capitalLookup: ((String) -> String?)? = nil) {
        let lookup = capitalLookup ?? { name in
            CountryDataCache.shared.countries.first { $0.name == name }?.capital?.name
        }
        self.capitalLookup = lookup
        // A country without capital data could never be answered — drop it
        let pool = (countries ?? region.countries.shuffled()).filter { lookup($0) != nil }
        super.init(mode: .nameCapital, region: region, countries: pool, statsStore: statsStore)
    }

    /// The capital of the current target country.
    var currentCapital: String? {
        currentTarget.flatMap(capitalLookup)
    }

    func capital(of country: String) -> String? {
        capitalLookup(country)
    }

    func submitGuess(_ guess: String) -> SweepGuessOutcome {
        guard phase == .playing, let capital = currentCapital else { return .ignored }

        let trimmed = guess.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .ignored }
        guard !wrongGuesses.contains(where: { $0.caseInsensitiveCompare(trimmed) == .orderedSame }) else {
            return .ignored
        }

        let outcome = resolveGuess(correct: capital.caseInsensitiveCompare(trimmed) == .orderedSame)
        if case .wrong = outcome {
            wrongGuesses.append(trimmed)
        }
        return outcome
    }

    override func targetDidChange() {
        wrongGuesses = []
    }

    override func freshQueue() -> [String] {
        super.freshQueue().filter { capitalLookup($0) != nil }
    }
}
