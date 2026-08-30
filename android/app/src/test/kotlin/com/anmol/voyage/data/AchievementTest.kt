package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The achievement catalog, against the same shared data the app reads — a port
 * of the iOS `AchievementCompletionTests`.
 *
 * The cases that matter are the ones where the two platforms could quietly
 * disagree about what counts: which countries are in the running, whether a
 * continent counts once or once per country, and which attractions are wonders.
 */
class AchievementTest {

    private val cache = SharedFiles.countryDataCache()

    private fun catalog(
        visited: Set<String> = emptySet(),
        checkedCities: Map<String, Set<String>> = emptyMap(),
        checkedAttractions: Map<String, Set<String>> = emptyMap(),
    ) = AchievementCatalog.of(cache, visited, checkedCities, checkedAttractions)

    private fun achievement(kind: AchievementKind, visited: Set<String> = emptySet()) =
        catalog(visited = visited).first { it.kind == kind }

    private fun countriesOf(continent: Continent) = cache.continents.countries(of = continent)

    // ---- The catalog ----

    @Test
    fun `the catalog is iOS's list, in iOS's order`() {
        assertEquals(
            listOf(
                AchievementKind.Globetrotter,
                AchievementKind.CapitalCollector,
                AchievementKind.Wonders,
                AchievementKind.ContinentalDrifter,
                AchievementKind.Explorer(Continent.AFRICA),
                AchievementKind.Explorer(Continent.ASIA),
                AchievementKind.Explorer(Continent.EUROPE),
                AchievementKind.Explorer(Continent.NORTH_AMERICA),
                AchievementKind.Explorer(Continent.SOUTH_AMERICA),
                AchievementKind.Explorer(Continent.OCEANIA),
            ),
            catalog().map { it.kind },
        )
    }

    @Test
    fun `Antarctica has no explorer medal of its own`() {
        assertTrue(
            catalog().none { it.kind == AchievementKind.Explorer(Continent.ANTARCTICA) },
        )
    }

    @Test
    fun `identities are unique and stable`() {
        val ids = catalog().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("explorer-SOUTH_AMERICA", AchievementKind.Explorer(Continent.SOUTH_AMERICA).id)
    }

    // ---- Progress arithmetic ----

    @Test
    fun `progress is the earned share, truncated to a percentage`() {
        val third = Achievement(AchievementKind.Globetrotter, listOf("a"), listOf("b", "c"))
        assertEquals(1, third.current)
        assertEquals(3, third.total)
        assertEquals(1f / 3f, third.progress, 1e-6f)
        assertEquals(33, third.percentage)
        assertFalse(third.isCompleted)

        val done = Achievement(AchievementKind.Globetrotter, listOf("a", "b"), emptyList())
        assertTrue(done.isCompleted)
        assertEquals(100, done.percentage)

        val empty = Achievement(AchievementKind.Globetrotter, emptyList(), emptyList())
        assertEquals(0f, empty.progress, 0f)
        assertEquals(0, empty.percentage)
        assertTrue("nothing to do is done", empty.isCompleted)
    }

    // ---- Globetrotter ----

    @Test
    fun `Globetrotter counts the 195 UN states, not all 206 features`() {
        val globetrotter = achievement(AchievementKind.Globetrotter)

        assertEquals(195, globetrotter.total)
        assertEquals(0, globetrotter.current)
        assertEquals(206, cache.countries.size)
        assertTrue("Greenland is not a UN state", "Greenland" !in globetrotter.remaining)
        assertTrue("France is", "France" in globetrotter.remaining)
    }

    @Test
    fun `visiting a territory does not move Globetrotter`() {
        val globetrotter = achievement(AchievementKind.Globetrotter, visited = setOf("Greenland"))

        assertEquals(0, globetrotter.current)
        assertEquals(195, globetrotter.total)
    }

    @Test
    fun `Globetrotter completes only when every UN state is visited`() {
        val unCountries = UnMembership.membersOf(cache.countryNames)

        val partial = achievement(AchievementKind.Globetrotter, visited = unCountries.take(150).toSet())
        assertFalse(partial.isCompleted)
        assertEquals(150, partial.current)

        val all = achievement(AchievementKind.Globetrotter, visited = unCountries)
        assertTrue(all.isCompleted)
        assertEquals(100, all.percentage)
        assertTrue(all.remaining.isEmpty())
    }

    @Test
    fun `a country saved under a name the dataset no longer uses cannot inflate the total`() {
        // iOS subtracts its territory list from the visited set and counts what
        // is left, so a stale name would be counted as visited *and* leave its
        // current name outstanding. Intersecting with the dataset cannot.
        val globetrotter = achievement(AchievementKind.Globetrotter, visited = setOf("Turkey"))

        assertEquals(0, globetrotter.current)
        assertEquals(195, globetrotter.total)
    }

    // ---- Capital Collector ----

    @Test
    fun `Capital Collector counts the capitals of UN states`() {
        val capitals = achievement(AchievementKind.CapitalCollector)

        val expected = cache.countries.count {
            it.capital != null && UnMembership.isMember(it.name)
        }
        assertEquals(expected, capitals.total)
        assertTrue("the dataset should have most of them", capitals.total > 180)
        assertTrue("Paris" in capitals.remaining)
    }

    @Test
    fun `ticking a capital in its country's city list earns it`() {
        val earned = catalog(checkedCities = mapOf("France" to setOf("Paris")))
            .first { it.kind == AchievementKind.CapitalCollector }

        assertEquals(1, earned.current)
        assertEquals(listOf("Paris"), earned.earned)
    }

    @Test
    fun `ticking a city that is not the capital earns nothing`() {
        val earned = catalog(checkedCities = mapOf("France" to setOf("Nice", "Lyon")))
            .first { it.kind == AchievementKind.CapitalCollector }

        assertEquals(0, earned.current)
    }

    // ---- Wonders of the World ----

    private fun wonders(checked: Map<String, Set<String>>) =
        catalog(checkedAttractions = checked).first { it.kind == AchievementKind.Wonders }

    @Test
    fun `all eight wonders complete the achievement`() {
        val checked = WondersOfTheWorld.wonders
            .groupBy({ it.country }, { it.attraction })
            .mapValues { (_, attractions) -> attractions.toSet() }

        val achievement = wonders(checked)
        assertEquals(8, achievement.total)
        assertTrue(achievement.isCompleted)
        assertEquals(100, achievement.percentage)
    }

    @Test
    fun `wonders track partial progress`() {
        val achievement = wonders(
            mapOf("Peru" to setOf("Machu Picchu"), "Italy" to setOf("Colosseum")),
        )

        assertEquals(2, achievement.current)
        assertEquals(8, achievement.total)
        assertFalse(achievement.isCompleted)
    }

    @Test
    fun `the Pyramids of Giza are the honorary eighth`() {
        val achievement = wonders(mapOf("Egypt" to setOf("Pyramids of Giza")))

        assertEquals(listOf("Pyramids of Giza"), achievement.earned)
    }

    @Test
    fun `other attractions in a wonder's country do not count`() {
        val achievement = wonders(
            mapOf("Italy" to setOf("Pompeii"), "China" to setOf("Great Wall of China")),
        )

        assertEquals(1, achievement.current)
        assertEquals(listOf("Great Wall of China"), achievement.earned)
    }

    @Test
    fun `every wonder exists in the shared highlights data`() {
        // A wonder naming an attraction no country lists could never be ticked
        // off, which would make the achievement unwinnable on both platforms.
        WondersOfTheWorld.wonders.forEach { wonder ->
            val country = cache.countryNamed(wonder.country)
            requireNotNull(country) { "${wonder.country} is not in world.geojson" }
            val code = requireNotNull(country.isoCode) { "${wonder.country} has no ISO code" }
            val attractions = cache.highlights(code)?.attractions.orEmpty()
            assertTrue(
                "${wonder.attraction} is missing from ${wonder.country}'s attractions",
                wonder.attraction in attractions,
            )
        }
    }

    // ---- Continental Drifter ----

    @Test
    fun `Continental Drifter tracks all seven continents, Antarctica included`() {
        val drifter = achievement(AchievementKind.ContinentalDrifter)

        assertEquals(7, drifter.total)
        assertEquals(0, drifter.current)
        assertTrue("Antarctica" in drifter.remaining)
    }

    @Test
    fun `one country counts its whole continent, and only once`() {
        val europe = countriesOf(Continent.EUROPE).take(4).toSet()

        val one = achievement(AchievementKind.ContinentalDrifter, visited = setOf(europe.first()))
        assertEquals(1, one.current)
        assertEquals(listOf("Europe"), one.earned)

        val four = achievement(AchievementKind.ContinentalDrifter, visited = europe)
        assertEquals("four countries on one continent is still one continent", 1, four.current)
    }

    @Test
    fun `one country per continent completes it, and six leaves Antarctica`() {
        val everywhere = Continent.entries.map { countriesOf(it).first() }.toSet()

        val complete = achievement(AchievementKind.ContinentalDrifter, visited = everywhere)
        assertTrue(complete.isCompleted)
        assertTrue(complete.remaining.isEmpty())

        val inhabited = Continent.entries.filterNot { it == Continent.ANTARCTICA }
            .map { countriesOf(it).first() }.toSet()
        val short = achievement(AchievementKind.ContinentalDrifter, visited = inhabited)
        assertFalse(short.isCompleted)
        assertEquals(6, short.current)
        assertEquals(listOf("Antarctica"), short.remaining)
    }

    // ---- Explorer of … ----

    @Test
    fun `an explorer medal is earned by visiting its whole continent`() {
        Continent.entries.filterNot { it == Continent.ANTARCTICA }.forEach { continent ->
            val countries = countriesOf(continent)
            val explorer = achievement(AchievementKind.Explorer(continent), visited = countries)

            assertTrue("${continent.displayName} should be complete", explorer.isCompleted)
            assertEquals(100, explorer.percentage)
            assertEquals(countries.size, explorer.total)
        }
    }

    @Test
    fun `an explorer medal counts only its own continent's countries`() {
        val europe = countriesOf(Continent.EUROPE).take(2)
        val asia = countriesOf(Continent.ASIA).take(3)
        val visited = (europe + asia).toSet()

        assertEquals(2, achievement(AchievementKind.Explorer(Continent.EUROPE), visited).current)
        assertEquals(3, achievement(AchievementKind.Explorer(Continent.ASIA), visited).current)
        assertEquals(
            0,
            achievement(AchievementKind.Explorer(Continent.OCEANIA), visited).current,
        )
    }

    @Test
    fun `explorer medals count territories, unlike Globetrotter`() {
        // Greenland is not a UN state but it is very much in North America —
        // iOS's continent achievements make the same distinction.
        val explorer = achievement(
            AchievementKind.Explorer(Continent.NORTH_AMERICA),
            visited = setOf("Greenland"),
        )

        assertEquals(1, explorer.current)
        assertEquals(listOf("Greenland"), explorer.earned)
    }

    @Test
    fun `earned and remaining items are sorted`() {
        val visited = countriesOf(Continent.EUROPE).take(5).toSet()
        val explorer = achievement(AchievementKind.Explorer(Continent.EUROPE), visited)

        assertEquals(explorer.earned.sorted(), explorer.earned)
        assertEquals(explorer.remaining.sorted(), explorer.remaining)
    }
}
