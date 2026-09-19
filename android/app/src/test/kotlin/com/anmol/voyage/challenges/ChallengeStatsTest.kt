package com.anmol.voyage.challenges

import com.anmol.voyage.data.SharedFiles
import com.anmol.voyage.data.UnMembership
import com.anmol.voyage.state.InMemoryStateStore
import com.anmol.voyage.state.PersistedState
import com.anmol.voyage.state.PersistedStateCodec
import com.anmol.voyage.state.VoyageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Challenge statistics, trophies and region pools — the rest of iOS's
 * `ClickCountryGameTests` and `NameCapitalGameTests`, which pin the rules every
 * mode shares.
 */
class ChallengeStatsTest {

    private fun state(store: InMemoryStateStore = InMemoryStateStore()) =
        VoyageState(store, CoroutineScope(Dispatchers.Unconfined))

    private val cache by lazy { SharedFiles.countryDataCache() }

    @Test
    fun `a best result compares the share correct, then the time`() {
        val state = state()
        fun record(correct: Int, time: Double) =
            state.recordChallengeGame(ChallengeGameMode.ClickCountry, ChallengeRegion.World, correct, 9, time)

        assertTrue("the first completed game is always a best", record(correct = 5, time = 100.0))
        assertFalse("fewer correct is no best, however fast", record(correct = 4, time = 50.0))
        assertTrue("as many correct, faster, is a best", record(correct = 5, time = 80.0))
        assertTrue("more correct beats a faster time", record(correct = 6, time = 200.0))

        val stats = state.challengeStats.stats(ChallengeGameMode.ClickCountry, ChallengeRegion.World)
        assertEquals(4, stats.gamesPlayed)
        assertEquals(6, stats.bestCorrect)
        assertEquals(200.0, stats.bestTimeSeconds!!, 0.0)
    }

    @Test
    fun `stats are kept per mode and region`() {
        val state = state()
        state.recordChallengeGame(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe, 5, 9, 100.0)

        val stats = state.challengeStats
        assertEquals(1, stats.stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe).gamesPlayed)
        assertEquals(0, stats.stats(ChallengeGameMode.ClickCountry, ChallengeRegion.Africa).gamesPlayed)
        assertEquals(0, stats.stats(ChallengeGameMode.NameFlag, ChallengeRegion.Europe).gamesPlayed)
        assertEquals(1, stats.totalGamesPlayed(ChallengeGameMode.ClickCountry))
    }

    @Test
    fun `trophy tiers match iOS`() {
        assertEquals(ChallengeTrophy.Gold, ChallengeRegion.World.trophy)
        assertEquals(ChallengeTrophy.Silver, ChallengeRegion.Europe.trophy)
        assertEquals(ChallengeTrophy.Silver, ChallengeRegion.Asia.trophy)
        assertEquals(ChallengeTrophy.Silver, ChallengeRegion.Africa.trophy)
        assertEquals(ChallengeTrophy.Bronze, ChallengeRegion.SouthAmerica.trophy)
        assertEquals(ChallengeTrophy.Bronze, ChallengeRegion.NorthAmerica.trophy)
        assertEquals(ChallengeTrophy.Bronze, ChallengeRegion.Oceania.trophy)
    }

    @Test
    fun `only flawless sweeps count toward trophies`() {
        val state = state()
        val mode = ChallengeGameMode.ClickCountry
        state.recordChallengeGame(mode, ChallengeRegion.SouthAmerica, 12, 12, 120.0)
        state.recordChallengeGame(mode, ChallengeRegion.Oceania, 14, 14, 200.0)
        state.recordChallengeGame(mode, ChallengeRegion.Europe, 44, 44, 400.0)
        state.recordChallengeGame(mode, ChallengeRegion.Asia, 33, 49, 300.0)
        state.recordChallengeGame(mode, ChallengeRegion.World, 166, 195, 900.0)

        val stats = state.challengeStats
        assertTrue(stats.stats(mode, ChallengeRegion.SouthAmerica).isPerfect)
        assertFalse(stats.stats(mode, ChallengeRegion.Asia).isPerfect)
        assertEquals(2, stats.trophyCount(ChallengeTrophy.Bronze))
        assertEquals(1, stats.trophyCount(ChallengeTrophy.Silver))
        assertEquals(0, stats.trophyCount(ChallengeTrophy.Gold))
    }

    @Test
    fun `stats are saved and read back under iOS's keys`() {
        val store = InMemoryStateStore()
        state(store).recordChallengeGame(ChallengeGameMode.NameCapital, ChallengeRegion.Asia, 10, 30, 60.0)

        val saved = store.state
        assertEquals(setOf("nameCapital|asia"), saved.challengeStats.keys)
        val reloaded = state(InMemoryStateStore(PersistedStateCodec.decode(PersistedStateCodec.encode(saved))))
        val stats = reloaded.challengeStats.stats(ChallengeGameMode.NameCapital, ChallengeRegion.Asia)
        assertEquals(1, stats.gamesPlayed)
        assertEquals(10, stats.bestCorrect)
    }

    @Test
    fun `a document from before challenges reads with none played`() {
        val old = PersistedStateCodec.decode("""{"version":1,"visitedCountries":["Norway"]}""")
        assertEquals(emptyMap<String, ChallengeGameStats>(), old.challengeStats)
    }

    @Test
    fun `resetting all data clears challenge results too`() {
        val state = state(InMemoryStateStore(PersistedState(visitedCountries = setOf("Norway"))))
        state.recordChallengeGame(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe, 5, 9, 100.0)

        state.resetAllData()

        assertEquals(0, state.challengeStats.totalGamesPlayed(ChallengeGameMode.ClickCountry))
    }

    @Test
    fun `region pools count countries the way progress does`() {
        val world = ChallengeRegion.World.countries(cache.continents).toSet()

        assertEquals("the world sweep is the 195 UN states", 195, world.size)
        UnMembership.nonMemberTerritories.forEach { territory ->
            assertFalse("$territory should not be a target", territory in world)
        }
        ChallengeRegion.entries.filter { it != ChallengeRegion.World }.forEach { region ->
            val pool = region.countries(cache.continents).toSet()
            assertTrue("${region.name} should not be empty", pool.isNotEmpty())
            assertTrue("${region.name} should be part of the world", world.containsAll(pool))
        }
    }

    @Test
    fun `every country in play has a capital`() {
        // Otherwise Name the Capital would drop it without a word.
        ChallengeRegion.World.countries(cache.continents).forEach { country ->
            assertNotNull("$country has no capital in world.geojson", cache.countryNamed(country)?.capital)
        }
    }

    @Test
    fun `game time reads as m ss, and h mm ss past an hour`() {
        assertEquals("0:00", formatGameTime(0.0))
        assertEquals("1:05", formatGameTime(65.9))
        assertEquals("59:59", formatGameTime(3_599.0))
        assertEquals("1:00:07", formatGameTime(3_607.0))
    }
}
