package com.anmol.voyage.ui.achievements

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which medals a change to what is marked celebrates. */
class UnlockCelebrationTest {

    @Test
    fun theFirstReadingIsABaseline() {
        assertEquals(emptyList<String>(), newlyCompleted(before = null, after = listOf("globetrotter")))
    }

    @Test
    fun aNewlyCompletedMedalIsCelebrated() {
        assertEquals(
            listOf("explorer-EUROPE"),
            newlyCompleted(before = listOf("wonders"), after = listOf("wonders", "explorer-EUROPE")),
        )
    }

    @Test
    fun nothingNewCelebratesNothing() {
        assertEquals(emptyList<String>(), newlyCompleted(before = listOf("wonders"), after = listOf("wonders")))
    }

    @Test
    fun losingMedalsCelebratesNothing() {
        assertEquals(emptyList<String>(), newlyCompleted(before = listOf("wonders", "globetrotter"), after = emptyList()))
    }

    @Test
    fun severalAtOnceAreCelebratedInListOrder() {
        assertEquals(
            listOf("continental-drifter", "explorer-OCEANIA"),
            newlyCompleted(before = listOf("wonders"), after = listOf("wonders", "continental-drifter", "explorer-OCEANIA")),
        )
    }
}
