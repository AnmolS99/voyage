package com.anmol.voyage.navigation

import androidx.navigation.NavBackStackEntry
import com.anmol.voyage.challenges.ChallengeGameMode
import com.anmol.voyage.challenges.ChallengeRegion

/**
 * The routes under the Challenges tab: a mode's region picker, and a game being
 * played — iOS's `NavigationLink` to `RegionSelectView` and its
 * `fullScreenCover`. Both sit under [VoyageDestination.Challenges]'s route, which
 * is how the bottom bar knows which tab they belong to.
 */
object ChallengeRoutes {

    private const val MODE = "mode"
    private const val REGION = "region"

    val REGION_SELECT = "${VoyageDestination.Challenges.route}/{$MODE}"
    val PLAY = "${VoyageDestination.Challenges.route}/{$MODE}/{$REGION}/play"

    fun regionSelect(mode: ChallengeGameMode) = "${VoyageDestination.Challenges.route}/${mode.rawValue}"

    fun play(mode: ChallengeGameMode, region: ChallengeRegion) =
        "${VoyageDestination.Challenges.route}/${mode.rawValue}/${region.rawValue}/play"

    fun modeOf(entry: NavBackStackEntry): ChallengeGameMode? =
        entry.arguments?.getString(MODE)?.let(ChallengeGameMode::fromRawValue)

    fun regionOf(entry: NavBackStackEntry): ChallengeRegion? =
        entry.arguments?.getString(REGION)?.let(ChallengeRegion::fromRawValue)
}

/**
 * The tab [route] belongs to: its own destination, or the one whose route it
 * extends — `challenges/nameFlag` is still the Challenges tab.
 */
fun VoyageDestination.Companion.owning(route: String): VoyageDestination? =
    VoyageDestination.entries.firstOrNull { route == it.route || route.startsWith("${it.route}/") }
