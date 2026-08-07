package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the iOS `ContinentDataTests` against the same GeoJSON grouping. */
class ContinentIndexTest {

    private val cache = SharedFiles.countryDataCache()
    private val continents = cache.continents

    @Test
    fun `display names and medals match iOS`() {
        assertEquals(
            listOf("Africa", "Asia", "Europe", "North America", "South America", "Oceania", "Antarctica"),
            Continent.entries.map { it.displayName },
        )
        assertEquals(
            listOf("🦁", "🐉", "🏰", "🦅", "🦜", "🐨", "🐧"),
            Continent.entries.map { it.medal },
        )
        assertEquals(Continent.NORTH_AMERICA, Continent.fromRaw("North America"))
        assertNull(Continent.fromRaw("Atlantis"))
    }

    @Test
    fun `every country is grouped under exactly one continent`() {
        val grouped = continents.countriesByContinent.values.flatten()
        assertEquals(cache.countries.size, grouped.size)
        assertEquals(grouped.size, grouped.distinct().size)
        assertEquals(cache.countryNames, grouped.toSet())
    }

    @Test
    fun `continent country counts are in the expected range`() {
        val counts = Continent.entries.associateWith { continents.countries(of = it).size }
        assertTrue("Europe: ${counts[Continent.EUROPE]}", counts.getValue(Continent.EUROPE) > 30)
        assertTrue("Asia: ${counts[Continent.ASIA]}", counts.getValue(Continent.ASIA) > 35)
        assertTrue("Africa: ${counts[Continent.AFRICA]}", counts.getValue(Continent.AFRICA) > 40)
        assertTrue(counts.getValue(Continent.NORTH_AMERICA) > 15)
        assertTrue(counts.getValue(Continent.SOUTH_AMERICA) > 10)
        assertTrue(counts.getValue(Continent.OCEANIA) > 10)
        assertTrue(counts.getValue(Continent.ANTARCTICA) <= 2)
    }

    @Test
    fun `continent lookup round-trips for every continent`() {
        Continent.entries.forEach { continent ->
            val country = continents.countries(of = continent).firstOrNull() ?: return@forEach
            assertEquals(continent, continents.continentOf(country))
        }
        assertNull(continents.continentOf("NonexistentCountry"))
    }

    @Test
    fun `visited countries are filtered per continent`() {
        val europe = continents.countries(of = Continent.EUROPE).take(2)
        val asia = continents.countries(of = Continent.ASIA).take(1)
        val visited = (europe + asia).toSet()

        assertEquals(2, continents.visitedCountries(Continent.EUROPE, visited).size)
        assertEquals(1, continents.visitedCountries(Continent.ASIA, visited).size)
        assertTrue(continents.visitedCountries(Continent.AFRICA, visited).isEmpty())
        assertTrue(continents.visitedCountries(Continent.EUROPE, emptySet()).isEmpty())
    }

    @Test
    fun `a single country marks its continent visited`() {
        val kenya = setOf("Kenya")
        assertTrue(continents.hasVisited(Continent.AFRICA, kenya))
        assertFalse(continents.hasVisited(Continent.EUROPE, kenya))
        assertEquals(listOf("Africa"), continents.visitedContinentNames(kenya))
        assertEquals(
            listOf("Asia", "Europe", "North America", "South America", "Oceania", "Antarctica"),
            continents.remainingContinentNames(kenya),
        )
    }

    @Test
    fun `visited and remaining continents partition all seven in enum order`() {
        val visited = setOf("Kenya", "Japan", "Norway")
        val names = continents.visitedContinentNames(visited) + continents.remainingContinentNames(visited)
        assertEquals(Continent.entries.size, names.size)
        assertEquals(listOf("Africa", "Asia", "Europe"), continents.visitedContinentNames(visited))
    }
}
