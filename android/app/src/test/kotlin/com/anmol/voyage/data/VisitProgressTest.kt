package com.anmol.voyage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's dock: the count, the percent, and when the card previews a visit. */
class VisitProgressTest {

    @Test
    fun `the total is every UN state in the dataset`() {
        val names = SharedFiles.countryDataCache().countryNames
        assertEquals(UnMembership.MEMBER_COUNT, UnMembership.membersOf(names).size)
    }

    @Test
    fun `territories do not count toward the total`() {
        val progress = VisitProgress.of(setOf("France", "Greenland", "Taiwan"))
        assertEquals(1, progress.visited)
        assertEquals(195, progress.total)
    }

    @Test
    fun `the percent rounds to the nearest whole number`() {
        assertEquals(0, VisitProgress(0).percent)
        // 1/195 is 0.51%: rounds up, so a first visit already moves the label.
        assertEquals(1, VisitProgress(1).percent)
        // 12/195 is 6.15%, the design's example.
        assertEquals(6, VisitProgress(12).percent)
        assertEquals(100, VisitProgress(195).percent)
    }

    @Test
    fun `an unvisited UN state is previewed`() {
        assertTrue(VisitProgress.previewsVisit("Chad", isVisited = false, justUnvisited = null))
    }

    @Test
    fun `nothing selected, a visited country or a territory is not previewed`() {
        assertFalse(VisitProgress.previewsVisit(null, isVisited = false, justUnvisited = null))
        assertFalse(VisitProgress.previewsVisit("Chad", isVisited = true, justUnvisited = null))
        assertFalse(VisitProgress.previewsVisit("Greenland", isVisited = false, justUnvisited = null))
    }

    @Test
    fun `a country just un-visited from the card is not previewed until another is picked`() {
        assertFalse(VisitProgress.previewsVisit("Chad", isVisited = false, justUnvisited = "Chad"))
        assertTrue(VisitProgress.previewsVisit("Niger", isVisited = false, justUnvisited = "Chad"))
    }
}
