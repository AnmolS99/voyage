import Foundation

/// Drives one "Click the Country" sweep on top of the shared region-sweep
/// engine (`RegionSweepGameViewModel`).
///
/// Guesses are confirmed with two taps: the first tap marks a country
/// (`.marked`), tapping the same country again submits it as the guess.
/// Tapping a different country moves the mark instead — so a mis-tap never
/// costs the country's single guess.
final class ClickCountryGameViewModel: RegionSweepGameViewModel {
    typealias TapOutcome = SweepGuessOutcome

    /// Country marked by the first tap, awaiting a confirming second tap.
    @Published private(set) var pendingGuess: String?

    /// `countries` overrides the region's country pool (used by tests).
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared) {
        super.init(mode: .clickCountry, region: region, countries: countries, statsStore: statsStore)
    }

    func handleTap(on country: String) -> TapOutcome {
        guard phase == .playing, let target = currentTarget else { return .ignored }
        guard !isAnswered(country) else { return .ignored }

        // First tap marks the country; only a tap on the marked country
        // confirms it as the guess (a tap elsewhere moves the mark)
        guard country == pendingGuess else {
            pendingGuess = country
            return .marked
        }
        pendingGuess = nil

        return resolveGuess(correct: country == target)
    }

    override func targetDidChange() {
        pendingGuess = nil
    }
}
