package com.anmol.voyage

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anmol.voyage.navigation.ChallengeRoutes
import com.anmol.voyage.navigation.VoyageDestination
import com.anmol.voyage.navigation.challengesToRegionsEnter
import com.anmol.voyage.navigation.challengesToRegionsExit
import com.anmol.voyage.navigation.owning
import com.anmol.voyage.navigation.regionsToChallengesEnter
import com.anmol.voyage.navigation.regionsToChallengesExit
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.achievements.AchievementUnlockHaptics
import com.anmol.voyage.ui.achievements.AchievementsScreen
import com.anmol.voyage.ui.challenges.ChallengeGameScreen
import com.anmol.voyage.ui.challenges.ChallengeSession
import com.anmol.voyage.ui.challenges.ChallengesScreen
import com.anmol.voyage.ui.challenges.RegionSelectScreen
import com.anmol.voyage.ui.home.HomeScreen
import com.anmol.voyage.ui.screens.PlaceholderScreen
import com.anmol.voyage.ui.settings.SettingsScreen

/**
 * App shell: a Material 3 [NavigationBar] — or, in a wide window, a
 * [NavigationRail] — beside a [NavHost], one entry per top-level destination. Later phases replace the remaining placeholder bodies
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

    // A challenge being played takes over the whole screen, as iOS's
    // `fullScreenCover` does: no navigation, and Home's globe — the one engine
    // there is — shows the game's world under the game's controls.
    val session = backStackEntry
        ?.takeIf { it.destination.route == ChallengeRoutes.PLAY }
        ?.let { challengeSession(it, state) }
    val showsNavigation = session == null

    AchievementUnlockHaptics(state)

    val onSelectTab: (VoyageDestination) -> Unit = { destination ->
        if (currentTab == destination) {
            // Re-selecting a tab returns it to its root, as a tab bar does on iOS.
            navController.popBackStack(destination.route, inclusive = false)
        } else {
            navController.navigate(destination.route) {
                // Tab switching keeps a single-entry back stack: back from any
                // tab returns to Home, then exits.
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Material's navigation for the window: a bottom bar on a phone held
    // upright, a rail down the side from the medium width class up — a tablet,
    // an unfolded foldable, a phone on its side — so the globe and the map keep
    // the window's full height.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useRail = maxWidth >= RAIL_MIN_WIDTH
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showsNavigation && !useRail) VoyageNavigationBar(currentTab, onSelectTab)
            },
        ) { scaffoldPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (showsNavigation && useRail) VoyageNavigationRail(currentTab, onSelectTab)
                VoyageContent(
                    state = state,
                    navController = navController,
                    onHome = currentRoute == VoyageDestination.Home.route,
                    session = session,
                    // The rail pads itself clear of the start edge's insets; the
                    // content beside it must not do so a second time.
                    innerPadding = if (showsNavigation && useRail) {
                        scaffoldPadding.withoutStart()
                    } else {
                        scaffoldPadding
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Everything beside or above the navigation: Home, and over it the [NavHost]
 * with every other screen.
 *
 * @param innerPadding the scaffold's padding, less whatever the navigation has
 *   already kept clear of.
 */
@Composable
private fun VoyageContent(
    state: VoyageState,
    navController: NavHostController,
    onHome: Boolean,
    session: ChallengeSession?,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier) {
        // Home runs up under the status bar, as iOS's globe and map ignore the
        // safe area: the sky (or the map) fills the screen's top edge, and Home
        // keeps only its own controls clear of the bar. Every other tab stays
        // inside the scaffold's padding. A game is full-screen and keeps its own
        // controls clear of the bars.
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
            // instantly. It is also what the layering needs: Home is hidden the
            // moment another tab is chosen, and a screen fading in over the gap
            // it leaves would show the bare scaffold for the length of the
            // animation. The one push within a tab animates — see below.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            VoyageDestination.entries.forEach { destination ->
                val isChallenges = destination == VoyageDestination.Challenges
                composable(
                    route = destination.route,
                    exitTransition = { if (isChallenges) challengesToRegionsExit() else ExitTransition.None },
                    popEnterTransition = { if (isChallenges) regionsToChallengesEnter() else EnterTransition.None },
                ) {
                    val subtitleRes = destination.subtitleRes
                    when {
                        // Drawn under this host instead; see the doc above.
                        // Empty, and empty of pointer handlers, so a tap reaches
                        // the globe behind it.
                        destination == VoyageDestination.Home -> Unit
                        destination == VoyageDestination.Achievements -> AchievementsScreen(state = state)
                        destination == VoyageDestination.Settings -> SettingsScreen(state = state)
                        isChallenges -> ChallengesScreen(
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
            composable(
                route = ChallengeRoutes.REGION_SELECT,
                enterTransition = { challengesToRegionsEnter() },
                popExitTransition = { regionsToChallengesExit() },
            ) { entry ->
                val mode = ChallengeRoutes.modeOf(entry) ?: return@composable
                RegionSelectScreen(
                    mode = mode,
                    state = state,
                    onPlay = { region -> navController.navigate(ChallengeRoutes.play(mode, region)) },
                    onBack = { navController.popBackStack() },
                )
            }
            // Transparent but for its controls, like Home's own entry: the globe
            // the game is played on is drawn beneath this host.
            composable(ChallengeRoutes.PLAY) { entry ->
                val playing = challengeSession(entry, state) ?: return@composable
                ChallengeGameScreen(session = playing, onExit = { navController.popBackStack() })
            }
        }
    }
}

/** These padding values with nothing on the start edge. */
@Composable
private fun PaddingValues.withoutStart(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}

/**
 * The phone's navigation: a Material 3 [NavigationBar] along the bottom.
 *
 * Its colors are spelled out rather than left to the defaults, which colour the
 * selected label with the scheme's `secondary` — Voyage's success green, not a
 * chrome accent. The rail below shares them.
 */
@Composable
private fun VoyageNavigationBar(currentTab: VoyageDestination, onSelect: (VoyageDestination) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = scheme.onSecondaryContainer,
        selectedTextColor = scheme.onSurface,
        indicatorColor = scheme.secondaryContainer,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationBar {
        VoyageDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentTab == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { NavigationLabel(destination) },
                colors = colors,
                modifier = Modifier.testTag(destinationTag(destination)),
            )
        }
    }
}

/** The wide window's navigation: the same destinations, down a [NavigationRail]. */
@Composable
private fun VoyageNavigationRail(currentTab: VoyageDestination, onSelect: (VoyageDestination) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = NavigationRailItemDefaults.colors(
        selectedIconColor = scheme.onSecondaryContainer,
        selectedTextColor = scheme.onSurface,
        indicatorColor = scheme.secondaryContainer,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationRail {
        // Centered in the rail's height, as Material places a rail's items when
        // it has no header.
        Spacer(Modifier.weight(1f))
        VoyageDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = currentTab == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { NavigationLabel(destination) },
                colors = colors,
                modifier = Modifier.testTag(destinationTag(destination)),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * A destination's label, one line at any font scale.
 *
 * Five items share a phone's width, so at 200% "Challenges" had nowhere to go
 * but a break mid-word. The label grows with the user's font scale up to
 * [MAX_LABEL_FONT_SCALE] and stops, as the labels in Google's own apps do; the
 * icon above it and TalkBack still say which tab it is.
 */
@Composable
private fun NavigationLabel(destination: VoyageDestination) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE)),
    ) {
        Text(
            text = stringResource(destination.labelRes),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_LABEL_FONT_SCALE = 1.2f

/** Material's medium window width class, where a rail replaces the bottom bar. */
private val RAIL_MIN_WIDTH = 600.dp

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
 * Test tag for [destination]'s bottom-bar or rail item. Labels are not enough: a tab's
 * own screen can show the same word its bar item does.
 */
internal fun destinationTag(destination: VoyageDestination) = "nav:${destination.route}"
