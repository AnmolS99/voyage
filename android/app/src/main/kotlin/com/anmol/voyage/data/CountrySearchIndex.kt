package com.anmol.voyage.data

import java.text.Normalizer
import java.util.Locale

/**
 * Name search over a fixed set of items, built once and queried per keystroke.
 *
 * Names are normalized up front — folded to lowercase with accents removed — so
 * a query typed on an English keyboard still finds `Türkiye` and
 * `Côte d'Ivoire`, and so the 206 names are not re-folded on every keystroke.
 *
 * Ranking differs from the iOS country list, which filters and leaves the
 * result alphabetical: here names *starting* with the query come first, so
 * typing "ind" offers India before the British Indian Ocean Territory. The
 * cross-platform rules that must not drift are the parser's and the renderers';
 * result ordering in a search field is not one of them.
 *
 * Generic over the item so the Daily Challenge (Phase 9) can index capitals with
 * the same matching rules.
 */
class CountrySearchIndex<T>(items: List<T>, name: (T) -> String) {

    private class Entry<T>(val item: T, val normalized: String)

    private val entries: List<Entry<T>> =
        items.map { Entry(it, normalize(name(it))) }.sortedBy { it.normalized }

    /** Every item, in the order results are listed — alphabetical, accents folded. */
    val all: List<T> = entries.map { it.item }

    /**
     * Items matching [query]: those whose name starts with it first, then those
     * that merely contain it, each alphabetical. A blank query matches everything.
     */
    fun search(query: String): List<T> {
        val normalized = normalize(query.trim())
        if (normalized.isEmpty()) return all

        val startsWith = mutableListOf<T>()
        val contains = mutableListOf<T>()
        for (entry in entries) {
            when {
                entry.normalized.startsWith(normalized) -> startsWith.add(entry.item)
                entry.normalized.contains(normalized) -> contains.add(entry.item)
            }
        }
        return startsWith + contains
    }

    companion object {

        /** Indexes parsed countries by name — what the search UI uses. */
        fun ofCountries(countries: List<GeoJsonCountry>): CountrySearchIndex<GeoJsonCountry> =
            CountrySearchIndex(countries) { it.name }

        /**
         * Lowercases [text] and strips accents, so `Türkiye` and `turkiye` compare
         * equal.
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
        )
    }
}
