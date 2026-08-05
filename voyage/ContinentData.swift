import Foundation

enum Continent: String, CaseIterable {
    case africa = "Africa"
    case asia = "Asia"
    case europe = "Europe"
    case northAmerica = "North America"
    case southAmerica = "South America"
    case oceania = "Oceania"
    case antarctica = "Antarctica"

    var medal: String {
        switch self {
        case .africa: return "🦁"
        case .asia: return "🐉"
        case .europe: return "🏰"
        case .northAmerica: return "🦅"
        case .southAmerica: return "🦜"
        case .oceania: return "🐨"
        case .antarctica: return "🐧"
        }
    }

    var countries: Set<String> {
        Set(ContinentData.countriesByContinent[self] ?? [])
    }

    /// Initialize from raw string value (e.g., from GeoJSON)
    init?(rawContinent: String) {
        switch rawContinent {
        case "Africa": self = .africa
        case "Asia": self = .asia
        case "Europe": self = .europe
        case "North America": self = .northAmerica
        case "South America": self = .southAmerica
        case "Oceania": self = .oceania
        case "Antarctica": self = .antarctica
        default: return nil
        }
    }
}

struct ContinentData {
    /// Lazily loaded country-to-continent mapping from GeoJSON
    /// This is the single source of truth for which countries belong to which continent
    private static var _countriesByContinent: [Continent: [String]]?

    static var countriesByContinent: [Continent: [String]] {
        if let cached = _countriesByContinent {
            return cached
        }

        var mapping: [Continent: [String]] = [:]
        for continent in Continent.allCases {
            mapping[continent] = []
        }

        // Load all countries from GeoJSON (includes both polygon and point countries)
        let geoJSONCountries = CountryDataCache.shared.countries
        for country in geoJSONCountries {
            if let continentStr = country.continent,
               let continent = Continent(rawContinent: continentStr) {
                mapping[continent, default: []].append(country.name)
            }
        }

        _countriesByContinent = mapping
        return mapping
    }

    static func continent(for country: String) -> Continent? {
        for (continent, countries) in countriesByContinent {
            if countries.contains(country) {
                return continent
            }
        }
        return nil
    }

    static func visitedCountries(in continent: Continent, from visited: Set<String>) -> Set<String> {
        visited.intersection(continent.countries)
    }

    /// A continent counts as visited once any single country on it has been visited.
    static func hasVisited(_ continent: Continent, from visited: Set<String>) -> Bool {
        !visitedCountries(in: continent, from: visited).isEmpty
    }

    /// Continents with at least one visited country, in `Continent.allCases` order.
    /// Antarctica is included here even though it has no "Explorer of" achievement
    /// of its own — the Continental Drifter achievement requires all seven.
    static func visitedContinentNames(from visited: Set<String>) -> [String] {
        Continent.allCases.filter { hasVisited($0, from: visited) }.map(\.rawValue)
    }

    /// Continents with no visited country, in `Continent.allCases` order.
    static func remainingContinentNames(from visited: Set<String>) -> [String] {
        Continent.allCases.filter { !hasVisited($0, from: visited) }.map(\.rawValue)
    }

    /// Reset cached data (useful for testing)
    static func resetCache() {
        _countriesByContinent = nil
    }
}
