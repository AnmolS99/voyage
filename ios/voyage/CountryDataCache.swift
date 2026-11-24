import Foundation

/// Singleton cache for parsed GeoJSON country data
/// Avoids re-parsing the multi-MB JSON file on every access
final class CountryDataCache {
    static let shared = CountryDataCache()

    /// All parsed countries. Parsing happens when `shared` is first touched;
    /// static-let initialization makes that thread-safe.
    let countries: [GeoJSONCountry]

    /// Set of all country names for quick lookup
    let countryNames: Set<String>

    private let countriesByName: [String: GeoJSONCountry]
    private let countriesByIsoCode: [String: GeoJSONCountry]

    /// The country called `name`, or nil. Android `countryNamed`.
    func countryNamed(_ name: String) -> GeoJSONCountry? {
        countriesByName[name]
    }

    /// The country with this ISO 3166-1 alpha-2 code, or nil. Android
    /// `countryWithIsoCode`.
    func countryWithIsoCode(_ code: String) -> GeoJSONCountry? {
        countriesByIsoCode[code]
    }

    /// Cached country highlights keyed by ISO code
    private(set) lazy var countryHighlights: [String: CountryHighlights] = {
        CountryHighlightsParser.loadHighlights()
    }()

    /// Other names each country is searched by, keyed by ISO code
    /// (`country_aliases.json`, shared with Android): its other names first, then
    /// its former names, then its parts.
    private(set) lazy var countryAliases: [String: [CountryAlias]] = {
        struct Entry: Decodable {
            var names: [String]?
            var formerly: [String]?
            var includes: [String]?
        }
        guard let url = Bundle.main.url(forResource: "country_aliases", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let entries = try? JSONDecoder().decode([String: Entry].self, from: data) else {
            print("Failed to load country aliases")
            return [:]
        }
        return entries.mapValues { entry in
            (entry.names ?? []).map { CountryAlias(text: $0, kind: .name) }
                + (entry.formerly ?? []).map { CountryAlias(text: $0, kind: .formerName) }
                + (entry.includes ?? []).map { CountryAlias(text: $0, kind: .part) }
        }
    }()

    /// The other names `country` goes by — what search finds it by besides its name and ISO code.
    func aliases(for country: GeoJSONCountry) -> [CountryAlias] {
        country.flagCode.flatMap { countryAliases[$0] } ?? []
    }

    /// Which countries border each other and how far apart they are
    /// (`neighbors.json` and `border_distances.json`, shared with Android and
    /// Postgres). Parsed on first use. See `CountryGraph`.
    private(set) lazy var countryGraph: CountryGraph = CountryGraph.load() ?? .empty

    /// The countries bordering `isoCode`, sorted. Android `neighbors`.
    func neighbors(of isoCode: String) -> [String] {
        countryGraph.neighbors(of: isoCode)
    }

    /// km between two countries' outlines, 0 for neighbours. Android `borderDistanceKm`.
    func borderDistanceKm(_ a: String, _ b: String) -> Int? {
        countryGraph.borderDistanceKm(a, b)
    }

    /// Every shortest route between two countries, ends included. Android `shortestRoutes`.
    func shortestRoutes(from start: String, to goal: String) -> [[String]] {
        countryGraph.shortestRoutes(from: start, to: goal)
    }

    /// Kicks off GeoJSON parsing (and hit-test index building) on a background
    /// queue so it overlaps the rest of app startup instead of blocking the
    /// first globe/map render.
    static func prewarm() {
        DispatchQueue.global(qos: .userInitiated).async {
            _ = CountryHitTester.shared
        }
    }

    private init() {
        countries = GeoJSONParser.loadCountries()
        countryNames = Set(countries.map { $0.name })
        // The first of any duplicates, as the `first { }` searches these replace found
        countriesByName = Dictionary(countries.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        countriesByIsoCode = Dictionary(
            countries.compactMap { country in country.flagCode.map { ($0, country) } },
            uniquingKeysWith: { first, _ in first }
        )
    }
}
