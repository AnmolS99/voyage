package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cache and the highlights data it exposes, over the real shared files. */
class CountryDataCacheTest {

    private val cache = SharedFiles.countryDataCache()

    @Test
    fun `country names cover every parsed country`() {
        assertEquals(cache.countries.size, cache.countryNames.size)
        assertTrue("Norway" in cache.countryNames)
    }

    @Test
    fun `lookups find countries by name and ISO code`() {
        val norway = cache.countryNamed("Norway")
        assertNotNull(norway)
        assertEquals("NO", norway!!.isoCode)
        assertEquals("Oslo", norway.capital?.name)
        assertEquals(norway.name, cache.countryWithIsoCode("NO")?.name)
        assertNull(cache.countryNamed("Atlantis"))
        assertNull(cache.countryWithIsoCode("ZZ"))
    }

    @Test
    fun `every country has highlights and every highlight has a country`() {
        val isoCodes = cache.countries.mapNotNull { it.isoCode }.toSet()
        assertEquals(
            "highlights without a country",
            emptySet<String>(),
            cache.countryHighlights.keys - isoCodes,
        )
        assertEquals(
            "countries without highlights",
            emptyList<String>(),
            cache.countries.filter { it.isoCode !in cache.countryHighlights.keys }.map { it.name },
        )
    }

    @Test
    fun `highlights list between one and five cities and attractions`() {
        cache.countryHighlights.forEach { (iso, highlights) ->
            assertTrue("$iso cities: ${highlights.cities.size}", highlights.cities.size in 1..5)
            assertTrue(
                "$iso attractions: ${highlights.attractions.size}",
                highlights.attractions.size in 1..5,
            )
        }
        assertEquals(
            listOf("Oslo", "Bergen", "Tromsø", "Stavanger", "Trondheim"),
            cache.highlights("NO")?.cities,
        )
    }
}
