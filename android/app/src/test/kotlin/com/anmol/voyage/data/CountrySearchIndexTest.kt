package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The country search field's matching and ordering, over the real country list. */
class CountrySearchIndexTest {

    private val countries = SharedFiles.countryDataCache().countries
    private val index = CountrySearchIndex.ofCountries(countries)

    private fun search(query: String): List<String> = index.search(query).map { it.name }

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
        val capitals = CountrySearchIndex(countries.mapNotNull { it.capital }) { it.name }
        assertEquals(listOf("Oslo"), capitals.search("oslo").map { it.name })
    }
}
