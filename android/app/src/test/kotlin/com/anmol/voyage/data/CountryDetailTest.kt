package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the details sheet shows for a country, assembled from the same two shared
 * files iOS reads in `CountryExploreView`.
 */
class CountryDetailTest {

    private val cache = SharedFiles.countryDataCache()

    private fun detail(name: String) = CountryDetail.of(cache, name)

    @Test
    fun `a country carries its flag, capital, continent and highlights`() {
        val france = requireNotNull(detail("France"))
        assertEquals("FR", france.isoCode)
        assertEquals("🇫🇷", france.flag)
        assertEquals("Paris", france.capital)
        assertEquals("Europe", france.continent)
        assertEquals(
            listOf("Paris", "Lyon", "Nice", "Marseille", "Strasbourg"),
            france.cities,
        )
        assertTrue("Eiffel Tower" in france.attractions)
        assertTrue(france.hasHighlights)
    }

    @Test
    fun `highlights are matched by ISO code, not by name`() {
        // Both are renames iOS also had to handle; the highlights file is keyed by
        // code, so a display name change cannot orphan them.
        val turkiye = requireNotNull(detail("Türkiye"))
        assertEquals("TR", turkiye.isoCode)
        assertTrue("Cappadocia" in turkiye.attractions)

        val coteDIvoire = requireNotNull(detail("Côte d'Ivoire"))
        assertEquals("CI", coteDIvoire.isoCode)
        assertEquals("Yamoussoukro", coteDIvoire.capital)
        assertTrue("Yamoussoukro" in coteDIvoire.cities)
    }

    @Test
    fun `a country without a capital still resolves`() {
        val antarctica = requireNotNull(detail("Antarctica"))
        assertNull(antarctica.capital)
        assertEquals("Antarctica", antarctica.continent)
        assertTrue(antarctica.hasHighlights)
    }

    @Test
    fun `microstates rendered as dots have details like anywhere else`() {
        val monaco = requireNotNull(detail("Monaco"))
        assertEquals("Monaco", monaco.capital)
        assertTrue(monaco.hasHighlights)
    }

    @Test
    fun `an unknown name has no detail`() {
        assertNull(detail("Atlantis"))
    }

    @Test
    fun `every country has highlights to show`() {
        val missing = cache.countries
            .map { CountryDetail.of(cache, it) }
            .filterNot { it.hasHighlights }
            .map { it.name }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every capital is spelled the same in both shared files`() {
        // The cities checklist badges the capital by matching its name, so the two
        // files have to agree on the spelling — they are edited separately, and a
        // mismatch would silently drop the badge rather than break anything loudly.
        val mismatched = cache.countries
            .map { CountryDetail.of(cache, it) }
            .filter { it.capital != null && it.capital !in it.cities }
            .map { "${it.name}: ${it.capital} not in ${it.cities}" }
        assertEquals(emptyList<String>(), mismatched)
    }
}
