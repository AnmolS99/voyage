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
