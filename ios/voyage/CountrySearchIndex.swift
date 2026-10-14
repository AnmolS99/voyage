import Foundation

/// One other name an item is found by, and what it is to the item — so a match
/// can say why it is listed: the UK is *known as* "UK" and *includes* Scotland;
/// it is not "known as Scotland".
struct CountryAlias: Equatable {
    enum Kind: Equatable {
        /// Another name for the whole country: "UK", "Deutschland".
        case name
        /// A name it no longer goes by: "Burma", "Swaziland".
        case formerName
        /// A part of it, searched for as if a country: "Scotland", "Hawaii".
        case part
        /// Its ISO 3166-1 alpha-2 code: "GB".
        case code
    }

    let text: String
    let kind: Kind

    /// Why a search listed a country its name does not explain: "Also known as
    /// UK", "Formerly Burma", "Includes Scotland", "Country code GB".
    var label: String {
        switch kind {
        case .name: "Also known as \(text)"
        case .formerName: "Formerly \(text)"
        case .part: "Includes \(text)"
        case .code: "Country code \(text)"
        }
    }
}

/// A search result, and the alias that found it when its own name did not.
struct SearchMatch<Item> {
    let item: Item
    var matchedAlias: CountryAlias? = nil
}

extension SearchMatch: Equatable where Item: Equatable {}

/// Name search over a fixed set of items, built once and queried per keystroke.
///
/// Names are normalized up front — folded to lowercase with accents removed — so
/// a query typed on an English keyboard still finds `Türkiye` and
/// `Côte d'Ivoire`.
///
/// An item can also be found by its aliases (`country_aliases.json`, shared with
/// Android) and by its ISO code, so "uk", "gb", "britain" and "england" all find
/// the United Kingdom. Results rank in five tiers, each alphabetical:
///
/// 1. an alias *is* the query — "uk" offers the United Kingdom before Ukraine;
/// 2. the name starts with the query — "guinea" offers Guinea before Papua New
///    Guinea;
/// 3. an alias starts with the query, or the ISO code is the query — "us" offers
///    the USA before Australia;
/// 4. the name contains the query;
/// 5. a word inside an alias starts with the query — "ireland" offers the United
///    Kingdom, after Ireland itself, by way of Northern Ireland.
///
/// The ISO code only ever matches whole, and never outranks a name: "is" lists
/// Israel before Iceland.
///
/// Aliases are only consulted from two letters on: a single letter would pull in
/// every Sverige and Suomi alongside the names it already lists. A match found
/// through an alias carries it in `matchedAlias` — unless the name contains the
/// query too, and so already explains itself.
///
/// A port of Android's `CountrySearchIndex`; `CountrySearchIndexTests` mirrors its
/// `CountrySearchIndexTest`, so a rule change is a two-platform change.
struct CountrySearchIndex<Item> {
    private struct Alias {
        let alias: CountryAlias
        let normalized: String
    }

    private struct Entry {
        let item: Item
        let normalized: String
        let aliases: [Alias]
        /// Matched whole, so it sits apart from the aliases.
        let code: Alias?
    }

    /// The shortest query aliases are matched against.
    static var minAliasQuery: Int { 2 }

    private let entries: [Entry]

    init(
        _ items: [Item],
        name: (Item) -> String,
        aliases: (Item) -> [CountryAlias] = { _ in [] },
        code: (Item) -> String? = { _ in nil }
    ) {
        func alias(_ alias: CountryAlias) -> Alias {
            Alias(alias: alias, normalized: CountrySearchIndexText.normalize(alias.text))
        }
        entries = items
            .map { item in
                Entry(
                    item: item,
                    normalized: CountrySearchIndexText.normalize(name(item)),
                    aliases: aliases(item).map(alias),
                    code: code(item).map { alias(CountryAlias(text: $0, kind: .code)) }
                )
            }
            .sorted { $0.normalized < $1.normalized }
    }

    /// Every item, in the order results are listed — alphabetical, accents folded.
    var all: [Item] { entries.map(\.item) }

    /// Items matching `query`, ranked as the type describes. A blank query matches everything.
    func search(_ query: String) -> [SearchMatch<Item>] {
        let normalized = CountrySearchIndexText.normalize(
            query.trimmingCharacters(in: .whitespaces)
        )
        guard !normalized.isEmpty else { return entries.map { SearchMatch(item: $0.item) } }

        let word = " " + normalized
        var tiers = Array(repeating: [SearchMatch<Item>](), count: 5)
        for entry in entries {
            let nameContains = entry.normalized.contains(normalized)
            let namePrefix = entry.normalized.hasPrefix(normalized)
            let tier: Int
            var alias: Alias?
            if normalized.count < Self.minAliasQuery {
                guard nameContains else { continue }
                tier = namePrefix ? 1 : 3
            } else if let exact = entry.aliases.first(where: { $0.normalized == normalized }) {
                tier = 0
                alias = exact
            } else if namePrefix {
                tier = 1
            } else if let prefix = entry.aliases.first(where: { $0.normalized.hasPrefix(normalized) })
                        ?? entry.code.flatMap({ $0.normalized == normalized ? $0 : nil }) {
                tier = 2
                alias = prefix
            } else if nameContains {
                tier = 3
            } else if let inWord = entry.aliases.first(where: { (" " + $0.normalized).contains(word) }) {
                tier = 4
                alias = inWord
            } else {
                continue
            }
            tiers[tier].append(SearchMatch(item: entry.item, matchedAlias: nameContains ? nil : alias?.alias))
        }
        return tiers.flatMap { $0 }
    }
}

extension CountrySearchIndex where Item == GeoJSONCountry {
    /// Indexes countries by name and alias — what the Countries list uses.
    static func countries(_ countries: [GeoJSONCountry], cache: CountryDataCache = .shared) -> Self {
        CountrySearchIndex(countries, name: \.name, aliases: cache.aliases(for:), code: \.flagCode)
    }
}

extension CountrySearchIndex where Item == String {
    /// Indexes country *names* by name and alias — the suggestions of a guess
    /// field, which hands back the name to submit.
    static func countryNames(cache: CountryDataCache = .shared) -> Self {
        let byName = Dictionary(cache.countries.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        return CountrySearchIndex(
            cache.countries.map(\.name),
            name: { $0 },
            aliases: { byName[$0].map(cache.aliases(for:)) ?? [] },
            code: { byName[$0]?.flagCode }
        )
    }

    /// Indexes plain strings by themselves — capitals, which have no aliases.
    static func plain(_ items: [String]) -> Self {
        CountrySearchIndex(items, name: { $0 })
    }
}

/// The text folding `CountrySearchIndex` matches with — outside the generic type
/// so it can be called without naming an item type.
enum CountrySearchIndexText {
    /// Lowercases `text` and strips accents, so `Türkiye` and `turkiye` compare
    /// equal. Hyphens read as spaces and full stops vanish, so "timor leste"
    /// finds Timor-Leste and "st. lucia" an alias "St Lucia"; a typographic
    /// apostrophe — what iOS keyboards type — reads as a straight one.
    ///
    /// Canonical decomposition splits a letter into its base plus combining
    /// marks, which are then dropped; letters that have no decomposition — `ø`,
    /// `æ`, `ß` — are folded by the table first, as on Android.
    static func normalize(_ text: String) -> String {
        var folded = ""
        for scalar in text.lowercased().unicodeScalars {
            if let replacement = foldedScalars[scalar] {
                folded += replacement
            } else {
                folded.unicodeScalars.append(scalar)
            }
        }
        var stripped = String.UnicodeScalarView()
        for scalar in folded.decomposedStringWithCanonicalMapping.unicodeScalars
        where scalar.properties.generalCategory != .nonspacingMark {
            stripped.append(scalar)
        }
        return String(stripped)
    }

    private static let foldedScalars: [Unicode.Scalar: String] = [
        "ø": "o",
        "æ": "ae",
        "œ": "oe",
        "ß": "ss",
        "ð": "d",
        "đ": "d",
        "þ": "th",
        "ł": "l",
        "-": " ",
        ".": "",
        "\u{2019}": "'",
    ]
}
