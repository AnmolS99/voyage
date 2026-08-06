import Foundation

/// Drives one "Name the Capital" sweep on top of the shared region-sweep
/// engine (`RegionSweepGameViewModel`): each country in the region is shown
/// (flag + name) and the player types its capital, with one guess per country.
final class NameCapitalGameViewModel: RegionSweepGameViewModel {
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

    /// The capital of the current target country — the expected answer.
    override var currentAnswer: String? {
        currentTarget.flatMap(capitalLookup)
    }

    func capital(of country: String) -> String? {
        capitalLookup(country)
    }

    override func freshQueue() -> [String] {
        super.freshQueue().filter { capitalLookup($0) != nil }
    }
}
