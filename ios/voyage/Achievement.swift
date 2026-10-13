import Foundation

/// The three medals each achievement can be held at, in the order they are
/// earned. An achievement short of bronze holds none and its medal is shown
/// as a silhouette. The Android `MedalTier` is the same three, in the same order.
enum MedalTier: Int, CaseIterable, Comparable {
    case bronze, silver, gold

    var displayName: String {
        switch self {
        case .bronze: return "Bronze"
        case .silver: return "Silver"
        case .gold: return "Gold"
        }
    }

    static func < (lhs: MedalTier, rhs: MedalTier) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// How many items earn bronze and silver. Gold is always the whole
/// achievement, so it is not stored: it moves with the total if the data does.
struct MedalThresholds: Equatable {
    let bronze: Int
    let silver: Int

    /// A third and two thirds of `total`, rounded up — the fallback for an
    /// achievement built without limits of its own (tests, mostly).
    static func thirds(of total: Int) -> MedalThresholds {
        MedalThresholds(bronze: (total + 2) / 3, silver: (2 * total + 2) / 3)
    }

    /// The item count at which `tier` is earned, out of `total`.
    func count(for tier: MedalTier, total: Int) -> Int {
        switch tier {
        case .bronze: return bronze
        case .silver: return silver
        case .gold: return total
        }
    }
}

struct Achievement: Identifiable {
    let name: String
    var id: String { name }
    let medal: String
    let visitedCountries: [String]
    let remainingCountries: [String]
    var itemLabel: String = "countries"
    /// Nil falls back to thirds of the total.
    var thresholds: MedalThresholds? = nil

    var current: Int { visitedCountries.count }
    var total: Int { visitedCountries.count + remainingCountries.count }
    var isCompleted: Bool { current >= total }
    var progress: Double { total > 0 ? Double(current) / Double(total) : 0 }
    var percentage: Int { Int(progress * 100) }

    var medalThresholds: MedalThresholds { thresholds ?? .thirds(of: total) }

    /// The highest medal held, or nil while short of bronze. Gold is exactly
    /// `isCompleted`, so a medal and its completion can never disagree.
    var tier: MedalTier? {
        if isCompleted { return .gold }
        if current >= medalThresholds.silver { return .silver }
        if current >= medalThresholds.bronze { return .bronze }
        return nil
    }

    /// The next medal to earn, or nil once gold is held.
    var nextTier: MedalTier? {
        guard let tier else { return .bronze }
        return MedalTier(rawValue: tier.rawValue + 1)
    }

    /// How many more items the next medal needs; 0 once gold is held.
    var remainingToNextTier: Int {
        guard let nextTier else { return 0 }
        return max(0, medalThresholds.count(for: nextTier, total: total) - current)
    }

    /// Every medal held — bronze up to `tier` — for the unlock celebration.
    var unlocks: [MedalUnlock] {
        guard let tier else { return [] }
        return MedalTier.allCases.filter { $0 <= tier }.map { MedalUnlock(id: id, tier: $0) }
    }
}

/// One medal of one achievement — what the unlock celebration announces.
struct MedalUnlock: Hashable {
    let id: String
    let tier: MedalTier
}

/// Every achievement and how far along it is, built from what the user has
/// marked. Lives outside `AchievementsView` so the app-wide unlock celebration
/// (`AchievementUnlockCelebration`) counts exactly what the tab shows. The
/// Android `AchievementCatalog` builds the same ten, in the same order, with
/// the same medal limits.
enum AchievementCatalog {
    /// Bronze and silver limits per achievement; gold is all of it. Pinned by
    /// `AchievementCompletionTests` here and `AchievementTest` on Android.
    static let globetrotterThresholds = MedalThresholds(bronze: 25, silver: 100)
    static let capitalCollectorThresholds = MedalThresholds(bronze: 25, silver: 100)
    static let wondersThresholds = MedalThresholds(bronze: 2, silver: 5)
    static let continentalDrifterThresholds = MedalThresholds(bronze: 2, silver: 4)

    static func explorerThresholds(_ continent: Continent) -> MedalThresholds {
        switch continent {
        case .africa: return MedalThresholds(bronze: 10, silver: 30)
        case .asia: return MedalThresholds(bronze: 10, silver: 25)
        case .europe: return MedalThresholds(bronze: 10, silver: 25)
        case .northAmerica: return MedalThresholds(bronze: 5, silver: 15)
        case .southAmerica: return MedalThresholds(bronze: 3, silver: 8)
        case .oceania: return MedalThresholds(bronze: 3, silver: 8)
        // No explorer medal; listed so the switch stays exhaustive.
        case .antarctica: return MedalThresholds(bronze: 1, silver: 1)
        }
    }

    static func achievements(
        visited: Set<String>,
        checkedCities: [String: Set<String>],
        checkedAttractions: [String: Set<String>]
    ) -> [Achievement] {
        var list: [Achievement] = []

        // World traveler achievement (first)
        let allCountries = CountryDataCache.shared.countryNames
        let unCountries = allCountries.subtracting(GlobeState.nonUNTerritories)
        let visitedUNSet = visited.subtracting(GlobeState.nonUNTerritories)
        let visitedUN = Array(visitedUNSet).sorted()
        let remainingUN = Array(unCountries.subtracting(visitedUNSet)).sorted()

        list.append(Achievement(
            name: "Globetrotter",
            medal: "🌍",
            visitedCountries: visitedUN,
            remainingCountries: remainingUN,
            thresholds: globetrotterThresholds
        ))

        // Capital Collector achievement
        let countriesWithCapitals = CountryDataCache.shared.countries.filter { country in
            guard country.capital != nil else { return false }
            return unCountries.contains(country.name)
        }
        let visitedCapitals = countriesWithCapitals.filter { country in
            (checkedCities[country.name] ?? []).contains(country.capital!.name)
        }.map { $0.capital!.name }.sorted()
        let remainingCapitals = countriesWithCapitals.filter { country in
            !(checkedCities[country.name] ?? []).contains(country.capital!.name)
        }.map { $0.capital!.name }.sorted()

        list.append(Achievement(
            name: "Capital Collector",
            medal: "🏛️",
            visitedCountries: visitedCapitals,
            remainingCountries: remainingCapitals,
            itemLabel: "capitals",
            thresholds: capitalCollectorThresholds
        ))

        // Wonders of the World achievement (New 7 + honorary Pyramids of Giza)
        list.append(Achievement(
            name: "Wonders of the World",
            medal: "⭐️",
            visitedCountries: WondersOfTheWorld.visited(from: checkedAttractions),
            remainingCountries: WondersOfTheWorld.remaining(from: checkedAttractions),
            itemLabel: "wonders",
            thresholds: wondersThresholds
        ))

        // Continental Drifter achievement — one item per continent, earned by
        // setting foot on all seven (Antarctica included)
        list.append(Achievement(
            name: "Continental Drifter",
            medal: "🌐",
            visitedCountries: ContinentData.visitedContinentNames(from: visited),
            remainingCountries: ContinentData.remainingContinentNames(from: visited),
            itemLabel: "continents",
            thresholds: continentalDrifterThresholds
        ))

        // Continent achievements
        for continent in Continent.allCases where continent != .antarctica {
            let countries = continent.countries
            let visitedInContinent = ContinentData.visitedCountries(in: continent, from: visited)
            let visitedSorted = Array(visitedInContinent).sorted()
            let remainingSorted = Array(countries.subtracting(visitedInContinent)).sorted()

            list.append(Achievement(
                name: "Explorer of \(continent.rawValue)",
                medal: continent.medal,
                visitedCountries: visitedSorted,
                remainingCountries: remainingSorted,
                thresholds: explorerThresholds(continent)
            ))
        }

        return list
    }

    /// The medals `after` holds that `before` did not, in `after`'s order —
    /// each list being every medal held (`Achievement.unlocks`, bronze up). A
    /// nil `before` is the first reading — a baseline, not a change — and
    /// losing medals (a reset, an unticked country) unlocks nothing. A jump of
    /// several tiers at once announces only the highest. Android's
    /// `newlyUnlocked` answers the same question.
    static func newlyUnlocked(before: [MedalUnlock]?, after: [MedalUnlock]) -> [MedalUnlock] {
        guard let before else { return [] }
        let new = after.filter { !before.contains($0) }
        return new.filter { unlock in
            !new.contains { $0.id == unlock.id && $0.tier > unlock.tier }
        }
    }
}

/// The New 7 Wonders of the World plus the Pyramids of Giza — the only surviving
/// ancient wonder, which the New7Wonders campaign named an honorary eighth rather
/// than putting it to the vote. Each is paired with the country whose attraction
/// checklist (country_highlights.json) contains it.
enum WondersOfTheWorld {
    static let wonders: [(country: String, attraction: String)] = [
        ("Brazil", "Christ the Redeemer"),
        ("China", "Great Wall of China"),
        ("Egypt", "Pyramids of Giza"),
        ("India", "Taj Mahal"),
        ("Italy", "Colosseum"),
        ("Jordan", "Petra"),
        ("Mexico", "Chichen Itza"),
        ("Peru", "Machu Picchu"),
    ]

    static func visited(from checkedAttractions: [String: Set<String>]) -> [String] {
        wonders
            .filter { checkedAttractions[$0.country]?.contains($0.attraction) == true }
            .map(\.attraction)
            .sorted()
    }

    static func remaining(from checkedAttractions: [String: Set<String>]) -> [String] {
        wonders
            .filter { checkedAttractions[$0.country]?.contains($0.attraction) != true }
            .map(\.attraction)
            .sorted()
    }
}
