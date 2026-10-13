package com.anmol.voyage.ui.achievements

import com.anmol.voyage.data.MedalTier
import com.anmol.voyage.data.MedalUnlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which medals a change to what is marked celebrates — the same cases as the
 * iOS `AchievementCompletionTests` pin for `newlyUnlocked`.
 */
class UnlockCelebrationTest {

    private fun unlock(id: String, tier: MedalTier) = MedalUnlock(id, tier)

    private fun upTo(id: String, tier: MedalTier) =
        MedalTier.entries.filter { it <= tier }.map { unlock(id, it) }

    @Test
    fun theFirstReadingIsABaseline() {
        assertEquals(
            emptyList<MedalUnlock>(),
            newlyUnlocked(before = null, after = listOf(unlock("globetrotter", MedalTier.Bronze))),
        )
    }

    @Test
    fun aNewlyEarnedMedalIsCelebrated() {
        assertEquals(
            listOf(unlock("explorer-EUROPE", MedalTier.Bronze)),
            newlyUnlocked(
                before = upTo("wonders", MedalTier.Bronze),
                after = upTo("wonders", MedalTier.Bronze) + upTo("explorer-EUROPE", MedalTier.Bronze),
            ),
        )
    }

    @Test
    fun eachTierIsCelebratedInTurn() {
        val bronze = upTo("globetrotter", MedalTier.Bronze)
        val silver = upTo("globetrotter", MedalTier.Silver)
        val gold = upTo("globetrotter", MedalTier.Gold)
        assertEquals(listOf(unlock("globetrotter", MedalTier.Bronze)), newlyUnlocked(emptyList(), bronze))
        assertEquals(listOf(unlock("globetrotter", MedalTier.Silver)), newlyUnlocked(bronze, silver))
        assertEquals(listOf(unlock("globetrotter", MedalTier.Gold)), newlyUnlocked(silver, gold))
    }

    @Test
    fun jumpingSeveralTiersCelebratesOnlyTheHighest() {
        assertEquals(
            listOf(unlock("explorer-OCEANIA", MedalTier.Gold)),
            newlyUnlocked(before = emptyList(), after = upTo("explorer-OCEANIA", MedalTier.Gold)),
        )
    }

    @Test
    fun nothingNewCelebratesNothing() {
        val held = upTo("wonders", MedalTier.Silver)
        assertEquals(emptyList<MedalUnlock>(), newlyUnlocked(before = held, after = held))
    }

    @Test
    fun losingMedalsCelebratesNothing() {
        val silver = upTo("globetrotter", MedalTier.Silver)
        assertEquals(emptyList<MedalUnlock>(), newlyUnlocked(before = silver, after = emptyList()))
        assertEquals(
            "dropping back to bronze must not celebrate bronze again",
            emptyList<MedalUnlock>(),
            newlyUnlocked(before = silver, after = upTo("globetrotter", MedalTier.Bronze)),
        )
    }

    @Test
    fun severalAtOnceAreCelebratedInListOrder() {
        assertEquals(
            listOf(unlock("continental-drifter", MedalTier.Bronze), unlock("explorer-OCEANIA", MedalTier.Bronze)),
            newlyUnlocked(
                before = emptyList(),
                after = upTo("continental-drifter", MedalTier.Bronze) + upTo("explorer-OCEANIA", MedalTier.Bronze),
            ),
        )
    }
}
