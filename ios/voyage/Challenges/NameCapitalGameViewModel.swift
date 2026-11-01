import Foundation

/// Drives one "Name the Capital" sweep on top of the shared region-sweep
/// engine (`RegionSweepGameViewModel`): each country in the region is shown
/// (flag + name) and the player types its capital, with one guess per country.
/// A country with several capitals (Bolivia: La Paz and Sucre) takes any of them.
final class NameCapitalGameViewModel: RegionSweepGameViewModel {
    private let capitalLookup: (String) -> [String]

    /// `countries` overrides the region's pool and `capitalLookup` the
    /// GeoJSON capital source (both used by tests). The lookup returns every
    /// capital of a country, the main one first — empty when it has none.
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared,
         capitalLookup: ((String) -> [String])? = nil) {
        let lookup = capitalLookup ?? { name in
            CountryDataCache.shared.countries.first { $0.name == name }?.capitalNames ?? []
        }
        self.capitalLookup = lookup
        // A country without capital data could never be answered — drop it
        let pool = (countries ?? region.countries.shuffled()).filter { !lookup($0).isEmpty }
        super.init(mode: .nameCapital, region: region, countries: pool, statsStore: statsStore)
    }

    /// Every capital of the current target country — each an accepted answer.
    override var currentAnswers: [String] {
        currentTarget.map(capitalLookup) ?? []
    }

    /// Every capital of `country`, the main one first.
    func capitals(of country: String) -> [String] {
        capitalLookup(country)
    }

    override func freshQueue() -> [String] {
        super.freshQueue().filter { !capitalLookup($0).isEmpty }
    }
}
