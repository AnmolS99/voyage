package com.anmol.voyage.data

import java.text.Normalizer
import java.util.Locale

/** A search result, and the alternative name that found it when its own name did not. */
data class SearchMatch<T>(val item: T, val matchedAlias: String? = null)

/**
 * Name search over a fixed set of items, built once and queried per keystroke.
 *
 * Names are normalized up front — folded to lowercase with accents removed — so
 * a query typed on an English keyboard still finds `Türkiye` and
 * `Côte d'Ivoire`, and so the 207 names are not re-folded on every keystroke.
 *
 * An item can also be found by its aliases (`country_aliases.json`, shared with
 * iOS) and by its ISO code, so "uk", "gb", "britain" and "england" all find the
 * United Kingdom. Results rank in five tiers, each alphabetical:
 *
 * 1. an alias *is* the query — "uk" offers the United Kingdom before Ukraine;
 * 2. the name starts with the query — "guinea" offers Guinea before Papua New
 *    Guinea;
 * 3. an alias starts with the query, or the ISO code is the query — "us" offers
 *    the USA before Australia;
 * 4. the name contains the query;
 * 5. a word inside an alias starts with the query — "ireland" offers the United
 *    Kingdom, after Ireland itself, by way of Northern Ireland.
 *
 * The ISO code only ever matches whole, and never outranks a name: "is" lists
 * Israel before Iceland.
 *
 * Aliases are only consulted from two letters on: a single letter would pull in
 * every Sverige and Suomi alongside the names it already lists. A match found
 * through an alias carries it in [SearchMatch.matchedAlias] — unless the name
 * contains the query too, and so already explains itself.
 *
 * iOS's `CountrySearchIndex` follows the same rules, and `CountrySearchIndexTest`
 * mirrors its `CountrySearchIndexTests`, so a rule change is a two-platform change.
 *
 * Generic over the item so the challenges can index capitals with the same
 * matching rules.
 */
class CountrySearchIndex<T>(
    items: List<T>,
    name: (T) -> String,
    aliases: (T) -> List<String> = { emptyList() },
    code: (T) -> String? = { null },
) {

    private class Alias(val text: String, val normalized: String)

    private class Entry<T>(
        val item: T,
        val normalized: String,
        val aliases: List<Alias>,
        val code: Alias?,
    )

    private val entries: List<Entry<T>> =
        items.map { item ->
            val aliasList = aliases(item).map { Alias(it, normalize(it)) }
            // The code is matched whole, so it sits apart from the aliases.
            val codeAlias = code(item)?.let { Alias(it, normalize(it)) }
            Entry(item, normalize(name(item)), aliasList, codeAlias)
        }.sortedBy { it.normalized }

    /** Every item, in the order results are listed — alphabetical, accents folded. */
    val all: List<T> = entries.map { it.item }

    /** Items matching [query], ranked as the class describes. A blank query matches everything. */
    fun search(query: String): List<SearchMatch<T>> {
        val normalized = normalize(query.trim())
        if (normalized.isEmpty()) return entries.map { SearchMatch(it.item) }

        val tiers = List(TIER_COUNT) { mutableListOf<SearchMatch<T>>() }
        for (entry in entries) {
            val nameContains = entry.normalized.contains(normalized)
            val (tier, alias) = when {
                normalized.length < MIN_ALIAS_QUERY -> when {
                    entry.normalized.startsWith(normalized) -> NAME_PREFIX to null
                    nameContains -> NAME_CONTAINS to null
                    else -> continue
                }
                else -> {
                    val exact = entry.aliases.firstOrNull { it.normalized == normalized }
                    val prefix = entry.aliases.firstOrNull { it.normalized.startsWith(normalized) }
                        ?: entry.code?.takeIf { it.normalized == normalized }
                    val word = " $normalized"
                    when {
                        exact != null -> EXACT_ALIAS to exact
                        entry.normalized.startsWith(normalized) -> NAME_PREFIX to null
                        prefix != null -> ALIAS_PREFIX to prefix
                        nameContains -> NAME_CONTAINS to null
                        else -> ALIAS_WORD to
                            (entry.aliases.firstOrNull { " ${it.normalized}".contains(word) } ?: continue)
                    }
                }
            }
            tiers[tier].add(SearchMatch(entry.item, alias?.text?.takeUnless { nameContains }))
        }
        return tiers.flatten()
    }

    companion object {

        private const val EXACT_ALIAS = 0
        private const val NAME_PREFIX = 1
        private const val ALIAS_PREFIX = 2
        private const val NAME_CONTAINS = 3
        private const val ALIAS_WORD = 4
        private const val TIER_COUNT = 5

        /** The shortest query aliases are matched against. */
        const val MIN_ALIAS_QUERY = 2

        /** Indexes parsed countries by name and alias — what the search UI uses. */
        fun ofCountries(
            countries: List<GeoJsonCountry>,
            cache: CountryDataCache,
        ): CountrySearchIndex<GeoJsonCountry> =
            CountrySearchIndex(
                countries,
                name = { it.name },
                aliases = cache::aliases,
                code = { it.isoCode },
            )

        /**
         * Indexes country *names* by name and alias — the suggestions of a guess
         * field, which hands back the name to submit.
         */
        fun ofCountryNames(cache: CountryDataCache): CountrySearchIndex<String> =
            CountrySearchIndex(
                cache.countries.map { it.name },
                name = { it },
                aliases = { name -> cache.countryNamed(name)?.let(cache::aliases).orEmpty() },
                code = { name -> cache.countryNamed(name)?.isoCode },
            )

        /**
         * Lowercases [text] and strips accents, so `Türkiye` and `turkiye` compare
         * equal. Hyphens read as spaces and full stops vanish, so "timor leste"
         * finds Timor-Leste and "st. lucia" an alias "St Lucia"; a typographic
         * apostrophe — what iOS keyboards type — reads as a straight one.
         *
         * NFD splits a letter into its base plus combining marks, which are then
         * dropped; letters that have no decomposition — `ø`, `æ`, `ß` — are folded
         * by the table first, since NFD leaves them untouched.
         */
        fun normalize(text: String): String {
            val lowercased = text.lowercase(Locale.ROOT)
            val folded = StringBuilder(lowercased.length)
            for (char in lowercased) {
                val replacement = FOLDED[char]
                if (replacement != null) folded.append(replacement) else folded.append(char)
            }
            return Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
        }

        /** Combining marks left behind by NFD (Unicode category Mn). */
        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        private val FOLDED = mapOf(
            'ø' to "o",
            'æ' to "ae",
            'œ' to "oe",
            'ß' to "ss",
            'ð' to "d",
            'đ' to "d",
            'þ' to "th",
            'ł' to "l",
            '-' to " ",
            '.' to "",
            '\u2019' to "'",
        )
    }
}
