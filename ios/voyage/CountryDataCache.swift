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

    /// Cached country highlights keyed by ISO code
    private(set) lazy var countryHighlights: [String: CountryHighlights] = {
        CountryHighlightsParser.loadHighlights()
    }()

    /// Other names each country goes by, keyed by ISO code — short forms, former
    /// names and native names (`country_aliases.json`, shared with Android).
    private(set) lazy var countryAliases: [String: [String]] = {
        guard let url = Bundle.main.url(forResource: "country_aliases", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let aliases = try? JSONDecoder().decode([String: [String]].self, from: data) else {
            print("Failed to load country aliases")
            return [:]
        }
        return aliases
    }()

    /// The other names `country` goes by — what search finds it by besides its name and ISO code.
    func aliases(for country: GeoJSONCountry) -> [String] {
        country.flagCode.flatMap { countryAliases[$0] } ?? []
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
    }
}
