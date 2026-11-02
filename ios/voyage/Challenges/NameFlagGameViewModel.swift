import Foundation

/// Drives one "Name the Flag" sweep on top of the shared region-sweep engine
/// (`RegionSweepGameViewModel`): each country's flag is shown and the player
/// names the country. A typed guess counts when it is the country's name or
/// another name for it — "UK", "Great Britain", "Burma" — compared the way
/// search compares them, so "turkiye" names Türkiye. A part of a country
/// ("England", "Hawaii") or its ISO code is not a name for it and does not count.
///
/// Only exact names count, never the top suggestion: search ranks prefixes, and
/// "Ni" must not score Niger. Android's `NameFlagGame` follows the same rule.
final class NameFlagGameViewModel: RegionSweepGameViewModel {
    private let aliasLookup: (String) -> [CountryAlias]

    /// `countries` overrides the region's pool and `aliasLookup` the
    /// `country_aliases.json` source (both used by tests).
    init(region: ChallengeRegion,
         countries: [String]? = nil,
         statsStore: ChallengeStatsStore = .shared,
         aliasLookup: ((String) -> [CountryAlias])? = nil) {
        self.aliasLookup = aliasLookup ?? { name in
            let cache = CountryDataCache.shared
            return cache.countries.first { $0.name == name }.map(cache.aliases(for:)) ?? []
        }
        super.init(mode: .nameFlag, region: region, countries: countries, statsStore: statsStore)
    }

    /// The country's name when `guess` is its name or one of its other names,
    /// else nil — the banner names the country, not the alias typed.
    override func acceptedAnswer(for guess: String) -> String? {
        guard let target = currentTarget else { return nil }
        let typed = CountrySearchIndexText.normalize(guess.trimmingCharacters(in: .whitespacesAndNewlines))
        guard !typed.isEmpty else { return nil }
        let otherNames = aliasLookup(target)
            .filter { $0.kind == .name || $0.kind == .formerName }
            .map(\.text)
        let names = [target] + otherNames
        return names.contains { CountrySearchIndexText.normalize($0) == typed } ? target : nil
    }
}
