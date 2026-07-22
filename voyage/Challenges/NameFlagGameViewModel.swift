import Foundation

/// Drives one "Name the Flag" sweep on top of the shared region-sweep engine
/// (`RegionSweepGameViewModel`): each country in the region is shown as its
/// flag and the player types the country's name.
///
/// The answer is the country's own name, compared case-insensitively and
/// ignoring surrounding whitespace. Re-submitting a name already guessed wrong
/// for the current flag costs nothing.
final class NameFlagGameViewModel: RegionSweepGameViewModel {
    /// Wrong country names guessed for the current flag (cleared on advance),
    /// so the search field can grey them out.
    @Published private(set) var wrongGuesses: [String] = []

    /// `countries` overrides the region's pool (used by tests).
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared) {
        super.init(mode: .nameFlag, region: region, countries: countries, statsStore: statsStore)
    }

    func submitGuess(_ guess: String) -> SweepGuessOutcome {
        guard phase == .playing, let target = currentTarget else { return .ignored }

        let trimmed = guess.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .ignored }
        guard !wrongGuesses.contains(where: { $0.caseInsensitiveCompare(trimmed) == .orderedSame }) else {
            return .ignored
        }

        let outcome = resolveGuess(correct: target.caseInsensitiveCompare(trimmed) == .orderedSame)
        if case .wrong = outcome {
            wrongGuesses.append(trimmed)
        }
        return outcome
    }

    override func targetDidChange() {
        wrongGuesses = []
    }
}
