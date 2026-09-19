package com.anmol.voyage.challenges

import com.anmol.voyage.state.InMemoryStateStore
import com.anmol.voyage.state.VoyageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three game modes, ported from iOS `ClickCountryGameTests`,
 * `NameCapitalGameTests` and `NameFlagGameTests` — same pools, same guesses,
 * same expected scores — so a sweep plays by the same rules on both platforms.
 *
 * Results are recorded into a real `VoyageState` over an in-memory store, which
 * also checks that a finished sweep is what reaches the saved document.
 */
class RegionSweepGameTest {

    private val recorder = VoyageState(InMemoryStateStore(), CoroutineScope(Dispatchers.Unconfined))

    private val capitals = mapOf("France" to "Paris", "Germany" to "Berlin", "Spain" to "Madrid")

    private fun clickGame(region: ChallengeRegion, vararg countries: String) =
        ClickCountryGame(region, recorder, pool = { countries.toList() })

    private fun capitalGame(vararg countries: String) =
        NameCapitalGame(ChallengeRegion.Europe, recorder, pool = { countries.toList() }, capitalOf = capitals::get)

    private fun flagGame(vararg countries: String) =
        RegionSweepGame(ChallengeGameMode.NameFlag, ChallengeRegion.Europe, recorder, pool = { countries.toList() })

    /** A full Click the Country guess: a tap that marks, then one that confirms. */
    private fun ClickCountryGame.guess(country: String): SweepGuessOutcome {
        val first = handleTap(country)
        return if (first == SweepGuessOutcome.Marked) handleTap(country) else first
    }

    private fun stats(mode: ChallengeGameMode, region: ChallengeRegion) = recorder.challengeStats.stats(mode, region)

    // ---- Click the Country ----

    @Test
    fun `a first tap marks and only a second on the same country confirms`() {
        val game = clickGame(ChallengeRegion.Europe, "France")

        assertEquals(SweepGuessOutcome.Marked, game.handleTap("Germany"))
        assertEquals("Germany", game.pendingGuess)
        assertEquals(0, game.correctCount)

        // A tap elsewhere moves the mark rather than guessing.
        assertEquals(SweepGuessOutcome.Marked, game.handleTap("France"))
        assertEquals("France", game.pendingGuess)
        assertEquals(0, game.correctCount)

        assertEquals(SweepGuessOutcome.Correct, game.handleTap("France"))
        assertNull(game.pendingGuess)
    }

    @Test
    fun `click the country scores a full sweep`() {
        val game = clickGame(ChallengeRegion.Europe, "France", "Germany", "Spain")

        assertEquals("France", game.currentTarget)
        assertEquals(SweepGuessOutcome.Correct, game.guess("France"))

        // One miss is all it takes: the answer is revealed.
        assertEquals("Germany", game.currentTarget)
        assertEquals(SweepGuessOutcome.Reveal, game.guess("Norway"))
        assertEquals(RegionSweepGame.Phase.Revealing, game.phase)
        assertEquals(SweepGuessOutcome.Ignored, game.handleTap("Germany"))
        game.finishReveal()

        assertEquals("Spain", game.currentTarget)
        assertEquals(SweepGuessOutcome.Correct, game.guess("Spain"))

        assertEquals(RegionSweepGame.Phase.Finished, game.phase)
        assertEquals(2, game.correctCount)
        assertEquals(3, game.totalCountries)
        assertEquals(3, game.answeredCount)
        assertEquals(listOf("Germany"), game.missedCountries)
        assertTrue(game.isNewBest)

        val recorded = stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe)
        assertEquals(1, recorded.gamesPlayed)
        assertEquals(2, recorded.bestCorrect)
        assertEquals(3, recorded.bestTotal)
    }

    @Test
    fun `tapping a country already found is ignored`() {
        val game = clickGame(ChallengeRegion.Europe, "France", "Germany")
        game.guess("France")

        assertEquals(SweepGuessOutcome.Ignored, game.handleTap("France"))
        assertEquals("Germany", game.currentTarget)
        assertEquals(1, game.correctCount)
    }

    @Test
    fun `unfinished sweeps are not recorded`() {
        val game = clickGame(ChallengeRegion.Europe, "France", "Germany")
        game.guess("France")
        game.restart()
        assertEquals(0, stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe).gamesPlayed)

        // Abandoned after a guess: dropped, never finished.
        clickGame(ChallengeRegion.Europe, "France", "Germany").guess("France")
        assertEquals(0, stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe).gamesPlayed)
    }

    @Test
    fun `restarting starts the sweep over`() {
        val game = clickGame(ChallengeRegion.Europe, "France", "Germany")
        game.guess("France")
        game.handleTap("Spain")

        game.restart()

        assertEquals("France", game.currentTarget)
        assertEquals(0, game.correctCount)
        assertEquals(0, game.answeredCount)
        assertNull(game.pendingGuess)
        assertEquals(RegionSweepGame.Phase.Playing, game.phase)
    }

    @Test
    fun `a trophy is earned only by the first flawless sweep`() {
        val first = clickGame(ChallengeRegion.Oceania, "Fiji")
        first.guess("Fiji")
        assertEquals(RegionSweepGame.Phase.Finished, first.phase)
        assertTrue(first.didEarnTrophy)

        val second = clickGame(ChallengeRegion.Oceania, "Fiji")
        second.guess("Fiji")
        assertFalse(second.didEarnTrophy)

        assertEquals(1, recorder.challengeStats.trophyCount(ChallengeTrophy.Bronze))
    }

    @Test
    fun `an imperfect sweep earns no trophy`() {
        val game = clickGame(ChallengeRegion.Oceania, "Fiji")
        game.guess("Samoa")
        game.finishReveal()

        assertEquals(RegionSweepGame.Phase.Finished, game.phase)
        assertFalse(game.didEarnTrophy)
        assertEquals(0, recorder.challengeStats.trophyCount(ChallengeTrophy.Bronze))
    }

    // ---- Name the Capital ----

    @Test
    fun `name the capital scores a full sweep`() {
        val game = capitalGame("France", "Germany", "Spain")

        assertEquals("France", game.currentTarget)
        assertEquals("Paris", game.currentAnswer)
        assertEquals(SweepGuessOutcome.Correct, game.submitGuess("Paris"))

        assertEquals("Germany", game.currentTarget)
        assertEquals(SweepGuessOutcome.Reveal, game.submitGuess("Munich"))
        assertEquals(RegionSweepGame.Phase.Revealing, game.phase)
        assertEquals(SweepGuessOutcome.Ignored, game.submitGuess("Berlin"))
        game.finishReveal()

        assertEquals("Spain", game.currentTarget)
        assertEquals(SweepGuessOutcome.Correct, game.submitGuess("Madrid"))

        assertEquals(RegionSweepGame.Phase.Finished, game.phase)
        assertEquals(2, game.correctCount)
        assertEquals(3, game.totalCountries)
        assertEquals(3, game.answeredCount)
        assertEquals(listOf("Germany"), game.missedCountries)
        assertTrue(game.isNewBest)

        assertEquals(1, stats(ChallengeGameMode.NameCapital, ChallengeRegion.Europe).gamesPlayed)
        // Recorded under its own mode, not another's.
        assertEquals(0, stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe).gamesPlayed)
    }

    @Test
    fun `a typed capital ignores case and surrounding whitespace`() {
        assertEquals(SweepGuessOutcome.Correct, capitalGame("France").submitGuess("  pArIs "))
    }

    @Test
    fun `an empty guess is ignored`() {
        val game = capitalGame("France")
        assertEquals(SweepGuessOutcome.Ignored, game.submitGuess("   "))
        assertEquals("France", game.currentTarget)
        assertEquals(0, game.answeredCount)
    }

    @Test
    fun `countries without capital data are left out`() {
        val game = capitalGame("France", "Atlantis")
        assertEquals(1, game.totalCountries)
        assertEquals("France", game.currentTarget)
    }

    @Test
    fun `a flawless capital sweep earns the region's trophy once`() {
        capitalGame("France").submitGuess("Paris")
        val second = capitalGame("France")
        second.submitGuess("Paris")

        assertFalse(second.didEarnTrophy)
        assertEquals(1, recorder.challengeStats.trophyCount(ChallengeTrophy.Silver))
    }

    // ---- Name the Flag ----

    @Test
    fun `name the flag scores a full sweep`() {
        val game = flagGame("France", "Germany", "Spain")

        assertEquals("France", game.currentAnswer)
        assertEquals(SweepGuessOutcome.Correct, game.submitGuess("France"))
        assertEquals(SweepGuessOutcome.Reveal, game.submitGuess("Austria"))
        game.finishReveal()
        assertEquals(SweepGuessOutcome.Correct, game.submitGuess("Spain"))

        assertEquals(RegionSweepGame.Phase.Finished, game.phase)
        assertEquals(2, game.correctCount)
        assertEquals(listOf("Germany"), game.missedCountries)
        assertEquals(1, stats(ChallengeGameMode.NameFlag, ChallengeRegion.Europe).gamesPlayed)
        assertEquals(0, stats(ChallengeGameMode.NameCapital, ChallengeRegion.Europe).gamesPlayed)
    }

    @Test
    fun `a typed country name ignores case and surrounding whitespace`() {
        assertEquals(SweepGuessOutcome.Correct, flagGame("France").submitGuess(" fRaNcE  "))
    }

    // ---- The clock ----

    @Test
    fun `the clock stops while paused and freezes when the sweep ends`() {
        var now = 0L
        val game = ClickCountryGame(ChallengeRegion.Oceania, recorder, pool = { listOf("Fiji") }, nanoTime = { now })

        now = seconds(10)
        assertEquals(10.0, game.elapsedSeconds(), 1e-9)

        game.pause()
        now = seconds(100)
        assertEquals(10.0, game.elapsedSeconds(), 1e-9)

        game.resume()
        now = seconds(105)
        game.guess("Fiji")
        now = seconds(500)
        assertEquals(15.0, game.elapsedSeconds(), 1e-9)
        assertEquals(15.0, stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Oceania).bestTimeSeconds!!, 1e-9)
    }

    private fun seconds(value: Long) = value * 1_000_000_000L
}
