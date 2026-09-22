import Foundation

struct Achievement: Identifiable {
    let name: String
    var id: String { name }
    let medal: String
    let visitedCountries: [String]
    let remainingCountries: [String]
    var itemLabel: String = "countries"

    var current: Int { visitedCountries.count }
    var total: Int { visitedCountries.count + remainingCountries.count }
    var isCompleted: Bool { current >= total }
    var progress: Double { total > 0 ? Double(current) / Double(total) : 0 }
    var percentage: Int { Int(progress * 100) }
}

/// Every achievement and how far along it is, built from what the user has
/// marked. Lives outside `AchievementsView` so the app-wide unlock celebration
/// (`AchievementUnlockCelebration`) counts exactly what the tab shows. The
/// Android `AchievementCatalog` builds the same ten, in the same order.
enum AchievementCatalog {
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
            remainingCountries: remainingUN
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
            itemLabel: "capitals"
        ))

        // Wonders of the World achievement (New 7 + honorary Pyramids of Giza)
        list.append(Achievement(
            name: "Wonders of the World",
            medal: "⭐️",
            visitedCountries: WondersOfTheWorld.visited(from: checkedAttractions),
            remainingCountries: WondersOfTheWorld.remaining(from: checkedAttractions),
            itemLabel: "wonders"
        ))

        // Continental Drifter achievement — one item per continent, earned by
        // setting foot on all seven (Antarctica included)
        list.append(Achievement(
            name: "Continental Drifter",
            medal: "🌐",
            visitedCountries: ContinentData.visitedContinentNames(from: visited),
            remainingCountries: ContinentData.remainingContinentNames(from: visited),
            itemLabel: "continents"
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
                remainingCountries: remainingSorted
            ))
        }

        return list
    }

    /// The achievements `after` completes that `before` had not, in `after`'s
    /// order. A nil `before` is the first reading — a baseline, not a change —
    /// and losing achievements (a reset, an unticked country) unlocks nothing.
    /// Android's `newlyCompleted` answers the same question.
    static func newlyCompleted(before: [String]?, after: [String]) -> [String] {
        guard let before else { return [] }
        return after.filter { !before.contains($0) }
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
