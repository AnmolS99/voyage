package com.anmol.voyage.ui.country

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Journey header's title rule, with the same cases as iOS
 * `testJourneyTitleFitsLongNamesOnTwoLines`: every letter 20 wide at full size.
 */
class JourneyTitleTest {

    private fun fit(name: String) = JourneyTitle.fit(name, 222f) { it.length * 20f }

    @Test
    fun shortNamesKeepTheFullSize() {
        assertEquals(JourneyTitle.Fit(40f, listOf("Chad")), fit("Chad"))
    }

    @Test
    fun longerNamesShrinkOnOneLineDownToThirty() {
        assertEquals(JourneyTitle.Fit(31.5f, listOf("United Kingdom")), fit("United Kingdom"))
    }

    @Test
    fun namesTooLongForOneLineBreakWhereTheLinesAreMostEven() {
        assertEquals(
            JourneyTitle.Fit(30f, listOf("United States", "of America")),
            fit("United States of America"),
        )
        assertEquals(
            JourneyTitle.Fit(23f, listOf("Democratic Republic", "of the Congo")),
            fit("Democratic Republic of the Congo"),
        )
    }

    @Test
    fun aSingleLongWordShrinksOnOneLine() {
        assertEquals(JourneyTitle.Fit(22f, listOf("Kyrgyzstanabcdefghij")), fit("Kyrgyzstanabcdefghij"))
    }
}
