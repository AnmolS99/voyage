import Foundation

/// Singleton cache for parsed GeoJSON country data
/// Avoids re-parsing the 920KB JSON file on every access
final class CountryDataCache {
    static let shared = CountryDataCache()

    private(set) lazy var countries: [GeoJSONCountry] = {
        GeoJSONParser.loadCountries()
    }()

    /// Set of all country names for quick lookup
    private(set) lazy var countryNames: Set<String> = {
        Set(countries.map { $0.name })
    }()

    private init() {}
}
