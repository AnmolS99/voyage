package com.anmol.voyage.navigation

import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ChallengeRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bottom bar must mirror the iOS `TabView` in ContentView.swift: same
 * destinations, same order, Home first.
 */
class VoyageDestinationTest {

    @Test
    fun `destinations mirror the iOS tab order`() {
        assertEquals(
            listOf("home", "daily", "challenges", "achievements", "settings"),
            VoyageDestination.entries.map { it.route },
        )
    }

    @Test
    fun `routes are unique`() {
        val routes = VoyageDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `home is the start destination`() {
        assertEquals(VoyageDestination.Home, VoyageDestination.start)
    }

    @Test
    fun `a challenge's screens belong to the Challenges tab`() {
        val routes = listOf(
            ChallengeRoutes.REGION_SELECT,
            ChallengeRoutes.PLAY,
            ChallengeRoutes.regionSelect(ChallengeGameMode.NameFlag),
            ChallengeRoutes.play(ChallengeGameMode.ClickCountry, ChallengeRegion.Europe),
        )
        routes.forEach { assertEquals(it, VoyageDestination.Challenges, VoyageDestination.owning(it)) }
        assertEquals(VoyageDestination.Settings, VoyageDestination.owning("settings"))
        assertNull(VoyageDestination.owning("challengesque"))
    }
}
