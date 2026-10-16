package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The country search field's matching and ordering, over the real country list. */
class CountrySearchIndexTest {

    private val cache = SharedFiles.countryDataCache()
    private val countries = cache.countries
    private val index = CountrySearchIndex.ofCountries(countries, cache)

    private fun search(query: String): List<String> = index.search(query).map { it.item.name }

    /** The alias shown for [country] when searching [query], or null. */
    private fun aliasShown(query: String, country: String): String? =
        matchedAlias(query, country)?.text

    private fun matchedAlias(query: String, country: String): CountryAlias? =
        index.search(query).first { it.item.name == country }.matchedAlias

    @Test
    fun `a blank query lists every country alphabetically`() {
        val all = search("")
        assertEquals(countries.size, all.size)
        assertEquals(all.sortedBy { CountrySearchIndex.normalize(it) }, all)
        assertEquals("Afghanistan", all.first())
        assertEquals(all, search("   "))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(search("norway"), search("NoRwAy"))
        assertTrue("Norway" in search("norway"))
    }

    @Test
    fun `names starting with the query come before names merely containing it`() {
        // Prefix matches first, then substring matches, each alphabetical.
        assertEquals(
            listOf("Guinea", "Guinea Bissau", "Equatorial Guinea", "Papua New Guinea"),
            search("guinea"),
        )
    }

    @Test
    fun `accents are folded so an English keyboard finds every country`() {
        assertEquals(listOf("Türkiye"), search("turkiye"))
        // Türkiye sorts ahead of Turkmenistan once the umlaut is folded away.
        assertEquals(listOf("Türkiye", "Turkmenistan"), search("türk"))
        assertEquals(listOf("Côte d'Ivoire"), search("cote d"))
    }

    @Test
    fun `the query is trimmed`() {
        assertEquals(search("japan"), search("  japan "))
    }

    @Test
    fun `a query nothing matches returns nothing`() {
        assertTrue(search("atlantis").isEmpty())
    }

    @Test
    fun `normalize folds letters NFD leaves alone`() {
        assertEquals("o", CountrySearchIndex.normalize("Ø"))
        assertEquals("aeoe", CountrySearchIndex.normalize("ÆŒ"))
        assertEquals("ss", CountrySearchIndex.normalize("ß"))
        assertEquals("turkiye", CountrySearchIndex.normalize("Türkiye"))
    }

    @Test
    fun `the index is generic over what it searches`() {
        val capitals = CountrySearchIndex(countries.mapNotNull { it.capital }, name = { it.name })
        assertEquals(listOf("Oslo"), capitals.search("oslo").map { it.item.name })
    }

    // ---- Aliases ----

    @Test
    fun `short forms, informal names and constituent countries find the United Kingdom`() {
        for (query in listOf("uk", "UK", "britain", "Great Britain", "england", "Scot", "wales")) {
            assertTrue("$query finds the UK", "United Kingdom" in search(query))
        }
        assertEquals("England", aliasShown("eng", "United Kingdom"))
        assertEquals("UK", aliasShown("uk", "United Kingdom"))
    }

    @Test
    fun `former and native names find the country`() {
        assertEquals(listOf("Myanmar"), search("burma"))
        assertEquals(listOf("Türkiye"), search("turkey"))
        assertEquals(listOf("Eswatini"), search("swaziland"))
        assertEquals(listOf("Germany"), search("deutschland"))
        assertEquals(listOf("Spain"), search("espana"))
        assertEquals(listOf("Japan"), search("nippon"))
        assertEquals("Ivory Coast", aliasShown("ivory", "Côte d'Ivoire"))
    }

    @Test
    fun `the ISO code finds the country`() {
        assertEquals("GB", aliasShown("gb", "United Kingdom"))
        // "jp" is in no name and no other alias, so the code alone finds Japan.
        assertEquals(listOf("Japan"), search("jp"))
        assertEquals("JP", aliasShown("jp", "Japan"))
    }

    @Test
    fun `a query that is an alias puts its country first`() {
        // Ukraine starts with "uk", but "uk" *is* the United Kingdom.
        assertEquals(listOf("United Kingdom", "Ukraine"), search("uk"))
        assertEquals(listOf("Central African Republic"), search("car").take(1))
    }

    @Test
    fun `an ISO code matches whole and never outranks a name`() {
        assertEquals(listOf("Israel", "Iceland"), search("is").take(2))
        assertEquals(listOf("Chad", "Chile", "China", "Switzerland"), search("ch").take(4))
        assertEquals("CH", aliasShown("ch", "Switzerland"))
    }

    @Test
    fun `a name starting with the query outranks an alias starting with it`() {
        // "us" starts no name, so the USA (alias "USA") leads the names merely
        // containing it, such as Australia.
        assertEquals("United States of America", search("us").first())
        assertTrue(search("us").indexOf("Australia") > 0)
        // Ireland itself first; the UK follows through "Northern Ireland".
        assertEquals(listOf("Ireland", "United Kingdom"), search("ireland"))
        assertEquals("Northern Ireland", aliasShown("ireland", "United Kingdom"))
    }

    @Test
    fun `an alias matches at the start of a word, not inside one`() {
        // England ends in "land", but "land" must not drag the UK in.
        assertTrue("United Kingdom" !in search("land"))
        assertTrue("United Kingdom" in search("ireland"))
    }

    @Test
    fun `a single letter does not search aliases`() {
        // Every country starting with S, and those containing it — but not Sweden
        // twice, nor the UK by way of "Scotland".
        val results = search("s")
        assertEquals(results.distinct(), results)
        assertTrue(results.all { CountrySearchIndex.normalize(it).contains("s") })
    }

    @Test
    fun `each alias says what it is to the country`() {
        // The UK is known as the UK, but it *includes* Scotland.
        assertEquals(CountryAlias("UK", CountryAlias.Kind.NAME), matchedAlias("uk", "United Kingdom"))
        assertEquals(CountryAlias("Scotland", CountryAlias.Kind.PART), matchedAlias("scot", "United Kingdom"))
        assertEquals(CountryAlias("Hawaii", CountryAlias.Kind.PART), matchedAlias("hawaii", "United States of America"))
        assertEquals(CountryAlias("Burma", CountryAlias.Kind.FORMER_NAME), matchedAlias("burma", "Myanmar"))
        assertEquals(CountryAlias("GB", CountryAlias.Kind.CODE), matchedAlias("gb", "United Kingdom"))
    }

    @Test
    fun `a match the name already explains shows no alias`() {
        assertNull(aliasShown("congo", "Democratic Republic of the Congo"))
        assertNull(aliasShown("united", "United Kingdom"))
    }

    @Test
    fun `hyphens, full stops and typographic apostrophes are folded`() {
        assertEquals(listOf("Timor-Leste"), search("timor leste"))
        assertEquals(listOf("Guinea Bissau"), search("guinea-bissau"))
        assertEquals(listOf("Saint Lucia"), search("st. lucia"))
        assertEquals(listOf("Côte d'Ivoire"), search("cote d\u2019ivoire"))
    }

    @Test
    fun `every alias is keyed by a real country and names no other country`() {
        val isoCodes = countries.mapNotNull { it.isoCode }.toSet()
        val names = countries.map { CountrySearchIndex.normalize(it.name) }.toSet()
        val seen = mutableMapOf<String, String>()
        for ((isoCode, aliases) in cache.countryAliases) {
            assertTrue("$isoCode is not a country in world.geojson", isoCode in isoCodes)
            for (alias in aliases.map { it.text }) {
                val normalized = CountrySearchIndex.normalize(alias)
                assertTrue("$isoCode: $alias is already a country name", normalized !in names)
                val owner = seen.put(normalized, isoCode)
                assertNull("$alias is an alias of both $owner and $isoCode", owner)
            }
        }
    }

    @Test
    fun `the guess field's country names are found by alias too`() {
        val names = CountrySearchIndex.ofCountryNames(cache)
        assertEquals(listOf("Myanmar"), names.search("burma").map { it.item })
        assertEquals("Burma", names.search("burma").single().matchedAlias?.text)
    }
}
