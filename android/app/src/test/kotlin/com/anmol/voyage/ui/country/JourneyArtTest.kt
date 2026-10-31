package com.anmol.voyage.ui.country

import com.anmol.voyage.data.SharedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds `shared/data/journey/` together: every country with art has both a day
 * and a night painting and names the place they show, and every named place has
 * art. iOS `testJourneyArtNamesItsPlace` checks the same folder.
 */
class JourneyArtTest {

    private val folder = SharedFiles.file("shared/data/journey")

    private val places = SharedFiles.open("shared/data/${JourneyArt.PLACES}").use(JourneyArt::parsePlaces)

    private fun countriesWith(suffix: String): Set<String> =
        folder.listFiles().orEmpty().map { it.name }
            .filter { it.endsWith("_$suffix.webp") }
            .mapTo(sortedSetOf()) { it.removeSuffix("_$suffix.webp") }

    @Test
    fun everyPaintingHasADayAndANight() {
        assertEquals(countriesWith("day"), countriesWith("night"))
    }

    @Test
    fun everyPaintingNamesItsPlace() {
        assertEquals(countriesWith("day"), places.keys.toSortedSet())
        assertTrue(places.values.all { it.isNotBlank() })
    }
}
