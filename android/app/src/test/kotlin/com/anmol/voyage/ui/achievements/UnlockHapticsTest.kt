package com.anmol.voyage.ui.achievements

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a change to what is marked earns the unlock buzz. */
class UnlockHapticsTest {

    @Test
    fun theFirstReadingIsABaseline() {
        assertFalse(unlockedAny(before = null, after = listOf("globetrotter")))
    }

    @Test
    fun aNewlyCompletedMedalIsAnUnlock() {
        assertTrue(unlockedAny(before = listOf("wonders"), after = listOf("wonders", "explorer-EUROPE")))
    }

    @Test
    fun nothingNewIsNotAnUnlock() {
        assertFalse(unlockedAny(before = listOf("wonders"), after = listOf("wonders")))
    }

    @Test
    fun losingMedalsIsNotAnUnlock() {
        assertFalse(unlockedAny(before = listOf("wonders", "globetrotter"), after = emptyList()))
    }

    @Test
    fun swappingOneMedalForAnotherIsAnUnlock() {
        assertTrue(unlockedAny(before = listOf("wonders"), after = listOf("continental-drifter")))
    }
}
