package com.anmol.voyage

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anmol.voyage.navigation.ChallengeRoutes
import com.anmol.voyage.navigation.VoyageDestination
import com.anmol.voyage.navigation.owning
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.achievements.AchievementsScreen
import com.anmol.voyage.ui.challenges.ChallengeGameScreen
import com.anmol.voyage.ui.challenges.ChallengeSession
import com.anmol.voyage.ui.challenges.ChallengesScreen
import com.anmol.voyage.ui.challenges.RegionSelectScreen
import com.anmol.voyage.ui.home.HomeScreen
import com.anmol.voyage.ui.screens.PlaceholderScreen
import com.anmol.voyage.ui.settings.SettingsScreen

/**
 * App shell: a Material 3 [NavigationBar] over a [NavHost], one entry per
 * top-level destination. Later phases replace the remaining placeholder bodies
 * with real screens.
 *
 * [VoyageState] is owned by the activity and passed in from there — it decides
 * the theme, which wraps this shell — and handed to every tab, so they share one
 * source of truth, the role `GlobeState` plays on iOS.
 *
 * Home is the one destination the [NavHost] does not draw. It is composed here,
 * outside the host and *under* it, and hidden rather than removed when another
 * tab is chosen — because its globe owns a Filament engine and a `TextureView`
 * surface, and a detached view loses the surface however carefully the engine
 * is kept (the Android plan's 7.11). Its entry stays in the graph, empty, so the
 * back stack and the bar's selection still work exactly as they read here.
 *
 * Under the host, not over it, and that order is load-bearing. A hidden Home is
 * still a full-screen `AndroidView`, and Compose stops hit-testing siblings at
 * the first one it hits whether or not that one handles the event — so an
 * invisible globe on top would quietly eat every tap the tab below it should
 * have received. Beneath, it only ever sees touches that tab did not want, and
 * while hidden it has no handlers to answer them with.
 */
@Composable
fun VoyageApp(state: VoyageState, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: VoyageDestination.start.route
    val currentTab = VoyageDestination.owning(currentRoute) ?: VoyageDestination.start
    val onHome = currentRoute == VoyageDestination.Home.route

    // A challenge being played takes over the whole screen, as iOS's
    // `fullScreenCover` does: no bottom bar, and Home's globe — the one engine
    // there is — shows the game's world under the game's controls.
    val session = backStackEntry
        ?.takeIf { it.destination.route == ChallengeRoutes.PLAY }
        ?.let { challengeSession(it, state) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (session == null) NavigationBar {
                // Spelled out rather than left to the defaults, which colour the
                // selected label with the scheme's `secondary` — Voyage's success
                // green, not a chrome accent.
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VoyageDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        modifier = Modifier.testTag(destinationTag(destination)),
                        colors = itemColors,
                        selected = currentTab == destination,
                        onClick = {
                            if (currentTab == destination) {
                                // Re-selecting a tab returns it to its root, as a
                                // tab bar does on iOS.
                                navController.popBackStack(destination.route, inclusive = false)
                            } else {
                                navController.navigate(destination.route) {
                                    // Tab switching keeps a single-entry back stack:
                                    // back from any tab returns to Home, then exits.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Home runs up under the status bar, as iOS's globe and map ignore the
            // safe area: the sky (or the map) fills the screen's top edge, and
            // Home keeps only its own controls clear of the bar. Every other tab
            // stays inside the scaffold's padding.
            // A game is full-screen and keeps its own controls clear of the bars.
            val layoutDirection = LocalLayoutDirection.current
            HomeScreen(
                state = state,
                visible = onHome || session != null,
                challengeScene = session?.scene,
                modifier = if (session != null) {
                    Modifier
                } else {
                    Modifier.padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = innerPadding.calculateBottomPadding(),
                    )
                },
            )

            NavHost(
                navController = navController,
                modifier = if (session != null) Modifier else Modifier.padding(innerPadding),
                startDestination = VoyageDestination.start.route,
                // No fade between tabs, as on iOS, where a `TabView` switches
                // instantly. It is also what the layering needs: Home is hidden
                // the moment another tab is chosen, and a screen fading in over
                // the gap it leaves would show the bare scaffold for the length
                // of the animation.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                VoyageDestination.entries.forEach { destination ->
                    composable(destination.route) {
                        val subtitleRes = destination.subtitleRes
                        when {
                            // Drawn under this host instead; see the doc above.
                            // Empty, and empty of pointer handlers, so a tap
                            // reaches the globe behind it.
                            destination == VoyageDestination.Home -> Unit
                            destination == VoyageDestination.Achievements ->
                                AchievementsScreen(state = state)
                            destination == VoyageDestination.Settings -> SettingsScreen(state = state)
                            destination == VoyageDestination.Challenges -> ChallengesScreen(
                                state = state,
                                onSelectMode = { navController.navigate(ChallengeRoutes.regionSelect(it)) },
                            )
                            // Destinations a later phase still owns.
                            subtitleRes != null -> PlaceholderScreen(
                                title = stringResource(destination.titleRes),
                                subtitle = stringResource(subtitleRes),
                                icon = destination.icon,
                            )
                        }
                    }
                }
                composable(ChallengeRoutes.REGION_SELECT) { entry ->
                    val mode = ChallengeRoutes.modeOf(entry) ?: return@composable
                    RegionSelectScreen(
                        mode = mode,
                        state = state,
                        onPlay = { region -> navController.navigate(ChallengeRoutes.play(mode, region)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                // Transparent but for its controls, like Home's own entry: the
                // globe the game is played on is drawn beneath this host.
                composable(ChallengeRoutes.PLAY) { entry ->
                    val playing = challengeSession(entry, state) ?: return@composable
                    ChallengeGameScreen(session = playing, onExit = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * The game being played on [entry], created on first use and kept for as long
 * as the entry is on the back stack — across a rotation too. The app shell and
 * the game's own screen both ask for it, and get the same one.
 */
@Composable
private fun challengeSession(entry: NavBackStackEntry, state: VoyageState): ChallengeSession? {
    val mode = ChallengeRoutes.modeOf(entry) ?: return null
    val region = ChallengeRoutes.regionOf(entry) ?: return null
    return viewModel(
        viewModelStoreOwner = entry,
        factory = ChallengeSession.factory(mode, region, state),
    )
}

/**
 * Test tag for [destination]'s bottom-bar item. Labels are not enough: a tab's
 * own screen can show the same word its bar item does.
 */
internal fun destinationTag(destination: VoyageDestination) = "nav:${destination.route}"
