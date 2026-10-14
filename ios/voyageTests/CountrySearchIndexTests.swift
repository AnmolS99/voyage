import XCTest
@testable import voyage

/// The country search's matching and ordering, over the real country list —
/// mirrors Android's `CountrySearchIndexTest`, so both platforms hold the same rules.
final class CountrySearchIndexTests: XCTestCase {

    private static let cache = CountryDataCache.shared
    private static let index = CountrySearchIndex<GeoJSONCountry>.countries(cache.countries)

    private func search(_ query: String) -> [String] {
        Self.index.search(query).map(\.item.name)
    }

    /// The alias shown for `country` when searching `query`, or nil.
    private func aliasShown(_ query: String, _ country: String) -> String? {
        matchedAlias(query, country)?.text
    }

    private func matchedAlias(_ query: String, _ country: String) -> CountryAlias? {
        Self.index.search(query).first { $0.item.name == country }?.matchedAlias
    }

    func testBlankQueryListsEveryCountryAlphabetically() {
        let all = search("")
        XCTAssertEqual(all.count, Self.cache.countries.count)
        XCTAssertEqual(all.first, "Afghanistan")
        XCTAssertEqual(all, search("   "))
    }

    func testNamesStartingWithQueryComeFirst() {
        XCTAssertEqual(search("guinea"),
                       ["Guinea", "Guinea Bissau", "Equatorial Guinea", "Papua New Guinea"])
    }

    func testAccentsAreFolded() {
        XCTAssertEqual(search("turkiye"), ["Türkiye"])
        XCTAssertEqual(search("türk"), ["Türkiye", "Turkmenistan"])
        XCTAssertEqual(search("cote d"), ["Côte d'Ivoire"])
        XCTAssertEqual(CountrySearchIndexText.normalize("ÆŒ"), "aeoe")
        XCTAssertEqual(CountrySearchIndexText.normalize("Ø"), "o")
    }

    // MARK: - Aliases

    func testShortFormsAndConstituentCountriesFindTheUnitedKingdom() {
        for query in ["uk", "UK", "britain", "Great Britain", "england", "Scot", "wales"] {
            XCTAssertTrue(search(query).contains("United Kingdom"), "\(query) finds the UK")
        }
        XCTAssertEqual(aliasShown("eng", "United Kingdom"), "England")
        XCTAssertEqual(aliasShown("uk", "United Kingdom"), "UK")
    }

    func testFormerAndNativeNamesFindTheCountry() {
        XCTAssertEqual(search("burma"), ["Myanmar"])
        XCTAssertEqual(search("turkey"), ["Türkiye"])
        XCTAssertEqual(search("swaziland"), ["Eswatini"])
        XCTAssertEqual(search("deutschland"), ["Germany"])
        XCTAssertEqual(search("espana"), ["Spain"])
        XCTAssertEqual(search("nippon"), ["Japan"])
        XCTAssertEqual(aliasShown("ivory", "Côte d'Ivoire"), "Ivory Coast")
    }

    func testISOCodeFindsTheCountry() {
        XCTAssertEqual(aliasShown("gb", "United Kingdom"), "GB")
        XCTAssertEqual(search("jp"), ["Japan"])
        XCTAssertEqual(aliasShown("jp", "Japan"), "JP")
    }

    func testQueryThatIsAnAliasPutsItsCountryFirst() {
        // Ukraine starts with "uk", but "uk" *is* the United Kingdom.
        XCTAssertEqual(search("uk"), ["United Kingdom", "Ukraine"])
        XCTAssertEqual(search("car").first, "Central African Republic")
    }

    func testISOCodeMatchesWholeAndNeverOutranksAName() {
        XCTAssertEqual(Array(search("is").prefix(2)), ["Israel", "Iceland"])
        XCTAssertEqual(Array(search("ch").prefix(4)), ["Chad", "Chile", "China", "Switzerland"])
        XCTAssertEqual(aliasShown("ch", "Switzerland"), "CH")
    }

    func testNamePrefixOutranksAliasPrefix() {
        XCTAssertEqual(search("us").first, "United States of America")
        XCTAssertGreaterThan(search("us").firstIndex(of: "Australia") ?? -1, 0)
        XCTAssertEqual(search("ireland"), ["Ireland", "United Kingdom"])
        XCTAssertEqual(aliasShown("ireland", "United Kingdom"), "Northern Ireland")
    }

    func testAliasMatchesAtWordStartOnly() {
        XCTAssertFalse(search("land").contains("United Kingdom"))
    }

    func testSingleLetterDoesNotSearchAliases() {
        let results = search("s")
        XCTAssertEqual(results.count, Set(results).count)
        XCTAssertTrue(results.allSatisfy { CountrySearchIndexText.normalize($0).contains("s") })
    }

    func testEachAliasSaysWhatItIsToTheCountry() {
        // The UK is known as the UK, but it *includes* Scotland.
        XCTAssertEqual(matchedAlias("uk", "United Kingdom"), CountryAlias(text: "UK", kind: .name))
        XCTAssertEqual(matchedAlias("scot", "United Kingdom"), CountryAlias(text: "Scotland", kind: .part))
        XCTAssertEqual(matchedAlias("hawaii", "United States of America"), CountryAlias(text: "Hawaii", kind: .part))
        XCTAssertEqual(matchedAlias("burma", "Myanmar"), CountryAlias(text: "Burma", kind: .formerName))
        XCTAssertEqual(matchedAlias("gb", "United Kingdom"), CountryAlias(text: "GB", kind: .code))
        XCTAssertEqual(matchedAlias("scot", "United Kingdom")?.label, "Includes Scotland")
        XCTAssertEqual(matchedAlias("burma", "Myanmar")?.label, "Formerly Burma")
    }

    func testMatchTheNameExplainsShowsNoAlias() {
        XCTAssertNil(aliasShown("congo", "Democratic Republic of the Congo"))
        XCTAssertNil(aliasShown("united", "United Kingdom"))
    }

    func testHyphensFullStopsAndTypographicApostrophesAreFolded() {
        XCTAssertEqual(search("timor leste"), ["Timor-Leste"])
        XCTAssertEqual(search("guinea-bissau"), ["Guinea Bissau"])
        XCTAssertEqual(search("st. lucia"), ["Saint Lucia"])
        XCTAssertEqual(search("cote d\u{2019}ivoire"), ["Côte d'Ivoire"])
    }

    func testEveryAliasIsKeyedByARealCountryAndNamesNoOther() {
        let codes = Set(Self.cache.countries.compactMap(\.flagCode))
        let names = Set(Self.cache.countries.map { CountrySearchIndexText.normalize($0.name) })
        var seen: [String: String] = [:]
        XCTAssertFalse(Self.cache.countryAliases.isEmpty, "country_aliases.json is bundled")
        for (code, aliases) in Self.cache.countryAliases {
            XCTAssertTrue(codes.contains(code), "\(code) is not a country in world.geojson")
            for alias in aliases.map(\.text) {
                let normalized = CountrySearchIndexText.normalize(alias)
                XCTAssertFalse(names.contains(normalized), "\(code): \(alias) is already a country name")
                if let owner = seen.updateValue(code, forKey: normalized) {
                    XCTFail("\(alias) is an alias of both \(owner) and \(code)")
                }
            }
        }
    }
}
